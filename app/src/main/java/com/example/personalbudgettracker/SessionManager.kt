package com.example.personalbudgettracker

import android.content.Context

/** Stores only the signed-in account identifier; account data remains in SQLite. */
object SessionManager {
    private const val PREFERENCES = "smart_budget_session"
    private const val KEY_EMAIL = "authenticated_email"

    fun signIn(context: Context, email: String) {
        preferences(context).edit()
            .putString(KEY_EMAIL, email.trim().lowercase())
            .apply()
    }

    fun currentEmail(context: Context): String? =
        preferences(context).getString(KEY_EMAIL, null)

    fun signOut(context: Context) {
        preferences(context).edit().remove(KEY_EMAIL).apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
