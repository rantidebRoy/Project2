package com.example.flashcard

import android.content.Context
import android.content.SharedPreferences

// Define constants for SharedPreferences keys and file name
private const val PREFS_NAME = "StudyBuddyPrefs"
private const val KEY_IS_LOGGED_IN = "is_logged_in"
private const val KEY_USER_ID = "user_id" // Added key for storing the User ID

/**
 * Manages the user's session state using SharedPreferences.
 * It's responsible for saving and retrieving whether the user is currently logged in,
 * providing a seamless experience upon app restart.
 */
class SessionManager(context: Context) {

    // Get a reference to the SharedPreferences file
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Saves the user session data (sets logged in to true and stores the userId).
     */
    fun saveSession(userId: String) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    /**
     * Retrieves the current login state. Defaults to false (not logged in).
     */
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * Retrieves the current logged-in user ID. Returns null if not logged in.
     */
    fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }

    /**
     * Clears the user's session by setting the login state to false and removing the user ID.
     */
    fun clearSession() {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_USER_ID)
            .apply()
    }
}
