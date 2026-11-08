package com.example.jarvis.memory

import android.content.Context
import androidx.core.content.edit

/**
 * Version 2.0 Context & Relationship Memory.
 * Persists user identity, preferred name, and contact relationships (e.g. brother -> Teja, mom -> Mom).
 * Zero cloud dependency — stored locally in SharedPreferences.
 */
class MemoryRepository(context: Context) {

    private val prefs = context.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)

    var userName: String?
        get() = prefs.getString("user_name", null)
        set(value) {
            prefs.edit {
                if (value != null) putString("user_name", value.trim())
                else remove("user_name")
            }
        }

    fun setRelationship(relation: String, contactName: String) {
        val key = "rel_${relation.lowercase().trim()}"
        prefs.edit { putString(key, contactName.trim()) }
    }

    fun getRelationship(relation: String): String? {
        val key = "rel_${relation.lowercase().trim()}"
        return prefs.getString(key, null)
    }

    fun removeRelationship(relation: String): Boolean {
        val key = "rel_${relation.lowercase().trim()}"
        return if (prefs.contains(key)) {
            prefs.edit { remove(key) }
            true
        } else false
    }

    fun getAllRelationships(): Map<String, String> {
        val all = prefs.all
        val result = mutableMapOf<String, String>()
        for ((k, v) in all) {
            if (k.startsWith("rel_") && v is String) {
                result[k.removePrefix("rel_")] = v
            }
        }
        return result
    }

    // ── Custom Workflows / Routines (v5) ──────────────────────────────────

    fun saveWorkflow(name: String, steps: List<String>) {
        val jsonArray = org.json.JSONArray(steps)
        val key = "wf_${name.lowercase().trim()}"
        prefs.edit { putString(key, jsonArray.toString()) }
    }

    fun getWorkflow(name: String): List<String>? {
        val key = "wf_${name.lowercase().trim()}"
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val jsonArray = org.json.JSONArray(raw)
            List(jsonArray.length()) { jsonArray.getString(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun deleteWorkflow(name: String): Boolean {
        val key = "wf_${name.lowercase().trim()}"
        return if (prefs.contains(key)) {
            prefs.edit { remove(key) }
            true
        } else false
    }

    fun getAllWorkflows(): Map<String, List<String>> {
        val all = prefs.all
        val result = mutableMapOf<String, List<String>>()
        for ((k, v) in all) {
            if (k.startsWith("wf_") && v is String) {
                try {
                    val arr = org.json.JSONArray(v)
                    result[k.removePrefix("wf_")] = List(arr.length()) { arr.getString(it) }
                } catch (_: Exception) {}
            }
        }
        return result
    }

    fun clearAll() {
        prefs.edit { clear() }
    }
}
