package com.bin624.dsh.notifications

import android.app.NotificationChannel
import android.graphics.BitmapFactory
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bin624.dsh.data.BalanceSnapshot
import java.math.BigDecimal
import android.content.SharedPreferences
import android.preference.PreferenceManager
import java.text.NumberFormat
import java.util.Locale

object BalanceNotificationManager {
    private const val CHANNEL_ID = "deepseek_balance_updates_v2"
    private const val BALANCE_NOTIFICATION_ID = 62401
    private const val REMINDER_NOTIFICATION_ID = 62402
    fun showBalance(context: Context, snapshot: BalanceSnapshot) {
        val body = snapshot.balances.joinToString(" · ") { it.currency + " " + format(it.totalBalance) }
            .ifBlank { "已连接 DeepSeek，但接口未返回余额明细。" }
        notify(context, BALANCE_NOTIFICATION_ID, "DeepSeek 账户余额", body)
    }
    fun showKeyRemoved(context: Context) {
        notify(
            context,
            BALANCE_NOTIFICATION_ID,
            "DeepSeek 账户状态",
            "API Key 已移除，账户未连接，余额通知已暂停。"
        )
    }

    fun cancelBalance(context: Context) {
        NotificationManagerCompat.from(context).cancel(BALANCE_NOTIFICATION_ID)
    }

    fun showLowBalanceIfNeeded(context: Context, snapshot: BalanceSnapshot, thresholdCny: Double) {
        val cny = snapshot.balances.firstOrNull { it.currency.equals("CNY", ignoreCase = true) } ?: return
        val current = runCatching { BigDecimal(cny.totalBalance) }.getOrNull() ?: return
        if (current >= BigDecimal.valueOf(thresholdCny.coerceAtLeast(0.0))) return
        val prefs = context.getSharedPreferences("deepseek_notification_state", Context.MODE_PRIVATE)
        val lastSent = prefs.getLong("last_low_balance_notification", 0L)
        val now = System.currentTimeMillis()
        if (now - lastSent < 12L * 60L * 60L * 1000L) return
        val body = "当前余额 ¥" + format(cny.totalBalance) + "，已低于提醒阈值 ¥" + format(thresholdCny.toString()) + "。"
        notify(context, REMINDER_NOTIFICATION_ID, "DeepSeek 余额偏低", body)
        prefs.edit().putLong("last_low_balance_notification", now).apply()
    }
    private fun notify(context: Context, id: Int, title: String, body: String) {
        createChannel(context)
        val whaleIcon = runCatching { BitmapFactory.decodeResource(context.resources, com.bin624.dsh.R.drawable.whale_disconnected) }.getOrNull()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(whaleIcon)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTicker(title + "：" + body).setWhen(System.currentTimeMillis()).setShowWhen(true)
            .setOngoing(id == BALANCE_NOTIFICATION_ID).setAutoCancel(id != BALANCE_NOTIFICATION_ID)
            .setOnlyAlertOnce(false).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS).build()
        try { NotificationManagerCompat.from(context).notify(id, notification) }
        catch (_: SecurityException) { /* Permission may have been revoked by the user. */ }
    }
    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "DeepSeek 余额通知", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "账户余额内容与低余额提醒" })
        }
    }
    private fun format(value: String): String = runCatching {
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(BigDecimal(value))
    }.getOrDefault(value)
}
