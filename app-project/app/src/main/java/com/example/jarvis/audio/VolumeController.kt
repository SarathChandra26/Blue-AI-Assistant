package com.example.jarvis.audio

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.jarvis.commands.CommandResult

/**
 * Controls device volume and Do-Not-Disturb modes.
 *
 * Media volume operations use AudioManager and require no special permissions.
 * Ringer operations require the user to grant DND access when DND is active.
 * DND toggle requires ACCESS_NOTIFICATION_POLICY permission.
 */
class VolumeController(private val context: Context) {

    companion object { private const val TAG = "VolumeController" }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun volumeUp(): CommandResult = adjustVolume(AudioManager.ADJUST_RAISE)
    fun volumeDown(): CommandResult = adjustVolume(AudioManager.ADJUST_LOWER)

    fun muteMedia(): CommandResult {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_MUTE,
            0
        )
        return CommandResult.Success("Media muted")
    }

    fun unmuteMedia(): CommandResult {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_UNMUTE,
            0
        )
        return CommandResult.Success("Media unmuted")
    }

    fun silenceRinger(): CommandResult {
        return try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tm?.silenceRinger()
            }
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            CommandResult.Success("Ringer silenced")
        } catch (e: SecurityException) {
            CommandResult.NeedsPermission(
                "android.permission.ACCESS_NOTIFICATION_POLICY",
                "Changing ringer mode requires Do Not Disturb access. " +
                "Please grant it in Settings → Sound → Do Not Disturb → Allow apps."
            )
        } catch (e: Exception) {
            CommandResult.Failure("Could not silence ringer: ${e.localizedMessage}")
        }
    }

    fun setMediaVolume(level: Int): CommandResult {
        if (level !in 0..15) return CommandResult.Failure("Volume level must be between 0 and 15.")
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val scaled = (level / 15f * maxVol).toInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, scaled, AudioManager.FLAG_SHOW_UI)
        return CommandResult.Success("Volume set to $level")
    }

    fun currentMediaVolumePercent(): Int {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max     = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) (current * 100 / max) else 0
    }

    fun enableDnd(): CommandResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
        return if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_NONE)
            CommandResult.Success("Do Not Disturb enabled")
        } else {
            CommandResult.NeedsSetup(
                "Do Not Disturb access is required. " +
                "Go to Settings → Sound → Do Not Disturb → Allow apps, and grant access to Jarvis."
            )
        }
    }

    fun disableDnd(): CommandResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
        return if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
            CommandResult.Success("Do Not Disturb disabled")
        } else {
            CommandResult.NeedsSetup(
                "Do Not Disturb access is required to change the filter. " +
                "Grant it in Settings → Sound → Do Not Disturb."
            )
        }
    }

    private fun adjustVolume(direction: Int): CommandResult {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
        val label = if (direction == AudioManager.ADJUST_RAISE) "increased" else "decreased"
        Log.d(TAG, "Media volume $label")
        return CommandResult.Success("Volume $label")
    }
}
