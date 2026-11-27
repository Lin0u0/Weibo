package com.example.myweibo.data

import android.content.Context

internal class TimelineUnreadStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("timeline_unread_v1", Context.MODE_PRIVATE)

    fun read(scope: TimelineUnreadScope): TimelineUnreadSnapshot {
        val key = scope.storageKey
        val sortKey = prefs.getLong("$key.head_time", 0)
        val head = prefs.getString("$key.head_status", null)?.takeIf { it.isNotBlank() && sortKey > 0 }
            ?.let { TimelineUnreadHead(it, sortKey) }
        return TimelineUnreadSnapshot(
            head, prefs.getStringSet("$key.unread", null)?.toSet().orEmpty(),
            prefs.getBoolean("$key.order_confirmed", true),
        )
    }

    fun write(scope: TimelineUnreadScope, snapshot: TimelineUnreadSnapshot) {
        val key = scope.storageKey
        // Write the boundary and exact IDs together; do not store a running total.
        prefs.edit().putLong("$key.head_time", snapshot.head?.sortKey ?: 0)
            .putString("$key.head_status", snapshot.head?.statusId)
            .putStringSet("$key.unread", snapshot.unread)
            .putBoolean("$key.order_confirmed", snapshot.orderConfirmed)
            .apply()
    }
}
