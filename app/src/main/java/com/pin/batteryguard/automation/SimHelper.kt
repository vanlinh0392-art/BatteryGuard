package com.pin.batteryguard.automation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.pin.batteryguard.ui.screen.automation.SimInfoItem
import com.pin.batteryguard.ui.screen.automation.TargetSimSelection

object SimHelper {

    /**
     * Lấy danh sách các SIM đang hoạt động trên thiết bị
     */
    fun getDetectedSims(context: Context): List<SimInfoItem> {
        val result = mutableListOf<SimInfoItem>()

        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
                val activeList: List<SubscriptionInfo>? = subscriptionManager?.activeSubscriptionInfoList

                if (!activeList.isNullOrEmpty()) {
                    for (info in activeList) {
                        val carrier = info.carrierName?.toString() ?: ""
                        val name = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}"
                        result.add(
                            SimInfoItem(
                                slotIndex = info.simSlotIndex,
                                subId = info.subscriptionId,
                                displayName = name,
                                carrierName = carrier
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Nếu không lấy được qua quyền Android thông thường (hoặc chưa cấp), trả về 2 Slot SIM mặc định để user vẫn có thể chọn
        if (result.isEmpty()) {
            result.add(SimInfoItem(slotIndex = 0, subId = -1, displayName = "SIM 1", carrierName = "Khe 1"))
            result.add(SimInfoItem(slotIndex = 1, subId = -1, displayName = "SIM 2", carrierName = "Khe 2"))
        }

        return result
    }

    /**
     * Tìm SubscriptionId cho SIM được chọn
     */
    fun getTargetSubId(context: Context, targetSim: TargetSimSelection): Int? {
        if (targetSim == TargetSimSelection.AUTO) {
            val defaultSubId = SubscriptionManager.getDefaultDataSubscriptionId()
            return if (SubscriptionManager.isValidSubscriptionId(defaultSubId)) defaultSubId else null
        }

        val targetSlot = when (targetSim) {
            TargetSimSelection.SIM_1 -> 0
            TargetSimSelection.SIM_2 -> 1
            else -> return null
        }

        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
                val activeList = subscriptionManager?.activeSubscriptionInfoList
                val matched = activeList?.firstOrNull { it.simSlotIndex == targetSlot }
                if (matched != null && SubscriptionManager.isValidSubscriptionId(matched.subscriptionId)) {
                    return matched.subscriptionId
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * Tạo danh sách shell commands để bật/tắt dữ liệu di động chính xác theo SIM đã chọn
     */
    fun buildToggleDataCommands(enable: Boolean, targetSim: TargetSimSelection, context: Context): List<String> {
        val action = if (enable) "enable" else "disable"
        val commands = mutableListOf<String>()

        val subId = getTargetSubId(context, targetSim)

        // 1. Nếu có subId cụ thể cho SIM được chọn (SIM 1, SIM 2 hoặc AUTO), thử lệnh per-subId
        if (subId != null && subId > 0) {
            commands.add("cmd phone data $action $subId")
        }

        // 2. Lệnh tiêu chuẩn AOSP tác động lên SIM dữ liệu mặc định (hoạt động 100% trên toàn bộ các dòng máy)
        commands.add("svc data $action")
        commands.add("cmd phone data $action")

        return commands
    }
}
