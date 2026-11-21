package com.bin624.dsh.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CurrencyBalance(
    val currency: String,
    val totalBalance: String,
    val grantedBalance: String,
    val toppedUpBalance: String
)

data class BalanceSnapshot(
    val isAvailable: Boolean,
    val balances: List<CurrencyBalance>
)

class DeepSeekApiException(
    message: String,
    val httpStatus: Int? = null
) : Exception(message)

object DeepSeekBalanceApi {
    private const val ENDPOINT = "https://api.deepseek.com/user/balance"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    suspend fun getBalance(apiKey: String): BalanceSnapshot = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "请先填写 DeepSeek API Key。" }

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer " + apiKey)
        }

        try {
            val status = connection.responseCode
            val responseStream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                val message = when (status) {
                    401, 403 -> "API Key 无效或已失效（HTTP " + status + "）。"
                    429 -> "请求过于频繁，请稍后重试（HTTP 429）。"
                    in 500..599 -> "DeepSeek 服务暂时不可用（HTTP " + status + "）。"
                    else -> "余额查询失败（HTTP " + status + "）。"
                }
                throw DeepSeekApiException(message, status)
            }

            if (body.isBlank()) {
                throw DeepSeekApiException("DeepSeek 返回了空响应。", status)
            }

            val json = try {
                JSONObject(body)
            } catch (_: Exception) {
                throw DeepSeekApiException("无法解析 DeepSeek 返回的数据。", status)
            }

            val balanceArray = json.optJSONArray("balance_infos")
            val balances = buildList {
                if (balanceArray != null) {
                    for (index in 0 until balanceArray.length()) {
                        val item = balanceArray.optJSONObject(index) ?: continue
                        add(
                            CurrencyBalance(
                                currency = item.optString("currency", "CNY"),
                                totalBalance = item.optString("total_balance", "0"),
                                grantedBalance = item.optString("granted_balance", "0"),
                                toppedUpBalance = item.optString("topped_up_balance", "0")
                            )
                        )
                    }
                }
            }

            BalanceSnapshot(
                isAvailable = json.optBoolean("is_available", false),
                balances = balances
            )
        } finally {
            connection.disconnect()
        }
    }
}
