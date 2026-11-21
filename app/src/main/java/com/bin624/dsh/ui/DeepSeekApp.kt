package com.bin624.dsh.ui

import android.Manifest
import android.graphics.Movie
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.withFrameNanos
import android.os.Build
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bin624.dsh.notifications.BalanceNotificationManager
import com.bin624.dsh.settings.AppSettings
import com.bin624.dsh.settings.AutoRefreshScheduler

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
import androidx.compose.ui.platform.LocalUriHandler
import com.bin624.dsh.data.BalanceSnapshot
import com.bin624.dsh.data.BalanceHistoryPoint
import com.bin624.dsh.data.BalanceHistoryStore
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
private const val TAB_SETTINGS = 2
private const val NOTIFY_BALANCE_REMINDER = 1
private const val NOTIFY_STATUS_BALANCE = 2

@Composable
fun DeepSeekApp() {
    val context = LocalContext.current
    val keyStore = remember { ApiKeyStore(context) }
    val appSettings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()
    val pageBackdrop = rememberLayerBackdrop()

    var apiKey by remember {
        mutableStateOf(runCatching { keyStore.read() }.getOrNull())
    }
    var keyInput by remember { mutableStateOf("") }
    var keySaveError by remember { mutableStateOf<String?>(null) }
    var balance by remember { mutableStateOf<BalanceSnapshot?>(null) }
    var balanceHistory by remember { mutableStateOf(BalanceHistoryStore.read(context)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastUpdated by remember { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable("mainTabV2") { mutableIntStateOf(TAB_OVERVIEW) }
    var isConnected by remember { mutableStateOf(false) }
    var appearanceMode by rememberSaveable { mutableStateOf(appSettings.appearanceMode) }
    var refreshIntervalMinutes by rememberSaveable { mutableIntStateOf(appSettings.refreshIntervalMinutes) }
    var balanceReminderEnabled by rememberSaveable { mutableStateOf(appSettings.balanceReminderEnabled) }
    var balanceThresholdText by rememberSaveable { mutableStateOf(appSettings.balanceReminderThreshold.toString()) }
    var statusBalanceNotificationEnabled by rememberSaveable { mutableStateOf(appSettings.statusBalanceNotificationEnabled) }
    var settingsMessage by remember { mutableStateOf<String?>(null) }
    var pendingNotificationOption by remember { mutableIntStateOf(0) }
    var showAboutPage by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val option = pendingNotificationOption
        pendingNotificationOption = 0
        if (granted) {
            when (option) {
                NOTIFY_BALANCE_REMINDER -> {
                    balanceReminderEnabled = true
                    appSettings.balanceReminderEnabled = true
                }
                NOTIFY_STATUS_BALANCE -> {
                    statusBalanceNotificationEnabled = true
                    appSettings.statusBalanceNotificationEnabled = true
                }
            }
            settingsMessage = "通知权限已开启。"
        } else {
            settingsMessage = "系统未授予通知权限，请在系统设置中允许本应用发送通知。"
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val useDarkTheme = when (appearanceMode) {
        AppSettings.APPEARANCE_LIGHT -> false
        AppSettings.APPEARANCE_DARK -> true
        else -> systemDark
    }
    androidx.compose.runtime.SideEffect {
        Palette.isDarkTheme = useDarkTheme
        (context as? android.app.Activity)?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !useDarkTheme
                isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    fun updateNotificationOption(option: Int, enabled: Boolean) {
        if (!enabled) {
            when (option) {
                NOTIFY_BALANCE_REMINDER -> {
                    balanceReminderEnabled = false
                    appSettings.balanceReminderEnabled = false
                }
                NOTIFY_STATUS_BALANCE -> {
                    statusBalanceNotificationEnabled = false
                    appSettings.statusBalanceNotificationEnabled = false
                }
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingNotificationOption = option
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            when (option) {
                NOTIFY_BALANCE_REMINDER -> {
                    balanceReminderEnabled = true
                    appSettings.balanceReminderEnabled = true
                }
                NOTIFY_STATUS_BALANCE -> {
                    statusBalanceNotificationEnabled = true
                    appSettings.statusBalanceNotificationEnabled = true
                }
            }
        }
    }

    fun requestBalance(key: String, isConnectionTest: Boolean = false) {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            errorMessage = null
            if (isConnectionTest) settingsMessage = "正在测试 DeepSeek 连接…"
            try {
                val snapshot = DeepSeekBalanceApi.getBalance(key)
                balance = snapshot
                BalanceHistoryStore.record(context, snapshot)
                balanceHistory = BalanceHistoryStore.read(context)
                isConnected = true
                lastUpdated = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                if (statusBalanceNotificationEnabled) BalanceNotificationManager.showBalance(context, snapshot)
                if (balanceReminderEnabled) BalanceNotificationManager.showLowBalanceIfNeeded(context, snapshot, appSettings.balanceReminderThreshold)
                if (isConnectionTest) settingsMessage = "连接成功，DeepSeek 余额接口已返回数据。"
            } catch (exception: Exception) {
                isConnected = false
                errorMessage = exception.message ?: "查询失败，请检查网络后重试。"
                if (isConnectionTest) settingsMessage = "连接失败：" + (exception.message ?: "请检查 API Key 和网络。")
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(apiKey) {
        val key = apiKey
        if (key.isNullOrBlank()) {
            balance = null
            isConnected = false
            lastUpdated = null
            errorMessage = null
            if (statusBalanceNotificationEnabled) {
                BalanceNotificationManager.showKeyRemoved(context)
            } else {
                BalanceNotificationManager.cancelBalance(context)
            }
        } else {
            requestBalance(key)
        }
    }

    LaunchedEffect(statusBalanceNotificationEnabled) {
        if (statusBalanceNotificationEnabled) {
            val key = apiKey
            if (!key.isNullOrBlank()) requestBalance(key)
        } else {
            BalanceNotificationManager.cancelBalance(context)
        }
    }

    LaunchedEffect(refreshIntervalMinutes) {
        appSettings.refreshIntervalMinutes = refreshIntervalMinutes
        AutoRefreshScheduler.apply(context, refreshIntervalMinutes)
    }

    val headerTitle = when (selectedTab) {
        TAB_BALANCE -> "余额明细"
        TAB_SETTINGS -> if (showAboutPage) "关于" else "设置"
        else -> "仪表盘"
    }
    val headerSubtitle = when (selectedTab) {
        TAB_BALANCE -> "查看各币种的账户余额"
        TAB_SETTINGS -> if (showAboutPage) "开源信息与社群" else "提醒、刷新、外观与连接设置"
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
                if (selectedTab == TAB_SETTINGS && showAboutPage) {
                    Box(
                        Modifier
                            .padding(end = 12.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Palette.card)
                            .clickable { showAboutPage = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("←", color = Palette.text, fontSize = 27.sp)
                    }
                }
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
            }

            when (selectedTab) {
                TAB_BALANCE -> BalanceDetailsScreen(balance, apiKey != null, lastUpdated, balanceHistory)
                TAB_SETTINGS -> if (showAboutPage) {
                    AboutScreen()
                } else SettingsScreen(
                    onOpenAbout = { showAboutPage = true },
                    currentApiKey = apiKey,
                    keyInput = keyInput,
                    onKeyInputChange = { keyInput = it; settingsMessage = null },
                    onSaveKey = {
                        val value = keyInput.trim()
                        if (value.length < 8) {
                            settingsMessage = "请填写完整的 DeepSeek API Key。"
                        } else runCatching { keyStore.save(value) }
                            .onSuccess { apiKey = value; keyInput = ""; settingsMessage = "API Key 已加密保存在本机。" }
                            .onFailure { settingsMessage = "无法安全保存密钥，请检查设备安全存储状态。" }
                    },
                    onTestConnection = {
                        val candidate = keyInput.trim().ifBlank { apiKey.orEmpty() }
                        if (candidate.isBlank()) settingsMessage = "请先输入 API Key。"
                        else requestBalance(candidate, isConnectionTest = true)
                    },
                    onRemoveKey = {
                        runCatching { keyStore.clear() }
                            .onSuccess {
                                apiKey = null
                                keyInput = ""
                                balance = null
                                isConnected = false
                                BalanceNotificationManager.showKeyRemoved(context)
                                settingsMessage = "已移除本机保存的 API Key，通知栏状态已更新。"
                            }
                            .onFailure { settingsMessage = "无法清除本地密钥，请稍后重试。" }
                    },
                    appearanceMode = appearanceMode,
                    onAppearanceChange = { appearanceMode = it; appSettings.appearanceMode = it },
                    refreshIntervalMinutes = refreshIntervalMinutes,
                    onRefreshIntervalChange = { refreshIntervalMinutes = it },
                    balanceReminderEnabled = balanceReminderEnabled,
                    onBalanceReminderChange = { updateNotificationOption(NOTIFY_BALANCE_REMINDER, it) },
                    balanceThresholdText = balanceThresholdText,
                    onBalanceThresholdChange = { value ->
                        balanceThresholdText = value
                        value.toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO }?.let { appSettings.balanceReminderThreshold = it.toDouble() }
                    },
                    statusBalanceNotificationEnabled = statusBalanceNotificationEnabled,
                    onStatusBalanceNotificationChange = { updateNotificationOption(NOTIFY_STATUS_BALANCE, it) },
                    isTestingConnection = isRefreshing,
                    connectionMessage = settingsMessage
                )
                else -> OverviewScreen(
                    balance = balance,
                    hasApiKey = !apiKey.isNullOrBlank(),
                    isRefreshing = isRefreshing,
                    isConnected = isConnected,
                    lastUpdated = lastUpdated,
                    error = errorMessage,
                    lowBalance = balanceReminderEnabled && (balance?.balances?.firstOrNull { it.currency.equals("CNY", true) }?.totalBalance?.let { runCatching { BigDecimal(it) < BigDecimal.valueOf(appSettings.balanceReminderThreshold) }.getOrDefault(false) } == true),
                    lowBalanceThreshold = appSettings.balanceReminderThreshold,
                    onRefresh = {
                        val key = apiKey
                        if (key.isNullOrBlank()) errorMessage = "请先到设置页配置 API Key，再进行余额查询。" else requestBalance(key)
                    },
                )
            }

            Spacer(Modifier.height(if (showAboutPage) 32.dp else 124.dp))
        }

        if (!showAboutPage) {
            LiquidGlassBottomBar(
                backdrop = pageBackdrop,
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it; showAboutPage = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
            )
        }
    }
}

@Composable
private fun OverviewScreen(
    balance: BalanceSnapshot?,
    hasApiKey: Boolean,
    isRefreshing: Boolean,
    isConnected: Boolean,
    lastUpdated: String?,
    error: String?,
    lowBalance: Boolean,
    lowBalanceThreshold: Double,
    onRefresh: () -> Unit
) {
    val first = balance?.balances?.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (lowBalance) LowBalanceWarningCard(balance, lowBalanceThreshold)
        BalanceHeroCard(
            balance = balance,
            hasApiKey = hasApiKey,
            isRefreshing = isRefreshing,
            isConnected = isConnected,
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

        if (error != null) {
            ErrorCard(error)
        }
    }
}

@Composable
private fun LowBalanceWarningCard(balance: BalanceSnapshot?, threshold: Double) {
    val cny = balance?.balances?.firstOrNull { it.currency.equals("CNY", true) }?.totalBalance ?: return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFE8E5)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("余额偏低，请及时充值", color = Color(0xFF9D3028), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("当前余额 ¥" + formatMoney(cny, "CNY") + "，低于提醒阈值 ¥" + formatMoney(threshold.toString(), "CNY") + "。", color = Color(0xFF9D3028), fontSize = 12.sp)
    }
}

@Composable
private fun BalanceHeroCard(
    balance: BalanceSnapshot?,
    hasApiKey: Boolean,
    isRefreshing: Boolean,
    isConnected: Boolean,
    lastUpdated: String?,
    onRefresh: () -> Unit
) {
    val first = balance?.balances?.firstOrNull()
    val statusText = when {
        isRefreshing && !isConnected -> "连接中"
        isConnected -> "已连接"
        hasApiKey -> "连接失败"
        else -> "未连接"
    }
    val statusBackground = if (isConnected) Palette.greenBackground else Palette.softCard
    val statusColor = if (isConnected) Palette.greenText else Palette.secondary

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    if (Palette.isDarkTheme) {
                        listOf(Color(0xFF1C2C42), Color(0xFF253D5A), Color(0xFF1A2738))
                    } else {
                        listOf(Color(0xFFFFFCFD), Color(0xFFE6EDFC), Color(0xFFF4F1F7))
                    }
                )
            )
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("账户状态", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.text)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.clip(CircleShape).background(statusBackground).padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (balance?.isAvailable == true) "账户余额可用" else if (isConnected) "账户余额暂不可用" else "连接成功后鲸鱼娘会动起来",
                    color = Palette.secondary, fontSize = 11.sp, lineHeight = 15.sp
                )
            }
            WhaleStatusBlob(connected = isConnected, modifier = Modifier.size(154.dp))
        }
        Spacer(Modifier.height(16.dp))
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
            androidx.compose.material.Button(
                onClick = onRefresh,
                enabled = !isRefreshing,
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material.ButtonDefaults.buttonColors(backgroundColor = Palette.blue, contentColor = Color.White)
            ) {
                Text(if (isRefreshing) "刷新中…" else "手动刷新", fontWeight = FontWeight.SemiBold)
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
    lastUpdated: String?,
    history: List<BalanceHistoryPoint>
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InfoCard(
            title = "余额明细",
            body = if (lastUpdated == null) "尚未获取余额数据。" else "数据更新时间：" + lastUpdated
        )

        BalanceHistoryChart(
            points = history,
            currency = balance?.balances?.firstOrNull()?.currency ?: "CNY"
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
private fun BalanceHistoryChart(points: List<BalanceHistoryPoint>, currency: String) {
    val values = points.filter { it.currency.equals(currency, ignoreCase = true) }.takeLast(30)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Palette.card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("余额变化趋势", color = Palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("根据每次成功查询记录余额变化", color = Palette.secondary, fontSize = 11.sp)
        if (values.size < 2) {
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text("至少成功查询两次后显示折线图", color = Palette.secondary, fontSize = 12.sp)
            }
        } else {
            val amounts = values.map { it.amount }
            val minValue = amounts.minOrNull() ?: 0.0
            val maxValue = amounts.maxOrNull() ?: 0.0
            val range = (maxValue - minValue).takeIf { it > 0.000001 } ?: 1.0
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                val left = 12.dp.toPx()
                val right = size.width - 12.dp.toPx()
                val top = 12.dp.toPx()
                val bottom = size.height - 14.dp.toPx()
                for (i in 0..3) {
                    val y = top + (bottom - top) * i / 3f
                    drawLine(Palette.divider, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                }
                val path = Path()
                values.forEachIndexed { index, point ->
                    val x = if (values.size == 1) left else left + (right - left) * index / (values.size - 1).toFloat()
                    val y = bottom - (((point.amount - minValue) / range).toFloat() * (bottom - top))
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Palette.blue, style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                values.forEachIndexed { index, point ->
                    val x = left + (right - left) * index / (values.size - 1).toFloat()
                    val y = bottom - (((point.amount - minValue) / range).toFloat() * (bottom - top))
                    drawCircle(Palette.blue, radius = 4.dp.toPx(), center = Offset(x, y))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("最低 " + formatMoney(minValue.toString(), currency), color = Palette.secondary, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("最高 " + formatMoney(maxValue.toString(), currency), color = Palette.secondary, fontSize = 10.sp)
            }
        }
        Text("说明：仅记录本版本安装后的余额快照，不代表官方 Token 消耗账单。", color = Palette.secondary, fontSize = 10.sp, lineHeight = 14.sp)
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
private fun SettingsScreen(
    onOpenAbout: () -> Unit,
    currentApiKey: String?,
    keyInput: String,
    onKeyInputChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onTestConnection: () -> Unit,
    onRemoveKey: () -> Unit,
    appearanceMode: String,
    onAppearanceChange: (String) -> Unit,
    refreshIntervalMinutes: Int,
    onRefreshIntervalChange: (Int) -> Unit,
    balanceReminderEnabled: Boolean,
    onBalanceReminderChange: (Boolean) -> Unit,
    balanceThresholdText: String,
    onBalanceThresholdChange: (String) -> Unit,
    statusBalanceNotificationEnabled: Boolean,
    onStatusBalanceNotificationChange: (Boolean) -> Unit,
    isTestingConnection: Boolean,
    connectionMessage: String?
) {
    var refreshMenuExpanded by remember { mutableStateOf(false) }
    val refreshOptions = listOf(0 to "手动刷新", 15 to "每 15 分钟", 30 to "每 30 分钟", 60 to "每 1 小时", 180 to "每 3 小时", 360 to "每 6 小时", 720 to "每 12 小时", 1440 to "每 24 小时")
    val selectedRefreshLabel = refreshOptions.firstOrNull { it.first == refreshIntervalMinutes }?.second ?: "手动刷新"
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeading("API Key 设置", "密钥保存在当前设备")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.card).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (currentApiKey.isNullOrBlank()) "尚未配置 API Key" else "当前密钥：" + maskKey(currentApiKey), color = Palette.secondary, fontSize = 12.sp)
            BasicTextField(
                value = keyInput,
                onValueChange = onKeyInputChange,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.softCard).padding(horizontal = 14.dp, vertical = 16.dp),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Palette.text, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Palette.blue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                decorationBox = { innerTextField ->
                    Box {
                        if (keyInput.isEmpty()) Text("粘贴你的 DeepSeek API Key", color = Palette.secondary, fontSize = 14.sp)
                        innerTextField()
                    }
                }
            )
            PrimaryAction("安全保存 API Key", onSaveKey)
            if (!currentApiKey.isNullOrBlank()) SecondaryAction("移除本机密钥", onRemoveKey)
            Text("密钥使用 Android Keystore 保护的 AES-GCM 加密后保存在本机。", color = Palette.secondary, fontSize = 11.sp, lineHeight = 16.sp)
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.card).clickable(onClick = onTestConnection).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⟳", color = Palette.blue, fontSize = 25.sp)
                Text("测试连接", color = Palette.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(if (isTestingConnection) "正在测试…" else "立即测试  ›", color = Palette.secondary, fontSize = 13.sp)
            }
            if (connectionMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(connectionMessage, color = if (connectionMessage.startsWith("连接成功")) Palette.greenText else Palette.secondary, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.card).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("刷新与提醒", fontSize = 17.sp, color = Palette.text, fontWeight = FontWeight.Bold)
            Box {
                Row(
                    Modifier.fillMaxWidth().clickable { refreshMenuExpanded = true }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("◷", color = Palette.blue, fontSize = 22.sp)
                    Text("自动刷新", color = Palette.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(selectedRefreshLabel + "  ›", color = Palette.secondary, fontSize = 13.sp)
                }
                DropdownMenu(expanded = refreshMenuExpanded, onDismissRequest = { refreshMenuExpanded = false }) {
                    refreshOptions.forEach { option ->
                        DropdownMenuItem(onClick = { onRefreshIntervalChange(option.first); refreshMenuExpanded = false }) {
                            Text(option.second, color = Palette.text)
                        }
                    }
                }
            }
            Text(if (refreshIntervalMinutes == 0) "前台与后台自动刷新均关闭，可在概览页手动刷新。" else "前台与后台按所选间隔刷新；Android 后台最短周期为 15 分钟。", color = Palette.secondary, fontSize = 11.sp)
            DividerLine()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("♧", color = Palette.blue, fontSize = 22.sp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("余额提醒", color = Palette.text, fontSize = 14.sp)
                    Text("余额低于设定阈值时推送通知，12 小时内最多一次", color = Palette.secondary, fontSize = 11.sp)
                }
                Switch(checked = balanceReminderEnabled, onCheckedChange = onBalanceReminderChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = Palette.blue))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("低于阈值", color = Palette.secondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                BasicTextField(
                    value = balanceThresholdText,
                    onValueChange = onBalanceThresholdChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(color = Palette.text, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End),
                    modifier = Modifier.width(110.dp).clip(RoundedCornerShape(12.dp)).background(Palette.softCard).padding(horizontal = 12.dp, vertical = 10.dp)
                )
                Text(" CNY", color = Palette.secondary, fontSize = 12.sp)
            }
            DividerLine()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("▤", color = Palette.blue, fontSize = 22.sp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("状态栏余额通知", color = Palette.text, fontSize = 14.sp)
                    Text("每次余额查询成功后显示账户余额", color = Palette.secondary, fontSize = 11.sp)
                }
                Switch(checked = statusBalanceNotificationEnabled, onCheckedChange = onStatusBalanceNotificationChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = Palette.blue))
            }
        }

        SectionHeading("通用", "外观主题设置")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.card).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("外观", color = Palette.text, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AppearanceChoice("浅色", appearanceMode == AppSettings.APPEARANCE_LIGHT, Modifier.weight(1f)) { onAppearanceChange(AppSettings.APPEARANCE_LIGHT) }
                AppearanceChoice("深色", appearanceMode == AppSettings.APPEARANCE_DARK, Modifier.weight(1f)) { onAppearanceChange(AppSettings.APPEARANCE_DARK) }
                AppearanceChoice("跟随系统", appearanceMode == AppSettings.APPEARANCE_SYSTEM, Modifier.weight(1f)) { onAppearanceChange(AppSettings.APPEARANCE_SYSTEM) }
            }
        }
        InfoCard("接口说明", "本应用展示 DeepSeek 官方余额接口返回的账户可用状态、总余额、赠送余额和充值余额。接口不提供 Token 消耗明细或账单历史。")

        SectionHeading("使用与支持", "应用信息与相关链接")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Palette.card)
                .clickable(onClick = onOpenAbout)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ⓘ", color = Palette.secondary, fontSize = 25.sp)
            Text("关于", color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 14.dp))
            Text("›", color = Palette.secondary, fontSize = 24.sp)
        }
    }
}

@Composable
private fun AboutScreen() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION")
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "1.0.0"
    }

    val repositoryUrl = "https://github.com/bin624/dsh"
    val communityUrl = "https://qun.qq.com/universal-share/share?ac=1&authKey=Qswq9%2BQxJ6iwlOc1XhADBhl%2FkpwPfOkYVIYnHVCX%2BKcaEQUnmFpEZlbEwRQF47rg&busi_data=eyJncm91cENvZGUiOiI5NzYxOTQwMjgiLCJ0b2tlbiI6IktJQW5QWTFYV3MvbE9hQk93VmthR0lXcUU5K0swQ2FaZ012VG1mRVRqMUxBZFJtdTVKd0duUlBWZ3BTYjc0YWciLCJ1aW4iOiIyMTIzMjg1MjkifQ%3D%3D&data=iFV0Zy5vlsKD4pnGtptD8UBl35E427AnD59dlr8FRfNOegQVHPvu9vzKkfmnuRENv2L1iSEeSOXFQscngy9CSQ&svctype=4&tempid=h5_group_info"

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .size(148.dp)
                .clip(CircleShape)
                .background(Palette.card),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = com.bin624.dsh.R.drawable.whale_disconnected),
                contentDescription = "DeepSeek 余额图标",
                modifier = Modifier.fillMaxSize().padding(17.dp),
                contentScale = ContentScale.Fit
            )
        }
        Text("DeepSeek 余额", color = Palette.text, fontSize = 31.sp, fontWeight = FontWeight.Medium)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Palette.card)
                .padding(horizontal = 18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 19.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ⓘ", color = Palette.secondary, fontSize = 24.sp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("版本", color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(versionName, color = Palette.secondary, fontSize = 13.sp)
                }
            }
            DividerLine()
            AboutActionRow(
                icon = "◎",
                title = "开源",
                detail = repositoryUrl,
                onClick = { runCatching { uriHandler.openUri(repositoryUrl) } }
            )
            DividerLine()
            AboutActionRow(
                icon = "♧",
                title = "社群",
                detail = "加入 QQ 群",
                onClick = { runCatching { uriHandler.openUri(communityUrl) } }
            )
        }
    }
}

@Composable
private fun AboutActionRow(
    icon: String,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 19.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, color = Palette.secondary, fontSize = 24.sp)
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
            Text(title, color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = Palette.secondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Text("↗", color = Palette.secondary, fontSize = 20.sp)
    }
}

@Composable
private fun SettingsToggleRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.card)
            .clickable { onCheckedChange(!checked) }.padding(horizontal = 17.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = Palette.secondary, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Palette.blue))
    }
}

@Composable
private fun AppearanceChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(13.dp))
        .background(if (selected) Palette.blue.copy(alpha = 0.13f) else Palette.softCard)
        .clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Palette.blue else Palette.secondary, fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun WhaleStatusBlob(connected: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val movie = remember(context, connected) {
        if (connected) {
            runCatching {
                context.resources.openRawResource(com.bin624.dsh.R.drawable.whale_connected).use {
                    Movie.decodeStream(it)
                }
            }.getOrNull()
        } else null
    }
    var frameTimeMillis by remember(movie) { mutableIntStateOf(0) }

    LaunchedEffect(movie) {
        val duration = movie?.duration()?.takeIf { it > 0 } ?: 1
        if (movie != null) {
            while (true) {
                withFrameNanos { frameTimeNanos ->
                    frameTimeMillis = ((frameTimeNanos / 1_000_000L) % duration).toInt()
                }
            }
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.minDimension * 0.445f
            val blob = Path()
            val steps = 144
            for (i in 0..steps) {
                val a = i.toDouble() / steps.toDouble() * Math.PI * 2.0
                val wave = 1f + 0.065f * kotlin.math.sin(a * 8.0).toFloat() +
                    0.018f * kotlin.math.sin(a * 13.0 + 0.6).toFloat()
                val r = radius * wave
                val x = cx + kotlin.math.cos(a).toFloat() * r
                val y = cy + kotlin.math.sin(a).toFloat() * r
                if (i == 0) blob.moveTo(x, y) else blob.lineTo(x, y)
            }
            blob.close()
            drawPath(
                blob,
                Brush.linearGradient(
                    listOf(Color(0xFF369BFF), Color(0xFF087FF5), Color(0xFF2689F4)),
                    start = Offset(size.width * 0.15f, 0f),
                    end = Offset(size.width * 0.85f, size.height)
                ),
                style = Fill
            )
            drawPath(blob, Color.White.copy(alpha = 0.34f), style = Stroke(1.5.dp.toPx()))
        }

        if (connected && movie != null) {
            Canvas(Modifier.fillMaxSize(0.80f)) {
                movie.setTime(frameTimeMillis)
                drawIntoCanvas { composeCanvas ->
                    val canvas = composeCanvas.nativeCanvas
                    val scale = minOf(
                        size.width / movie.width().toFloat(),
                        size.height / movie.height().toFloat()
                    )
                    val left = (size.width - movie.width() * scale) / 2f
                    val top = (size.height - movie.height() * scale) / 2f
                    val save = canvas.save()
                    canvas.translate(left, top)
                    canvas.scale(scale, scale)
                    movie.draw(canvas, 0f, 0f)
                    canvas.restoreToCount(save)
                }
            }
        } else {
            Image(
                painter = painterResource(id = com.bin624.dsh.R.drawable.whale_disconnected),
                contentDescription = "未连接的鲸鱼娘",
                modifier = Modifier.fillMaxSize(0.80f),
                contentScale = ContentScale.Fit
            )
        }

        if (!connected) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.82f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("待连接", color = Color(0xFF41658C), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }
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
