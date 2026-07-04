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
    private lateinit var accessibilityEventStatus: TextView
    private lateinit var sourceAuthenticityStatus: TextView
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
        accessibilityEventStatus = findViewById(R.id.accessibilityEventStatus)
        sourceAuthenticityStatus = findViewById(R.id.sourceAuthenticityStatus)
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

        val isAccessibilityEvent = event.flags and 0x400 != 0

        val isFromTouchscreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        } else {
            source and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN
        }
        val hasRealDevice = device != null && deviceId != -1 && deviceId != 0
        val sourceAuthentic = isFromTouchscreen && hasRealDevice
        val deviceDescriptor = device?.descriptor ?: "none"

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

        accessibilityEventStatus.text = "Accessibility Gesture (dispatchGesture): $isAccessibilityEvent"
        accessibilityEventStatus.setTextColor(getColor(
            if (isAccessibilityEvent) R.color.status_red else R.color.status_green
        ))

        val sourceDetail = buildString {
            append("Source: ${getSourceName(source)}")
            append(" | Device: ${device?.name ?: "null"}")
            append(" | ID: $deviceId")
            append(" | Descriptor: $deviceDescriptor")
            if (!isFromTouchscreen) append(" | NOT touchscreen source")
            if (!hasRealDevice) append(" | NO real device")
            if (isVirtual) append(" | VIRTUAL device")
        }
        sourceAuthenticityStatus.text = "Source Authenticity: $sourceDetail"
        sourceAuthenticityStatus.setTextColor(getColor(
            if (sourceAuthentic) R.color.status_green else R.color.status_red
        ))

        generateVerdict(deviceId, isVirtual, source, toolType, isObscured, isAccessibilityEvent, sourceAuthentic)
    }

    private fun generateVerdict(
        deviceId: Int,
        isVirtual: Boolean,
        source: Int,
        toolType: Int,
        isObscured: Boolean,
        isAccessibilityEvent: Boolean,
        sourceAuthentic: Boolean
    ) {
        val risks = mutableListOf<String>()

        if (isAccessibilityEvent) {
            risks.add("dispatchGesture() detected (accessibility auto-clicker)")
        }
        if (deviceId == 0 || deviceId == -1) {
            risks.add("Device ID is 0/-1 (software injection)")
        }
        if (isVirtual) {
            risks.add("Virtual input device detected")
        }
        if (!sourceAuthentic) {
            risks.add("Source/device mismatch (synthetic event)")
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
            InputDevice.SOURCE_UNKNOWN -> "UNKNOWN (0)"
            InputDevice.SOURCE_KEYBOARD -> "KEYBOARD"
            InputDevice.SOURCE_DPAD -> "DPAD"
            InputDevice.SOURCE_GAMEPAD -> "GAMEPAD"
            InputDevice.SOURCE_TOUCHSCREEN -> "TOUCHSCREEN"
            InputDevice.SOURCE_MOUSE -> "MOUSE"
            InputDevice.SOURCE_STYLUS -> "STYLUS"
            InputDevice.SOURCE_BLUETOOTH_STYLUS -> "BT_STYLUS"
            InputDevice.SOURCE_TRACKBALL -> "TRACKBALL"
            InputDevice.SOURCE_MOUSE_RELATIVE -> "MOUSE_RELATIVE"
            InputDevice.SOURCE_TOUCHPAD -> "TOUCHPAD"
            InputDevice.SOURCE_TOUCH_NAVIGATION -> "TOUCH_NAV"
            InputDevice.SOURCE_ROTARY_ENCODER -> "ROTARY_ENCODER"
            InputDevice.SOURCE_JOYSTICK -> "JOYSTICK"
            InputDevice.SOURCE_HDMI -> "HDMI"
            InputDevice.SOURCE_SENSOR -> "SENSOR"
            else -> "COMPOSITE/OTHER (0x${Integer.toHexString(source)})"
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
