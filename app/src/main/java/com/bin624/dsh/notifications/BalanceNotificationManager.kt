package com.bin624.dsh.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bin624.dsh.data.BalanceSnapshot
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

object BalanceNotificationManager {
    private const val CHANNEL_ID = "deepseek_balance_updates"
    private const val BALANCE_NOTIFICATION_ID = 62401
    private const val REMINDER_NOTIFICATION_ID = 62402
    fun showBalance(context: Context, snapshot: BalanceSnapshot) {
        val body = snapshot.balances.joinToString(" · ") { it.currency + " " + format(it.totalBalance) }
            .ifBlank { "已连接 DeepSeek，但接口未返回余额明细。" }
        notify(context, BALANCE_NOTIFICATION_ID, "DeepSeek 账户余额", body)
    }
    fun showUnavailable(context: Context) {
        notify(context, REMINDER_NOTIFICATION_ID, "DeepSeek 余额提醒", "DeepSeek 返回账户余额不可用，请检查账户余额后再调用 API。")
    }
    private fun notify(context: Context, id: Int, title: String, body: String) {
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try { NotificationManagerCompat.from(context).notify(id, notification) }
        catch (_: SecurityException) { /* Permission may have been revoked by the user. */ }
    }
    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "DeepSeek 余额通知", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "余额更新与余额不可用提醒" })
        }
    }
    private fun format(value: String): String = runCatching {
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(BigDecimal(value))
    }.getOrDefault(value)
}
