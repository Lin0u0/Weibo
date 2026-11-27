package com.example.myweibo.data

import android.content.Context

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
}
