package com.example.jarvis.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.jarvis.MainActivity

/**
 * Quick Settings pull-down tile for instant Jarvis activation.
 * Tapping the tile collapses the notification shade and launches Jarvis
 * in immediate listening mode.
 */
class JarvisTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = "Blue"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VOICE_COMMAND
            putExtra("EXTRA_AUTO_START_SPEECH", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
