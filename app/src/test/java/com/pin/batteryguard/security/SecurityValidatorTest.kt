package com.pin.batteryguard.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityValidatorTest {

    @Test
    fun test_xiaomi_freeze_packages_pass_regex_and_whitelist() {
        val expectedPackages = listOf(
            "com.milink.service",
            "com.milink.crossdeviceservice",
            "com.xiaomi.cameratools",
            "com.xiaomi.vtcamera",
            "com.miui.analytics",
            "com.miui.daemon",
            "com.miui.systemAdSolution",
            "com.miui.bugreport",
            "com.miui.hybrid",
            "com.xiaomi.security.onetrack",
            "com.miui.yellowpage"
        )

        for (pkg in expectedPackages) {
            assertTrue("Package $pkg must be a valid package name", SecurityValidator.isValidPackageName(pkg))
            assertTrue("Package $pkg must be in Xiaomi freeze whitelist", SecurityValidator.isSafeXiaomiFreezePackage(pkg))
            assertTrue("Package $pkg must be safe to freeze", SecurityValidator.isSafeToFreeze(pkg))
            assertFalse("Package $pkg must not be critical system app", SecurityValidator.isCriticalSystemApp(pkg))
            assertEquals(pkg, SecurityValidator.sanitizeXiaomiFreezePackageOrThrow(pkg))
        }
    }

    @Test
    fun test_critical_system_packages_rejected_from_freeze() {
        val criticalPackages = listOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.google.android.gms",
            "com.miui.home",
            "com.miui.securitycenter"
        )

        for (pkg in criticalPackages) {
            assertTrue("Package $pkg is critical", SecurityValidator.isCriticalSystemApp(pkg))
            assertFalse("Critical package $pkg must not be safe to freeze", SecurityValidator.isSafeToFreeze(pkg))
            assertFalse("Critical package $pkg must not be in Xiaomi safe freeze list", SecurityValidator.isSafeXiaomiFreezePackage(pkg))
        }
    }

    @Test
    fun test_thermal_override_enforces_failsafe_bounds() {
        // <= 48C is allowed
        assertTrue(SecurityValidator.isThermalOverrideAllowed(25.0f))
        assertTrue(SecurityValidator.isThermalOverrideAllowed(38.0f))
        assertTrue(SecurityValidator.isThermalOverrideAllowed(42.0f))
        assertTrue(SecurityValidator.isThermalOverrideAllowed(47.9f))
        assertTrue(SecurityValidator.isThermalOverrideAllowed(48.0f))

        // > 48C MUST be disallowed
        assertFalse(SecurityValidator.isThermalOverrideAllowed(48.1f))
        assertFalse(SecurityValidator.isThermalOverrideAllowed(50.0f))
        assertFalse(SecurityValidator.isThermalOverrideAllowed(60.0f))

        // Target thermal limit clamping
        assertEquals(48.0f, SecurityValidator.enforceSafeThermalLimit(55.0f), 0.001f)
        assertEquals(30.0f, SecurityValidator.enforceSafeThermalLimit(20.0f), 0.001f)
        assertEquals(45.0f, SecurityValidator.enforceSafeThermalLimit(45.0f), 0.001f)

        // Status code bounds (AOSP ThermalStatus 0..6)
        assertTrue(SecurityValidator.isValidThermalStatus(0))
        assertTrue(SecurityValidator.isValidThermalStatus(1))
        assertTrue(SecurityValidator.isValidThermalStatus(6))
        assertFalse(SecurityValidator.isValidThermalStatus(-1))
        assertFalse(SecurityValidator.isValidThermalStatus(7))
    }

    @Test
    fun test_shell_command_array_injection_vectors_blocked() {
        val dangerousTokens = listOf(
            "com.miui.analytics; rm -rf /",
            "test | cat /etc/passwd",
            "test & reboot",
            "`id`",
            "$(whoami)",
            "com.pkg\nreboot",
            "com.pkg\rreboot",
            "com.pkg>output.txt",
            "com.pkg<input.txt",
            "\"escaped\"",
            "'single_quoted'",
            "backslash\\injection"
        )

        for (token in dangerousTokens) {
            assertFalse("Token '$token' must be identified as unsafe", SecurityValidator.isSafeShellArgument(token))
            assertFalse(
                "Command array containing '$token' must be identified as unsafe",
                SecurityValidator.isSafeCommandArray(arrayOf("am", "force-stop", token))
            )
        }

        val safeArray = arrayOf("am", "set-standby-bucket", "com.milink.service", "restricted")
        assertTrue(SecurityValidator.isSafeCommandArray(safeArray))
        SecurityValidator.assertSafeCommandArray(safeArray)
    }
}
