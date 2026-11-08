package com.example.jarvis.flashlight

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import com.example.jarvis.commands.CommandResult

/**
 * Controls the device flashlight via CameraManager.
 *
 * Maintains internal state because the camera torch callback is asynchronous.
 * Does NOT assume every request succeeds — updates state from the hardware callback.
 *
 * NOTE: If another app is using the camera (e.g. the Camera app), torch access
 * will fail with a CameraAccessException. This is correctly reported as a failure.
 */
class FlashlightController(context: Context) {

    companion object { private const val TAG = "Flashlight" }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /** True only when the hardware has confirmed the torch is on. */
    @Volatile
    private var torchOn = false

    /** null if no flash unit is available. */
    private val flashCameraId: String? by lazy { findFlashCamera() }

    private fun findFlashCamera(): String? {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            val facing   = chars.get(CameraCharacteristics.LENS_FACING)
            if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) return id
        }
        return null
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == flashCameraId) {
                torchOn = enabled
                Log.d(TAG, "Torch hardware state → $enabled")
            }
        }
        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == flashCameraId) {
                torchOn = false
                Log.w(TAG, "Torch unavailable (camera conflict?)")
            }
        }
    }

    init {
        cameraManager.registerTorchCallback(torchCallback, null)
    }

    fun turnOn()     = setTorch(true)
    fun turnOff()    = setTorch(false)
    fun toggle()     = setTorch(!torchOn)
    fun isOn()       = torchOn

    private fun setTorch(enable: Boolean): CommandResult {
        val cameraId = flashCameraId
            ?: return CommandResult.Failure("This device has no rear camera flashlight.")

        return try {
            cameraManager.setTorchMode(cameraId, enable)
            // State is updated by the callback; optimistically report success
            val verb = if (enable) "Flashlight enabled" else "Flashlight disabled"
            Log.i(TAG, verb)
            CommandResult.Success(verb)
        } catch (e: Exception) {
            val msg = when {
                e.message?.contains("permission") == true ->
                    "Camera permission is required to use the flashlight."
                e.message?.contains("in use") == true || e.message?.contains("busy") == true ->
                    "The camera is currently in use by another app. Close it first."
                else -> "Flashlight error: ${e.localizedMessage}"
            }
            Log.e(TAG, "Torch error", e)
            CommandResult.Failure(msg)
        }
    }

    /** Call from Activity/Service onDestroy to clean up. */
    fun release() {
        cameraManager.unregisterTorchCallback(torchCallback)
    }
}
