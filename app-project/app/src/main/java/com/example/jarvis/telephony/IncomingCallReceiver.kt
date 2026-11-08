package com.example.jarvis.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.jarvis.overlay.JarvisOverlayService

/**
 * Listens for incoming phone calls and activates Jarvis's holographic call HUD
 * allowing hands-free voice answering, declining, or silencing.
 */
class IncomingCallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "IncomingCallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        Log.d(TAG, "Phone state changed: $state")

        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
            val telephony = TelephonyController(context)
            val match = if (incomingNumber.isNotBlank()) telephony.lookupBestContact(incomingNumber) else null
            val displayName = match?.displayName ?: if (incomingNumber.isNotBlank()) incomingNumber else "Incoming Call"

            Log.i(TAG, "Incoming call ringing from: $displayName ($incomingNumber)")
            JarvisOverlayService.notifyIncomingCall(context, displayName, incomingNumber)
        }
    }
}
