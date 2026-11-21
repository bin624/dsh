package com.bin624.dsh.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

internal object Palette {
    var isDarkTheme by mutableStateOf(false)
    val background: Color get() = if (isDarkTheme) Color(0xFF111722) else Color(0xFFF1EFF3)
    val card: Color get() = if (isDarkTheme) Color(0xFF202A39) else Color(0xFFFFFCFD)
    val softCard: Color get() = if (isDarkTheme) Color(0xFF293446) else Color(0xFFF0EEF2)
    val text: Color get() = if (isDarkTheme) Color(0xFFF0F4FA) else Color(0xFF171923)
    val secondary: Color get() = if (isDarkTheme) Color(0xFFADB8C8) else Color(0xFF686B78)
    val blue: Color get() = if (isDarkTheme) Color(0xFF4BA6FF) else Color(0xFF087FF5)
    val paleBlue: Color get() = if (isDarkTheme) Color(0xFF253A58) else Color(0xFFE6EDFC)
    val greenBackground: Color get() = if (isDarkTheme) Color(0xFF1E3B30) else Color(0xFFDCEFD5)
    val greenText: Color get() = if (isDarkTheme) Color(0xFF8BD6A4) else Color(0xFF1F6535)
    val divider: Color get() = if (isDarkTheme) Color(0xFF354154) else Color(0xFFE7E3E9)
    val dangerBackground: Color get() = if (isDarkTheme) Color(0xFF492C32) else Color(0xFFFFE8E6)
    val dangerText: Color get() = if (isDarkTheme) Color(0xFFFFA5A2) else Color(0xFFAB3E3A)
}
