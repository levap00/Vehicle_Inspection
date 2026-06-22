package com.example.vehicleinspection

import android.content.Context

object ApiConfig {
    const val API_BASE = "https://driver.ithowtozone.com"

    private const val PREFS = "vi_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_DRIVER_NAME = "driver_name"
    private const val KEY_DRIVER_ID = "driver_id"

    fun getToken(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun saveLogin(ctx: Context, token: String, driverName: String, driverId: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_DRIVER_NAME, driverName)
            .putInt(KEY_DRIVER_ID, driverId)
            .apply()
    }

    fun getDriverName(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DRIVER_NAME, "") ?: ""

    fun clearAuth(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_TOKEN).remove(KEY_DRIVER_NAME).remove(KEY_DRIVER_ID).apply()
    }

    fun authHeader(ctx: Context): String {
        val token = getToken(ctx) ?: return ""
        return "Bearer $token"
    }
}
