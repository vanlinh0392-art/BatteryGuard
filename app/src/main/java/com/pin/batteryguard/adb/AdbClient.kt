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
import java.io.IOException
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
    }

    private var cachedKeyPair: KeyPair? = null

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
                    val pubKeyData = formatAdbPublicKey(keyPair.public as RSAPublicKey)
                    AdbMessage(A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, pubKeyData).write(output)
                    msg = AdbMessage.read(input)
                }
            }

            if (msg.command != A_CNXN) {
                Log.w(TAG, "ADB handshake failed: unexpected response command 0x${Integer.toHexString(msg.command)}")
                return@withContext Pair(false, "ADB handshake failed (0x${Integer.toHexString(msg.command)})")
            }

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
     * Ký token nhận từ adbd bằng RSA private key (PKCS#1 v1.5 padding).
     */
    private fun signToken(privateKey: PrivateKey, token: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, privateKey)
            cipher.doFinal(token)
        } catch (e: Exception) {
            val sig = java.security.Signature.getInstance("NONEwithRSA")
            sig.initSign(privateKey)
            sig.update(token)
            sig.sign()
        }
    }

    /**
     * Mã hóa RSA Public Key theo định dạng `struct RSAPublicKey` (mincrypt) của Android adbd.
     */
    private fun formatAdbPublicKey(publicKey: RSAPublicKey): ByteArray {
        val n = publicKey.modulus
        val e = publicKey.publicExponent
        val r32 = BigInteger.valueOf(2).pow(32)
        val n0 = n.mod(r32)
        val n0inv = n0.modInverse(r32).negate().mod(r32).toLong()

        val r = BigInteger.valueOf(2).pow(2048)
        val rr = r.multiply(r).mod(n)

        val bb = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(64) // len in 32-bit words (2048 / 32 = 64)
        bb.putInt(n0inv.toInt())

        val nBytes = getUnsignedBytes(n, 256)
        for (i in 0 until 64) {
            val offset = 256 - (i + 1) * 4
            bb.put(nBytes[offset + 3])
            bb.put(nBytes[offset + 2])
            bb.put(nBytes[offset + 1])
            bb.put(nBytes[offset])
        }

        val rrBytes = getUnsignedBytes(rr, 256)
        for (i in 0 until 64) {
            val offset = 256 - (i + 1) * 4
            bb.put(rrBytes[offset + 3])
            bb.put(rrBytes[offset + 2])
            bb.put(rrBytes[offset + 1])
            bb.put(rrBytes[offset])
        }

        bb.putInt(e.toInt())

        val base64Key = Base64.encodeToString(bb.array(), Base64.NO_WRAP)
        val keyString = "$base64Key BatteryGuard\u0000"
        return keyString.toByteArray(Charsets.UTF_8)
    }

    private fun getUnsignedBytes(bi: BigInteger, length: Int): ByteArray {
        val raw = bi.toByteArray()
        val result = ByteArray(length)
        if (raw.size >= length) {
            System.arraycopy(raw, raw.size - length, result, 0, length)
        } else {
            System.arraycopy(raw, 0, result, length - raw.size, raw.size)
        }
        return result
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
        kpg.initialize(2048)
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
    val data: ByteArray = ByteArray(0)
) {
    fun write(output: OutputStream) {
        val buf = ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(command)
        buf.putInt(arg0)
        buf.putInt(arg1)
        buf.putInt(data.size)
        buf.putInt(checksum(data))
        buf.putInt(command xor -0x1)
        if (data.isNotEmpty()) {
            buf.put(data)
        }
        output.write(buf.array())
        output.flush()
    }

    companion object {
        fun checksum(data: ByteArray): Int {
            var sum = 0
            for (b in data) {
                sum += (b.toInt() and 0xFF)
            }
            return sum
        }

        fun read(input: InputStream): AdbMessage {
            val header = ByteArray(24)
            var read = 0
            while (read < 24) {
                val r = input.read(header, read, 24 - read)
                if (r < 0) throw EOFException("ADB connection closed while reading header")
                read += r
            }
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val command = buf.int
            val arg0 = buf.int
            val arg1 = buf.int
            val dataLen = buf.int
            val dataChecksum = buf.int
            val magic = buf.int

            if (command != (magic xor -0x1)) {
                throw IOException("Corrupted ADB header: command=0x${Integer.toHexString(command)}, magic=0x${Integer.toHexString(magic)}")
            }

            if (dataLen < 0 || dataLen > 65536) {
                throw IOException("Invalid or excessive ADB data payload length: $dataLen")
            }

            val data = ByteArray(dataLen)
            var dataRead = 0
            while (dataRead < dataLen) {
                val r = input.read(data, dataRead, dataLen - dataRead)
                if (r < 0) throw EOFException("ADB connection closed while reading data payload")
                dataRead += r
            }
            return AdbMessage(command, arg0, arg1, data)
        }
    }
}
