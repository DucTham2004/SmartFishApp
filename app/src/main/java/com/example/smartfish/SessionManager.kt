package com.example.smartfish

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    // Tên file SharedPreferences
    private companion object {
        const val PREFS_NAME = "smartfish_prefs"
        const val KEY_AUTH_TOKEN = "auth_token"
    }

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Lưu token vào SharedPreferences
     */
    fun saveAuthToken(token: String) {
        val editor = sharedPrefs.edit()
        editor.putString(KEY_AUTH_TOKEN, token)
        editor.apply()
    }

    /**
     * Lấy token đã lưu
     * Trả về null nếu không tìm thấy
     */
    fun fetchAuthToken(): String? {
        return sharedPrefs.getString(KEY_AUTH_TOKEN, null)
    }

    /**
     * Xóa token (khi đăng xuất)
     */
    fun clearAuthToken() {
        val editor = sharedPrefs.edit()
        editor.remove(KEY_AUTH_TOKEN)
        editor.apply()
    }
}