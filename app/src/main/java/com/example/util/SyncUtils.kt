package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object SyncUtils {
    private const val PREFS_NAME = "agri_stock_sync_prefs"
    private const val KEY_LAST_PRODUCT_SYNC = "last_product_sync_at"

    fun getLastProductSyncAt(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_PRODUCT_SYNC, 0L)
    }

    fun setLastProductSyncAt(context: Context, timestamp: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_PRODUCT_SYNC, timestamp).apply()
    }

    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    fun formatTimeAgo(timestampMs: Long): String {
        if (timestampMs == 0L) return "Waiting for first sync..."
        val now = System.currentTimeMillis()
        val diffMs = maxOf(0L, now - timestampMs)
        val minutes = diffMs / (1000 * 60)
        val hours = minutes / 60
        return when {
            minutes < 1 -> "Updated just now"
            minutes < 60 -> "Updated $minutes ${if (minutes == 1L) "min" else "mins"} ago"
            hours < 24 -> "Updated $hours ${if (hours == 1L) "hour" else "hours"} ago"
            else -> "Updated ${hours / 24} days ago"
        }
    }
}
