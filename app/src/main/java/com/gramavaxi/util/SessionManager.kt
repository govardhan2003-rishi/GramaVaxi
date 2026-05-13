package com.gramavaxi.util

import android.content.Context

object SessionManager {
    private const val PREFS_NAME = "farmer_session"
    private const val KEY_FARMER_ID = "farmer_id"
    private const val KEY_FARMER_NAME = "farmer_name"
    private const val KEY_FARMER_PHONE = "farmer_phone"
    private const val KEY_ROLE = "role"
    const val ROLE_FARMER = "farmer"
    const val ROLE_DOCTOR = "doctor"

    fun saveSession(context: Context, farmerId: Int, farmerName: String, farmerPhone: String, role: String = ROLE_FARMER) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FARMER_ID, farmerId)
            .putString(KEY_FARMER_NAME, farmerName)
            .putString(KEY_FARMER_PHONE, farmerPhone)
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun currentFarmerId(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_FARMER_ID, 0)
    }

    fun currentFarmerPhone(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FARMER_PHONE, "") ?: ""
    }

    fun currentFarmerName(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FARMER_NAME, "") ?: ""
    }

    fun currentRole(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ROLE, ROLE_FARMER) ?: ROLE_FARMER
    }

    fun isLoggedIn(context: Context): Boolean {
        return currentFarmerId(context) > 0 || currentRole(context) == ROLE_DOCTOR
    }

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
