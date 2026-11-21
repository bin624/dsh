package com.bin624.dsh.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BalanceHistoryPoint(
    val timestamp: Long,
    val currency: String,
    val amount: Double
)

object BalanceHistoryStore {
    private const val PREFS = "deepseek_balance_history"
    private const val KEY_POINTS = "points"
    private const val MAX_POINTS = 120

    fun read(context: Context): List<BalanceHistoryPoint> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_POINTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val amount = item.optDouble("amount", Double.NaN)
                    if (!amount.isNaN() && !amount.isInfinite()) {
                        add(BalanceHistoryPoint(item.optLong("timestamp"), item.optString("currency", "CNY"), amount))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun record(context: Context, snapshot: BalanceSnapshot) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = read(context).toMutableList()
        val now = System.currentTimeMillis()
        snapshot.balances.forEach { balance ->
            val amount = runCatching { balance.totalBalance.toDouble() }.getOrNull() ?: return@forEach
            if (amount.isNaN() || amount.isInfinite()) return@forEach
            existing.add(BalanceHistoryPoint(now, balance.currency, amount))
        }
        val array = JSONArray()
        existing.takeLast(MAX_POINTS).forEach { point ->
            array.put(JSONObject().put("timestamp", point.timestamp).put("currency", point.currency).put("amount", point.amount))
        }
        prefs.edit().putString(KEY_POINTS, array.toString()).apply()
    }
}
