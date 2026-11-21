package com.bin624.dsh.settings

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bin624.dsh.worker.BalanceRefreshWorker
import java.util.concurrent.TimeUnit

class AppSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("deepseek_app_settings", Context.MODE_PRIVATE)
    var appearanceMode: String
        get() = preferences.getString(KEY_APPEARANCE, APPEARANCE_SYSTEM) ?: APPEARANCE_SYSTEM
        set(value) { preferences.edit().putString(KEY_APPEARANCE, value).apply() }
    var refreshIntervalMinutes: Int
        get() = preferences.getInt(KEY_REFRESH_INTERVAL, 0)
        set(value) { preferences.edit().putInt(KEY_REFRESH_INTERVAL, value.coerceAtLeast(0)).apply() }
    var balanceReminderThreshold: Double
        get() = preferences.getFloat(KEY_BALANCE_THRESHOLD, 10f).toDouble()
        set(value) { preferences.edit().putFloat(KEY_BALANCE_THRESHOLD, value.coerceAtLeast(0.0).toFloat()).apply() }
    var balanceReminderEnabled: Boolean
        get() = preferences.getBoolean(KEY_BALANCE_REMINDER, false)
        set(value) { preferences.edit().putBoolean(KEY_BALANCE_REMINDER, value).apply() }
    var statusBalanceNotificationEnabled: Boolean
        get() = preferences.getBoolean(KEY_STATUS_NOTIFICATION, false)
        set(value) { preferences.edit().putBoolean(KEY_STATUS_NOTIFICATION, value).apply() }
    companion object {
        const val APPEARANCE_SYSTEM = "system"
        const val APPEARANCE_LIGHT = "light"
        const val APPEARANCE_DARK = "dark"
        private const val KEY_APPEARANCE = "appearance_mode"
        private const val KEY_REFRESH_INTERVAL = "refresh_interval_minutes"
        private const val KEY_BALANCE_REMINDER = "balance_reminder_enabled"
        private const val KEY_BALANCE_THRESHOLD = "balance_reminder_threshold"
        private const val KEY_STATUS_NOTIFICATION = "status_balance_notification_enabled"
    }
}

object AutoRefreshScheduler {
    private const val UNIQUE_WORK_NAME = "deepseek_periodic_balance_refresh"
    fun apply(context: Context, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (intervalMinutes < 15) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<BalanceRefreshWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints).build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
