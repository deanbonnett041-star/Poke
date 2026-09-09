package com.aicardgrader.app.ximilar

import android.content.Context

/**
 * Stores the user's own Ximilar API key locally on this device only --
 * never bundled into the app build or committed to source control. A key
 * baked into a redistributed debug APK could be extracted by anyone who
 * downloads it and billed against the owner's Ximilar account, so it's
 * entered once in Settings and kept in this app's private storage instead.
 */
object ApiKeySettings {
    private const val PREFS_NAME = "slabrate_settings"
    private const val KEY_XIMILAR_API_KEY = "ximilar_api_key"

    fun getXimilarApiKey(context: Context): String? =
        prefs(context).getString(KEY_XIMILAR_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setXimilarApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_XIMILAR_API_KEY, key.trim()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
