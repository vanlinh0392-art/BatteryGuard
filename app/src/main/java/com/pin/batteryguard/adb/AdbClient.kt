package com.pin.batteryguard.adb

import android.content.Context
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AdbClient"

@Singleton
class AdbClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val A_SYNC = 0x434e5953
        const val A_CNXN = 0x4e584e43
        const val A_AUTH = 0x48545541
        const val A_OPEN = 0x4e45504f
        const val A_OKAY = 0x59414b4f
        const val A_CLSE = 0x45534c43
        const val A_WRTE = 0x45545257

        const val A_VERSION = 0x01000000
        const val MAX_PAYLOAD = 4096

        const val ADB_AUTH_TOKEN = 1
        const val ADB_AUTH_SIGNATURE = 2
        const val ADB_AUTH_RSAPUBLICKEY = 3

        const val KEY_LENGTH_BITS = 2048
        const val KEY_LENGTH_BYTES = KEY_LENGTH_BITS / 8
        const val KEY_LENGTH_WORDS = KEY_LENGTH_BYTES / 4

        /**
         * Padding PKCS#1 v1.5 với ASN.1 header của SHA-1 theo chuẩn ADB protocol (AOSP mincrypt).
         * 236 bytes padding + 20 bytes SHA-1 token = 256 bytes (2048-bit RSA block).
         */
        val SIGNATURE_PADDING: ByteArray by lazy {
            val padding = ByteArray(236)
            padding[0] = 0x00
            padding[1] = 0x01
            for (i in 2..219) {
                padding[i] = 0xff.toByte()
            }
            padding[220] = 0x00
            // ASN.1 DigestInfo prefix cho SHA-1 (15 bytes)
            val asn1 = byteArrayOf(
                0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
                0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
            )
            System.arraycopy(asn1, 0, padding, 221, asn1.size)
            padding
        }
    }

    private var cachedKeyPair: KeyPair? = null
    @Volatile private var lastAuthDeclinedOrTimeoutTime: Long = 0L

    fun isAuthDeclinedRecently(cooldownMs: Long = 10 * 60 * 1000L): Boolean {
        return (System.currentTimeMillis() - lastAuthDeclinedOrTimeoutTime) < cooldownMs
    }

    fun resetAuthCooldown() {
        lastAuthDeclinedOrTimeoutTime = 0L
    }

    /**
     * Thực thi lệnh shell qua ADB socket localhost.
     * @param command Lệnh cần chạy
     * @param port Cổng ADB TCP (mặc định 5555)
     * @param timeoutMs Timeout kết nối và đọc socket (mặc định 8000ms)
     * @return Pair(success: Boolean, output: String)
     */
    suspend fun executeCommand(
        command: String,
        port: Int = 5555,
        timeoutMs: Int = 8000
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val keyPair = getOrCreateKeyPair()
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress("127.0.0.1", port), 3000)

            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 1. Gửi CNXN
            val banner = "host::BatteryGuard\u0000".toByteArray(Charsets.UTF_8)
            AdbMessage(A_CNXN, A_VERSION, MAX_PAYLOAD, banner).write(output)

            var msg = AdbMessage.read(input)

            // 2. Xử lý AUTH nếu server yêu cầu
            if (msg.command == A_AUTH && msg.arg0 == ADB_AUTH_TOKEN) {
                val token = msg.data
                val signature = signToken(keyPair.private, token)
                AdbMessage(A_AUTH, ADB_AUTH_SIGNATURE, 0, signature).write(output)

                msg = AdbMessage.read(input)

                // Nếu thiết bị chưa trust public key này, gửi RSAPUBLICKEY để hiển thị prompt cho user
                if (msg.command == A_AUTH && msg.arg0 == ADB_AUTH_TOKEN) {
                    Log.i(TAG, "Thiết bị chưa ghi nhớ key, yêu cầu xác thực RSA. Đang hiển thị hộp thoại cấp quyền...")
                    val pubKeyData = formatAdbPublicKey(keyPair.public as RSAPublicKey)
                    // Tăng timeout lên 30 giây để người dùng kịp đọc và bấm 'Cho phép' trên màn hình
                    socket.soTimeout = 30000
                    AdbMessage(A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, pubKeyData).write(output)
                    try {
                        msg = AdbMessage.read(input)
                    } catch (e: Exception) {
                        Log.w(TAG, "Người dùng không xác nhận hộp thoại ADB kịp thời hoặc đã hủy: ${e.message}")
                        lastAuthDeclinedOrTimeoutTime = System.currentTimeMillis()
                        return@withContext Pair(false, "ADB authorization timeout or cancelled")
                    } finally {
                        socket.soTimeout = timeoutMs
                    }

                    if (msg.command != A_CNXN) {
                        lastAuthDeclinedOrTimeoutTime = System.currentTimeMillis()
                    }
                }
            }

            if (msg.command != A_CNXN) {
                Log.w(TAG, "ADB handshake failed: unexpected response command 0x${Integer.toHexString(msg.command)}")
                return@withContext Pair(false, "ADB handshake failed (0x${Integer.toHexString(msg.command)})")
            }

            // Kết nối và xác thực thành công -> xóa cờ cooldown
            lastAuthDeclinedOrTimeoutTime = 0L

            // 3. Mở shell stream
            val localId = 1
            val shellCommand = "shell:$command\u0000".toByteArray(Charsets.UTF_8)
            AdbMessage(A_OPEN, localId, 0, shellCommand).write(output)

            msg = AdbMessage.read(input)
            if (msg.command != A_OKAY) {
                Log.w(TAG, "Failed to open shell stream: 0x${Integer.toHexString(msg.command)}")
                return@withContext Pair(false, "Failed to open shell stream")
            }
            val remoteId = msg.arg0

            // 4. Đọc output của shell
            val outputStream = ByteArrayOutputStream()
            while (true) {
                try {
                    msg = AdbMessage.read(input)
                } catch (e: EOFException) {
                    break
                }
                if (msg.command == A_WRTE) {
                    outputStream.write(msg.data)
                    // Gửi OKAY xác nhận đã nhận WRTE
                    AdbMessage(A_OKAY, localId, remoteId).write(output)
                } else if (msg.command == A_CLSE) {
                    AdbMessage(A_CLSE, localId, remoteId).write(output)
                    break
                }
            }

            val resultOutput = outputStream.toString("UTF-8")
            Log.d(TAG, "Command '$command' executed successfully via port $port. Output: $resultOutput")
            Pair(true, resultOutput)
        } catch (e: Exception) {
            Log.w(TAG, "ADB command execution failed on port $port: ${e.message}")
            Pair(false, e.message ?: "Connection error")
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Ký token nhận từ adbd bằng RSA private key theo chuẩn AOSP mincrypt.
     * Sử dụng RSA/ECB/NoPadding với mảng SIGNATURE_PADDING chuẩn để adbd xác minh thành công.
     */
    private fun signToken(privateKey: PrivateKey, token: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        cipher.update(SIGNATURE_PADDING)
        return cipher.doFinal(token)
    }

    /**
     * Mã hóa RSA Public Key theo định dạng `struct RSAPublicKey` (mincrypt) của Android adbd.
     * Dựa trên chuẩn RSA_to_RSAPublicKey trong AOSP / AdbLib của Cameron Gutman.
     */
    private fun formatAdbPublicKey(publicKey: RSAPublicKey): ByteArray {
        val r32 = BigInteger.ZERO.setBit(32)
        var n = publicKey.modulus
        val r = BigInteger.ZERO.setBit(KEY_LENGTH_WORDS * 32)
        var rr = r.modPow(BigInteger.valueOf(2), n)
        val rem = n.remainder(r32)
        val n0inv = rem.modInverse(r32)

        val myN = IntArray(KEY_LENGTH_WORDS)
        val myRr = IntArray(KEY_LENGTH_WORDS)
        for (i in 0 until KEY_LENGTH_WORDS) {
            val resRr = rr.divideAndRemainder(r32)
            rr = resRr[0]
            myRr[i] = resRr[1].toInt()

            val resN = n.divideAndRemainder(r32)
            n = resN[0]
            myN[i] = resN[1].toInt()
        }

        val bbuf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        bbuf.putInt(KEY_LENGTH_WORDS)
        bbuf.putInt(n0inv.negate().toInt())
        for (i in myN) bbuf.putInt(i)
        for (i in myRr) bbuf.putInt(i)
        bbuf.putInt(publicKey.publicExponent.toInt())

        val base64Key = Base64.encodeToString(bbuf.array(), Base64.NO_WRAP)
        val keyString = "$base64Key BatteryGuard\u0000"
        return keyString.toByteArray(Charsets.UTF_8)
    }

    @Synchronized
    private fun getOrCreateKeyPair(): KeyPair {
        cachedKeyPair?.let { return it }

        val keysDir = File(context.filesDir, "adb_keys").apply { mkdirs() }
        val privFile = File(keysDir, "adb_key")
        val pubFile = File(keysDir, "adb_key.pub")

        if (privFile.exists() && pubFile.exists()) {
            try {
                val keyFactory = KeyFactory.getInstance("RSA")
                val privBytes = privFile.readBytes()
                val pubBytes = pubFile.readBytes()
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
                val kp = KeyPair(publicKey, privateKey)
                cachedKeyPair = kp
                return kp
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load saved ADB keys, generating new pair: ${e.message}")
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(KEY_LENGTH_BITS)
        val newKp = kpg.generateKeyPair()

        try {
            privFile.writeBytes(newKp.private.encoded)
            pubFile.writeBytes(newKp.public.encoded)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist ADB keys: ${e.message}")
        }

        cachedKeyPair = newKp
        return newKp
    }
}

/**
 * Cấu trúc thông điệp ADB packet 24-byte header + data payload.
 */
data class AdbMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val dataLength: Int,
    val dataCrc32: Int,
    val magic: Int,
    val data: ByteArray = ByteArray(0)
) {
    companion object {
        fun read(input: InputStream): AdbMessage {
            val header = ByteArray(24)
            var read = 0
            while (read < 24) {
                val count = input.read(header, read, 24 - read)
                if (count == -1) throw EOFException("Socket closed while reading header")
                read += count
            }

            val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val command = bb.getInt()
            val arg0 = bb.getInt()
            val arg1 = bb.getInt()
            val dataLength = bb.getInt()
            val dataCrc32 = bb.getInt()
            val magic = bb.getInt()

            val data = if (dataLength > 0) {
                val dataBuffer = ByteArray(dataLength)
                read = 0
                while (read < dataLength) {
                    val count = input.read(dataBuffer, read, dataLength - read)
                    if (count == -1) throw EOFException("Socket closed while reading data")
                    read += count
                }
                dataBuffer
            } else {
                ByteArray(0)
            }

            return AdbMessage(command, arg0, arg1, dataLength, dataCrc32, magic, data)
        }
    }

    constructor(command: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) : this(
        command = command,
        arg0 = arg0,
        arg1 = arg1,
        dataLength = data.size,
        dataCrc32 = calculateChecksum(data),
        magic = command xor -0x1,
        data = data
    )

    fun write(output: OutputStream) {
        val bb = ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(command)
        bb.putInt(arg0)
        bb.putInt(arg1)
        bb.putInt(dataLength)
        bb.putInt(dataCrc32)
        bb.putInt(magic)
        if (data.isNotEmpty()) {
            bb.put(data)
        }
        output.write(bb.array())
        output.flush()
    }
}

private fun calculateChecksum(data: ByteArray): Int {
    var sum = 0
    for (b in data) {
        sum += (b.toInt() and 0xFF)
    }
    return sum
}
