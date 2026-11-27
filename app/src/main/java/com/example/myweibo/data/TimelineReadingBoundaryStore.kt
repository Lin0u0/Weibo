package com.example.myweibo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class TimelineReadingBoundaryStore(context: Context) {
    // Old unread high-water marks record fetches, not qualified views; never migrate them.
    private val prefs = context.applicationContext.getSharedPreferences("timeline_reading_boundary_v1", Context.MODE_PRIVATE)

    fun read(scope: TimelineReadingScope): TimelineReadingAnchor? {
        val key = scope.storageKey
        return prefs.getString("$key.row", null)?.takeIf { it.isNotBlank() && it != "0" }
            ?.let { TimelineReadingAnchor(it, prefs.getString("$key.status", null)) }
    }

    fun write(scope: TimelineReadingScope, anchor: TimelineReadingAnchor) {
        val key = scope.storageKey
        prefs.edit().putString("$key.row", anchor.rowId).putString("$key.status", anchor.statusId).apply()
    }

    fun readRefreshBatch(scope: TimelineReadingScope): TimelineRefreshReadingBatch? {
        val json = prefs.getString("${scope.storageKey}.refresh_batch", null) ?: return null
        return try {
            val record = JSONObject(json)
            fun JSONObject.anchor(): TimelineReadingAnchor = TimelineReadingAnchor(
                getString("row"), optString("status").takeIf { it.isNotBlank() },
            )
            val remaining = record.getJSONArray("remaining")
            TimelineRefreshReadingBatch(
                record.getJSONObject("baseline").anchor(),
                List(remaining.length()) { remaining.getJSONObject(it).anchor() },
                record.optJSONObject("viewed")?.anchor(),
                record.optBoolean("force_projection"),
            )
        } catch (_: org.json.JSONException) {
            null
        }
    }

    fun writeRefreshBatch(scope: TimelineReadingScope, batch: TimelineRefreshReadingBatch) {
        fun TimelineReadingAnchor.json(): JSONObject = JSONObject().put("row", rowId).put("status", statusId)
        val remaining = JSONArray()
        batch.remaining.forEach { remaining.put(it.json()) }
        val record = JSONObject().put("baseline", batch.baseline.json()).put("remaining", remaining)
            .put("viewed", batch.viewedThrough?.json()).put("force_projection", batch.forceProjection)
        prefs.edit().putString("${scope.storageKey}.refresh_batch", record.toString()).apply()
    }
}
