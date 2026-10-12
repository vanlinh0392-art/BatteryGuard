package com.pin.batteryguard.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.pin.batteryguard.R
import com.pin.batteryguard.data.settings.SystemSettingsManager

private const val TAG = "ScreenTranslateTile"

/**
 * Quick Settings Tile cho tính năng Dịch màn hình tức thì bằng Google Lens.
 */
class ScreenTranslateTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = "Dịch màn hình"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "Google Lens"
        }
        try {
            tile.icon = Icon.createWithResource(this, R.drawable.ic_translate)
        } catch (e: Exception) {
            Log.w(TAG, "Không thể tải icon ic_translate: ${e.message}")
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Quick Settings Tile Dịch màn hình được nhấn")
        unlockAndRun {
            if (SystemSettingsManager.isMiCTSInstalled(this)) {
                val launched = SystemSettingsManager.launchMiCTS(this)
                if (launched) return@unlockAndRun
            }
            val success = SystemSettingsManager.launchLensTranslateDirect(this)
            if (!success) {
                Log.d(TAG, "launchLensTranslateDirect không khả dụng, chuyển sang launchScreenSearchTest")
                SystemSettingsManager.launchScreenSearchTest(this)
            }
        }
    }
}
