package com.touchdetector

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var accessibilityStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var adbStatus: TextView
    private lateinit var deviceIdStatus: TextView
    private lateinit var virtualDeviceStatus: TextView
    private lateinit var sourceStatus: TextView
    private lateinit var toolTypeStatus: TextView
    private lateinit var obscuredStatus: TextView
    private lateinit var verdictText: TextView
    private lateinit var touchArea: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        overlayStatus = findViewById(R.id.overlayStatus)
        adbStatus = findViewById(R.id.adbStatus)
        deviceIdStatus = findViewById(R.id.deviceIdStatus)
        virtualDeviceStatus = findViewById(R.id.virtualDeviceStatus)
        sourceStatus = findViewById(R.id.sourceStatus)
        toolTypeStatus = findViewById(R.id.toolTypeStatus)
        obscuredStatus = findViewById(R.id.obscuredStatus)
        verdictText = findViewById(R.id.verdictText)
        touchArea = findViewById(R.id.touchArea)

        performEnvironmentChecks()

        touchArea.setOnTouchListener { _, event ->
            analyzeTouchEvent(event)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        performEnvironmentChecks()
    }

    private fun performEnvironmentChecks() {
        checkAccessibilityServices()
        checkOverlayPermissions()
        checkAdbStatus()
    }

    private fun checkAccessibilityServices() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )

        val suspiciousServices = enabledServices.filter { service ->
            val name = service.resolveInfo.serviceInfo.name.lowercase()
            val label = service.resolveInfo.serviceInfo.loadLabel(packageManager).toString().lowercase()
            name.contains("click") || name.contains("tap") || name.contains("auto") ||
            label.contains("click") || label.contains("tap") || label.contains("auto") ||
            name.contains("macro") || label.contains("macro")
        }

        if (suspiciousServices.isEmpty()) {
            val count = enabledServices.size
            accessibilityStatus.text = "Accessibility Services: $count active (none suspicious)"
            accessibilityStatus.setTextColor(getColor(R.color.status_green))
        } else {
            val names = suspiciousServices.joinToString { 
                it.resolveInfo.serviceInfo.loadLabel(packageManager).toString()
            }
            accessibilityStatus.text = "Accessibility Services: SUSPICIOUS - $names"
            accessibilityStatus.setTextColor(getColor(R.color.status_red))
        }
    }

    private fun checkOverlayPermissions() {
        if (Settings.canDrawOverlays(this)) {
            overlayStatus.text = "Overlay Permission: GRANTED (risk indicator)"
            overlayStatus.setTextColor(getColor(R.color.status_yellow))
        } else {
            overlayStatus.text = "Overlay Permission: Not granted"
            overlayStatus.setTextColor(getColor(R.color.status_green))
        }
    }

    private fun checkAdbStatus() {
        val adbEnabled = Settings.Global.getInt(
            contentResolver,
            Settings.Global.ADB_ENABLED,
            0
        ) == 1

        if (adbEnabled) {
            adbStatus.text = "ADB/Developer Options: ENABLED (risk indicator)"
            adbStatus.setTextColor(getColor(R.color.status_yellow))
        } else {
            adbStatus.text = "ADB/Developer Options: Disabled"
            adbStatus.setTextColor(getColor(R.color.status_green))
        }
    }

    private fun analyzeTouchEvent(event: MotionEvent) {
        val deviceId = event.deviceId
        val device = event.device
        val isVirtual = device?.isVirtual ?: false
        val source = event.source
        val toolType = event.getToolType(0)
        val isObscured = event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0

        deviceIdStatus.text = "Device ID: $deviceId"
        deviceIdStatus.setTextColor(getColor(
            if (deviceId == 0 || deviceId == -1) R.color.status_red else R.color.status_green
        ))

        virtualDeviceStatus.text = "Virtual Device: $isVirtual"
        virtualDeviceStatus.setTextColor(getColor(
            if (isVirtual) R.color.status_red else R.color.status_green
        ))

        sourceStatus.text = "Event Source: ${getSourceName(source)}"
        sourceStatus.setTextColor(getColor(
            if (source == InputDevice.SOURCE_MOUSE) R.color.status_yellow else R.color.status_green
        ))

        toolTypeStatus.text = "Tool Type: ${getToolTypeName(toolType)}"
        toolTypeStatus.setTextColor(getColor(
            if (toolType == MotionEvent.TOOL_TYPE_MOUSE) R.color.status_yellow else R.color.status_green
        ))

        obscuredStatus.text = "Window Obscured: $isObscured"
        obscuredStatus.setTextColor(getColor(
            if (isObscured) R.color.status_yellow else R.color.status_green
        ))

        generateVerdict(deviceId, isVirtual, source, toolType, isObscured)
    }

    private fun generateVerdict(
        deviceId: Int,
        isVirtual: Boolean,
        source: Int,
        toolType: Int,
        isObscured: Boolean
    ) {
        val risks = mutableListOf<String>()

        if (deviceId == 0 || deviceId == -1) {
            risks.add("Device ID is 0/-1 (software injection)")
        }
        if (isVirtual) {
            risks.add("Virtual input device detected")
        }
        if (source == InputDevice.SOURCE_MOUSE) {
            risks.add("Mouse source (possible PC tool)")
        }
        if (toolType == MotionEvent.TOOL_TYPE_MOUSE) {
            risks.add("Mouse tool type (possible PC tool)")
        }
        if (isObscured) {
            risks.add("Window obscured (overlay detected)")
        }

        when {
            risks.isEmpty() -> {
                verdictText.text = "LIKELY GENUINE"
                verdictText.setTextColor(getColor(R.color.status_green))
            }
            risks.size == 1 -> {
                verdictText.text = "SUSPICIOUS: ${risks[0]}"
                verdictText.setTextColor(getColor(R.color.status_yellow))
            }
            else -> {
                verdictText.text = "HIGH RISK AUTO-CLICKER:\n${risks.joinToString("\n")}"
                verdictText.setTextColor(getColor(R.color.status_red))
            }
        }
    }

    private fun getSourceName(source: Int): String {
        return when (source) {
            InputDevice.SOURCE_TOUCHSCREEN -> "TOUCHSCREEN"
            InputDevice.SOURCE_MOUSE -> "MOUSE"
            InputDevice.SOURCE_STYLUS -> "STYLUS"
            InputDevice.SOURCE_KEYBOARD -> "KEYBOARD"
            InputDevice.SOURCE_DPAD -> "DPAD"
            InputDevice.SOURCE_JOYSTICK -> "JOYSTICK"
            else -> "UNKNOWN ($source)"
        }
    }

    private fun getToolTypeName(toolType: Int): String {
        return when (toolType) {
            MotionEvent.TOOL_TYPE_FINGER -> "FINGER"
            MotionEvent.TOOL_TYPE_STYLUS -> "STYLUS"
            MotionEvent.TOOL_TYPE_MOUSE -> "MOUSE"
            MotionEvent.TOOL_TYPE_ERASER -> "ERASER"
            MotionEvent.TOOL_TYPE_UNKNOWN -> "UNKNOWN"
            else -> "UNKNOWN ($toolType)"
        }
    }
}
