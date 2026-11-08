package com.example.jarvis.telephony

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.util.Log
import com.example.jarvis.commands.CommandResult
import kotlin.math.max

/**
 * Handles contact lookup, phone calls, speakerphone switching, and WhatsApp calls.
 * Implements multi-tier fuzzy matching so contacts with emojis (e.g. "Teja 🫶🏻") or partial names match smoothly.
 */
class TelephonyController(private val context: Context) {

    companion object {
        private const val TAG = "Telephony"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    data class ContactMatch(
        val displayName: String,
        val number: String,
        val score: Int
    )

    data class WhatsAppMatch(
        val dataId: Long,
        val displayName: String,
        val phoneOrJid: String,
        val score: Int
    )

    /**
     * Initiate a call to a named contact.
     * Supports onSpeaker = true to auto-route audio to speakerphone.
     */
    fun callContact(name: String, onSpeaker: Boolean = false): CommandResult {
        val match = lookupBestContact(name)
            ?: return CommandResult.Failure(
                "I couldn't find a contact matching \"$name\". " +
                "Please verify your contacts or grant Contacts permission."
            )

        return try {
            val uri = Uri.parse("tel:${match.number}")
            val intent = Intent(Intent.ACTION_CALL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            if (onSpeaker) {
                activateSpeakerphone()
            }

            val speakerNote = if (onSpeaker) " on speaker" else ""
            CommandResult.Success("Calling ${match.displayName}$speakerNote")
        } catch (e: SecurityException) {
            dialFallback(match.displayName, match.number)
        } catch (e: Exception) {
            CommandResult.Failure("Could not initiate call: ${e.localizedMessage}")
        }
    }

    /**
     * Initiate a direct WhatsApp VoIP call to a contact.
     */
    fun callWhatsApp(contactName: String): CommandResult {
        val waMatch = lookupWhatsAppContact(contactName)
        if (waMatch != null) {
            return try {
                val uri = Uri.parse("content://com.android.contacts/data/${waMatch.dataId}")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "vnd.android.cursor.item/vnd.com.whatsapp.voip.call")
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                CommandResult.Success("Calling ${waMatch.displayName} on WhatsApp")
            } catch (e: Exception) {
                Log.w(TAG, "Direct WhatsApp VoIP call failed, falling back to chat", e)
                fallbackWhatsAppChat(waMatch.displayName, waMatch.phoneOrJid)
            }
        }

        // Fallback: search regular contacts for phone number and open WhatsApp chat
        val regMatch = lookupBestContact(contactName)
        if (regMatch != null) {
            val cleanNumber = regMatch.number.replace(Regex("[^0-9]"), "")
            return fallbackWhatsAppChat(regMatch.displayName, cleanNumber)
        }

        return CommandResult.Failure("Could not find WhatsApp contact for \"$contactName\".")
    }

    private fun fallbackWhatsAppChat(displayName: String, numberOrJid: String): CommandResult {
        return try {
            val clean = numberOrJid.replace("@s.whatsapp.net", "").replace(Regex("[^0-9]"), "")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$clean")).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult.Success("Opening WhatsApp chat with $displayName")
        } catch (e: Exception) {
            CommandResult.Failure("WhatsApp not installed or could not open chat.")
        }
    }

    /**
     * Silences the ringer for an incoming call.
     */
    fun silenceRinger(): CommandResult {
        return try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tm?.silenceRinger()
            }
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.ringerMode = AudioManager.RINGER_MODE_SILENT
            CommandResult.Success("Incoming call silenced")
        } catch (e: Exception) {
            CommandResult.Failure("Could not silence call: ${e.localizedMessage}")
        }
    }

    /** Answer the current incoming call. */
    fun answerCall(): CommandResult {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            ?: return CommandResult.Failure("TelecomManager unavailable.")

        return try {
            tm.acceptRingingCall()
            CommandResult.Success("Answering call")
        } catch (e: SecurityException) {
            CommandResult.NeedsPermission(
                "android.permission.ANSWER_PHONE_CALLS",
                "Answering calls requires the ANSWER_PHONE_CALLS permission."
            )
        } catch (e: Exception) {
            CommandResult.Failure("Could not answer call: ${e.localizedMessage}")
        }
    }

    /** Decline the current ringing call. */
    fun declineCall(): CommandResult {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            ?: return CommandResult.Failure("TelecomManager unavailable.")

        return try {
            tm.endCall()
            CommandResult.Success("Call declined")
        } catch (e: SecurityException) {
            CommandResult.NeedsPermission(
                "android.permission.ANSWER_PHONE_CALLS",
                "Declining calls requires the ANSWER_PHONE_CALLS permission."
            )
        } catch (e: Exception) {
            CommandResult.Failure("Could not decline call: ${e.localizedMessage}")
        }
    }

    @Volatile
    private var cachedContacts: List<Pair<String, String>>? = null
    @Volatile
    private var lastContactsCacheTime = 0L

    private fun getAllContacts(): List<Pair<String, String>> {
        val now = System.currentTimeMillis()
        val cached = cachedContacts
        if (cached != null && (now - lastContactsCacheTime) < 60_000L) {
            return cached
        }

        val cr: ContentResolver = context.contentResolver
        val cursor = try {
            cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Contacts permission not granted", e)
            return emptyList()
        }

        val list = mutableListOf<Pair<String, String>>()
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val rawName = it.getString(nameIdx) ?: continue
                val number = it.getString(numIdx) ?: continue
                list.add(Pair(rawName, number))
            }
        }

        cachedContacts = list
        lastContactsCacheTime = now
        return list
    }

    /**
     * Fuzzy multi-tier contact search.
     * Cleans emojis and punctuation, handles exact, prefix, token, and Levenshtein matches.
     */
    fun lookupBestContact(query: String): ContactMatch? {
        val cleanQuery = query.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
        if (cleanQuery.isBlank()) return null

        val contacts = getAllContacts()
        var bestMatch: ContactMatch? = null
        var highestScore = -1

        for ((rawName, number) in contacts) {
            val score = computeContactScore(rawName, cleanQuery)
            if (score > highestScore) {
                highestScore = score
                bestMatch = ContactMatch(rawName, number, score)
            }
        }

        return if (highestScore >= 40) bestMatch else null
    }

    /**
     * Query WhatsApp VoIP entries in ContactsContract.Data.
     */
    private fun lookupWhatsAppContact(query: String): WhatsAppMatch? {
        val cleanQuery = query.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
        if (cleanQuery.isBlank()) return null

        val cr: ContentResolver = context.contentResolver
        val cursor = try {
            cr.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data._ID,
                    ContactsContract.Data.DISPLAY_NAME,
                    ContactsContract.Data.DATA1
                ),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf("vnd.android.cursor.item/vnd.com.whatsapp.voip.call"),
                null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error querying WhatsApp contacts data", e)
            null
        }

        var bestMatch: WhatsAppMatch? = null
        var highestScore = -1

        cursor?.use {
            val idIdx = it.getColumnIndex(ContactsContract.Data._ID)
            val nameIdx = it.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
            val dataIdx = it.getColumnIndex(ContactsContract.Data.DATA1)

            while (it.moveToNext()) {
                val dataId = it.getLong(idIdx)
                val rawName = it.getString(nameIdx) ?: continue
                val data1 = it.getString(dataIdx) ?: ""

                val score = computeContactScore(rawName, cleanQuery)
                if (score > highestScore) {
                    highestScore = score
                    bestMatch = WhatsAppMatch(dataId, rawName, data1, score)
                }
            }
        }

        return if (highestScore >= 40) bestMatch else null
    }

    private fun computeContactScore(rawName: String, cleanQuery: String): Int {
        val cleanName = rawName.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
        if (cleanName == cleanQuery) return 100

        val nameTokens = cleanName.split(" ").filter { it.isNotBlank() }
        val queryTokens = cleanQuery.split(" ").filter { it.isNotBlank() }

        // All query tokens appear in name
        if (queryTokens.all { q -> nameTokens.any { n -> n == q } }) return 90

        // Starts with query
        if (cleanName.startsWith(cleanQuery)) return 85

        // Token starts with query (e.g. "teja" matches "tejaswin")
        if (nameTokens.any { it.startsWith(cleanQuery) }) return 80

        // Contains substring
        if (cleanName.contains(cleanQuery)) return 70

        // Levenshtein distance check on primary name token
        for (token in nameTokens) {
            val dist = levenshtein(token, cleanQuery)
            val maxLen = max(token.length, cleanQuery.length)
            if (maxLen > 0) {
                val similarity = 1.0 - (dist.toDouble() / maxLen)
                if (similarity >= 0.70) {
                    return (similarity * 65).toInt()
                }
            }
        }

        return 0
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun activateSpeakerphone() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val delays = listOf(600L, 1500L, 3000L)
        for (delay in delays) {
            mainHandler.postDelayed({
                try {
                    am.mode = AudioManager.MODE_IN_CALL
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = true
                    Log.i(TAG, "Speakerphone set to ON (mode=${am.mode})")
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting speakerphone", e)
                }
            }, delay)
        }
    }

    private fun dialFallback(name: String, number: String): CommandResult {
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult.Success(
                "Call permission not granted — opening dialer for $name. Tap call to proceed."
            )
        } catch (e: Exception) {
            CommandResult.Failure("Could not open dialer: ${e.localizedMessage}")
        }
    }
}
