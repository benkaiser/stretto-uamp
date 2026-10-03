package com.example.android.uamp.viewmodels

import android.content.Context

/** Persists which playlists the user has pinned to the home screen (in pinned order). */
class PinStore(context: Context) {
    private val prefs = context.getSharedPreferences("stretto_ui", Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getString(KEY_PINNED, null)?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()

    fun save(ids: List<String>) {
        prefs.edit().putString(KEY_PINNED, ids.joinToString("\n")).apply()
    }

    private companion object {
        const val KEY_PINNED = "pinned_playlists"
    }
}
