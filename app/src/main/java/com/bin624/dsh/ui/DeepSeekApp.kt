package com.bin624.dsh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.bin624.dsh.data.BalanceSnapshot
import com.bin624.dsh.data.DeepSeekBalanceApi
import com.bin624.dsh.security.ApiKeyStore
import com.bin624.dsh.ui.components.LiquidGlassBottomBar
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAB_OVERVIEW = 0
private const val TAB_BALANCE = 1
private const val TAB_KEY = 2
private const val TAB_SETTINGS = 3

@Composable
fun DeepSeekApp() {
    val context = LocalContext.current
    val keyStore = remember { ApiKeyStore(context) }
    val scope = rememberCoroutineScope()
    val pageBackdrop = rememberLayerBackdrop()

    var apiKey by remember {
        mutableStateOf(runCatching { keyStore.read() }.getOrNull())
    }
    var keyInput by remember { mutableStateOf("") }
    var keySaveError by remember { mutableStateOf<String?>(null) }
    var balance by remember { mutableStateOf<BalanceSnapshot?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastUpdated by remember { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_OVERVIEW) }

    fun requestBalance(key: String) {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            errorMessage = null
            try {
                balance = DeepSeekBalanceApi.getBalance(key)
                lastUpdated = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            } catch (exception: Exception) {
                errorMessage = exception.message ?: "查询失败，请检查网络后重试。"
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(apiKey) {
        val key = apiKey
        if (key.isNullOrBlank()) {
            balance = null
            lastUpdated = null
            errorMessage = null
        } else {
            requestBalance(key)
        }
    }

    val headerTitle = when (selectedTab) {
        TAB_BALANCE -> "余额明细"
        TAB_KEY -> "API Key"
        TAB_SETTINGS -> "设置"
        else -> "仪表盘"
    }
    val headerSubtitle = when (selectedTab) {
        TAB_BALANCE -> "查看各币种的余额构成"
        TAB_KEY -> "安全管理 DeepSeek API 密钥"
        TAB_SETTINGS -> "接口、刷新与隐私说明"
        else -> "DeepSeek API 账户概览"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .layerBackdrop(pageBackdrop)
                .background(Palette.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        headerTitle,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.text
                    )
                    Text(
                        headerSubtitle,
                        fontSize = 13.sp,
                        color = Palette.secondary
                    )
                }
                Box(
                    Modifier
                        .size(54.dp)
                        .shadow(8.dp, CircleShape)
                        .background(Palette.card, CircleShape)
                        .clickable {
                            val key = apiKey
                            if (key.isNullOrBlank()) {
                                selectedTab = TAB_KEY
                            } else {
                                requestBalance(key)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("↻", color = Palette.text, fontSize = 27.sp, fontWeight = FontWeight.Medium)
                }
            }

            when (selectedTab) {
                TAB_BALANCE -> BalanceDetailsScreen(balance, apiKey != null, lastUpdated)
                TAB_KEY -> ApiKeyScreen(
                    currentApiKey = apiKey,
                    keyInput = keyInput,
                    error = keySaveError,
                    onInputChange = {
                        keyInput = it
                        keySaveError = null
                    },
                    onSave = {
                        val value = keyInput.trim()
                        if (value.length < 8) {
                            keySaveError = "请填写完整的 DeepSeek API Key。"
                        } else {
                            try {
                                keyStore.save(value)
                                apiKey = value
                                keyInput = ""
                                keySaveError = null
                                selectedTab = TAB_OVERVIEW
                            } catch (_: Exception) {
                                keySaveError = "无法安全保存密钥，请检查设备安全存储状态。"
                            }
                        }
                    },
                    onRemove = {
                        try {
                            keyStore.clear()
                            apiKey = null
                            keyInput = ""
                            keySaveError = null
                            balance = null
                            selectedTab = TAB_OVERVIEW
                        } catch (_: Exception) {
                            keySaveError = "无法清除本地密钥，请稍后重试。"
                        }
                    }
                )
                TAB_SETTINGS -> SettingsScreen()
                else -> OverviewScreen(
                    balance = balance,
                    hasApiKey = !apiKey.isNullOrBlank(),
                    isRefreshing = isRefreshing,
                    lastUpdated = lastUpdated,
                    error = errorMessage,
                    onRefresh = {
                        val key = apiKey
                        if (key.isNullOrBlank()) selectedTab = TAB_KEY else requestBalance(key)
                    },
                    onManageKey = { selectedTab = TAB_KEY }
                )
            }

            Spacer(Modifier.height(124.dp))
        }

        LiquidGlassBottomBar(
            backdrop = pageBackdrop,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
        )
    }
}

@Composable
private fun OverviewScreen(
    balance: BalanceSnapshot?,
    hasApiKey: Boolean,
    isRefreshing: Boolean,
    lastUpdated: String?,
    error: String?,
    onRefresh: () -> Unit,
    onManageKey: () -> Unit
) {
    val first = balance?.balances?.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BalanceHeroCard(
            balance = balance,
            hasApiKey = hasApiKey,
            isRefreshing = isRefreshing,
            lastUpdated = lastUpdated,
            onRefresh = onRefresh
        )

        SectionHeading("余额构成", "赠送余额与充值余额")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "赠送余额",
                amount = first?.let { formatMoney(it.grantedBalance, it.currency) } ?: "--",
                hint = "未过期赠金",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "充值余额",
                amount = first?.let { formatMoney(it.toppedUpBalance, it.currency) } ?: "--",
                hint = "账户充值",
                modifier = Modifier.weight(1f)
            )
        }

        ApiKeySummaryCard(
            hasApiKey = hasApiKey,
            onManageKey = onManageKey
        )

        if (!hasApiKey) {
            InfoCard(
                title = "开始查询",
                body = "先添加 DeepSeek API Key。密钥会在本机加密保存，余额请求直接发送到 DeepSeek 官方 API。"
            )
        }

        if (error != null) {
            ErrorCard(error)
        }
    }
}

@Composable
private fun BalanceHeroCard(
    balance: BalanceSnapshot?,
    hasApiKey: Boolean,
    isRefreshing: Boolean,
    lastUpdated: String?,
    onRefresh: () -> Unit
) {
    val first = balance?.balances?.firstOrNull()
    val statusText = when {
        !hasApiKey -> "未连接"
        isRefreshing && balance == null -> "查询中"
        balance?.isAvailable == true -> "账户可用"
        balance != null -> "余额不可用"
        else -> "待查询"
    }
    val statusBackground = when {
        balance?.isAvailable == true -> Palette.greenBackground
        !hasApiKey -> Palette.softCard
        else -> Color(0xFFFFEDDA)
    }
    val statusColor = if (balance?.isAvailable == true) Palette.greenText else Palette.secondary

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFFFCFD), Color(0xFFE6EDFC), Color(0xFFF4F1F7))
                )
            )
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◉", fontSize = 17.sp, color = Palette.secondary)
            Text(
                "  账户状态",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.text
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(statusBackground)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("总余额", color = Palette.secondary, fontSize = 13.sp)
                Text(
                    first?.let { formatMoney(it.totalBalance, it.currency) } ?: "¥ --",
                    color = Palette.text,
                    fontSize = 34.sp,
                    lineHeight = 41.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (first == null) "添加 API Key 后查询" else "币种 " + first.currency,
                    color = Palette.secondary,
                    fontSize = 12.sp
                )
            }
            Box(
                Modifier
                    .size(68.dp)
                    .shadow(14.dp, CircleShape)
                    .background(Palette.blue, CircleShape)
                    .clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isRefreshing) "…" else "↻",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) {
                Text("最近查询", color = Palette.secondary, fontSize = 12.sp)
                Text(lastUpdated ?: "--:--", color = Palette.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {
                Text("API 连接", color = Palette.secondary, fontSize = 12.sp)
                Text(
                    if (hasApiKey) "已配置密钥" else "尚未配置",
                    color = Palette.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    amount: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.softCard)
            .padding(17.dp)
    ) {
        Text(title, color = Palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(9.dp))
        Text(
            amount,
            color = Palette.text,
            fontSize = 21.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(hint, color = Palette.secondary, fontSize = 11.sp)
    }
}

@Composable
private fun ApiKeySummaryCard(
    hasApiKey: Boolean,
    onManageKey: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Palette.card)
            .clickable(onClick = onManageKey)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("API Key", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Palette.text)
            Text(
                if (hasApiKey) "密钥已加密保存在本机" else "尚未添加 DeepSeek API Key",
                color = Palette.secondary,
                fontSize = 12.sp
            )
        }
        Text(
            if (hasApiKey) "管理  ›" else "添加  ›",
            color = Palette.blue,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BalanceDetailsScreen(
    balance: BalanceSnapshot?,
    hasApiKey: Boolean,
    lastUpdated: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InfoCard(
            title = "余额明细",
            body = if (lastUpdated == null) "尚未获取余额数据。" else "数据更新时间：" + lastUpdated
        )

        if (balance == null || balance.balances.isEmpty()) {
            EmptyStateCard(
                title = "暂无余额数据",
                body = if (hasApiKey) {
                    "返回仪表盘点击刷新，重新获取 DeepSeek 账户余额。"
                } else {
                    "先到密钥页面添加 API Key，再查询账户余额。"
                }
            )
        } else {
            balance.balances.forEach { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Palette.card)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.currency, color = Palette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatMoney(item.totalBalance, item.currency),
                            color = Palette.blue,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    DividerLine()
                    AmountRow("赠送余额", formatMoney(item.grantedBalance, item.currency))
                    AmountRow("充值余额", formatMoney(item.toppedUpBalance, item.currency))
                }
            }
            InfoCard(
                title = if (balance.isAvailable) "账户状态：可用" else "账户状态：不可用",
                body = "此状态来自 DeepSeek 返回的 is_available 字段，表示余额是否足以支持 API 调用。"
            )
        }
    }
}

@Composable
private fun ApiKeyScreen(
    currentApiKey: String?,
    keyInput: String,
    error: String?,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Palette.card)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("DeepSeek API Key", color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                if (currentApiKey.isNullOrBlank()) "当前未配置密钥" else "当前密钥：" + maskKey(currentApiKey),
                color = Palette.secondary,
                fontSize = 13.sp
            )
            BasicTextField(
                value = keyInput,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Palette.softCard)
                    .padding(horizontal = 16.dp, vertical = 17.dp),
                singleLine = true,
                textStyle = TextStyle(
                    color = Palette.text,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(Palette.blue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                decorationBox = { innerTextField ->
                    Box {
                        if (keyInput.isEmpty()) {
                            Text("粘贴你的 DeepSeek API Key", color = Palette.secondary, fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                }
            )
            if (error != null) {
                Text(error, color = Palette.dangerText, fontSize = 12.sp)
            }
            PrimaryAction("安全保存 API Key", onSave)
            if (!currentApiKey.isNullOrBlank()) {
                SecondaryAction("移除本机密钥", onRemove)
            }
        }

        InfoCard(
            title = "本机加密存储",
            body = "密钥使用 Android Keystore 保护的 AES-GCM 加密后保存在本机，不写入代码仓库，也不会上传到第三方服务器。余额请求仅发送至 api.deepseek.com。"
        )
        InfoCard(
            title = "如何获取 API Key",
            body = "在 DeepSeek 开放平台创建 API Key，然后粘贴到上方输入框。建议不要将密钥分享给他人。"
        )
    }
}

@Composable
private fun SettingsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsInfoCard(
            title = "查询接口",
            value = "GET https://api.deepseek.com/user/balance",
            detail = "使用 Authorization: Bearer API Key 进行鉴权。"
        )
        SettingsInfoCard(
            title = "自动查询",
            value = "启动应用时查询一次",
            detail = "进入应用后会读取本机密钥并请求最新余额；首页刷新按钮可手动重新查询。"
        )
        SettingsInfoCard(
            title = "数据范围",
            value = "总余额 · 赠送余额 · 充值余额",
            detail = "DeepSeek 余额接口不返回 Token 用量、消费流水或历史账单，本应用不会展示推算或虚构的用量数据。"
        )
        SettingsInfoCard(
            title = "隐私与网络",
            value = "仅连接 DeepSeek 官方 API",
            detail = "API Key 使用 Android Keystore 加密保存在设备本地，网络请求使用 HTTPS。"
        )
        Text(
            "DeepSeek 余额查询 · Android · 1.0.0",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = Palette.secondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SettingsInfoCard(
    title: String,
    value: String,
    detail: String
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.card)
            .padding(19.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(title, color = Palette.secondary, fontSize = 12.sp)
        Text(value, color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = Palette.secondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Palette.secondary, fontSize = 12.sp)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.card)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = Palette.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(body, color = Palette.secondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun EmptyStateCard(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Palette.card)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(title, color = Palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(body, color = Palette.secondary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun ErrorCard(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.dangerBackground)
            .padding(16.dp)
    ) {
        Text(message, color = Palette.dangerText, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun AmountRow(label: String, amount: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.secondary, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(amount, color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DividerLine() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Palette.divider)
    )
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.blue)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.softCard)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Palette.dangerText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatMoney(amount: String, currency: String): String {
    val symbol = when (currency.uppercase(Locale.ROOT)) {
        "CNY" -> "¥ "
        "USD" -> "$ "
        else -> currency + " "
    }
    val formatted = runCatching {
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(BigDecimal(amount))
    }.getOrDefault(amount)
    return symbol + formatted
}

private fun maskKey(key: String): String {
    val suffix = key.takeLast(4)
    return "••••••••" + suffix
}
