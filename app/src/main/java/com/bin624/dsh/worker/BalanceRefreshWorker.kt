package com.bin624.dsh.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bin624.dsh.data.DeepSeekBalanceApi
import com.bin624.dsh.data.BalanceHistoryStore
import com.bin624.dsh.notifications.BalanceNotificationManager
import com.bin624.dsh.security.ApiKeyStore
import com.bin624.dsh.settings.AppSettings

class BalanceRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext)
        if (settings.refreshIntervalMinutes < 15) return Result.success()
        val key = runCatching { ApiKeyStore(applicationContext).read() }.getOrNull()
        if (key.isNullOrBlank()) return Result.success()
        return try {
            val snapshot = DeepSeekBalanceApi.getBalance(key)
            BalanceHistoryStore.record(applicationContext, snapshot)
            if (settings.statusBalanceNotificationEnabled) BalanceNotificationManager.showBalance(applicationContext, snapshot)
            if (settings.balanceReminderEnabled) BalanceNotificationManager.showLowBalanceIfNeeded(applicationContext, snapshot, settings.balanceReminderThreshold)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
