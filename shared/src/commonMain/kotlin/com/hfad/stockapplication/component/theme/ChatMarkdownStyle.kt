package com.hfad.stockapplication.component.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.TextLinkStyles
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownColors
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownTypography
import com.tencent.kuiklybase.markdown.model.MarkdownColors
import com.tencent.kuiklybase.markdown.model.MarkdownTypography

internal data class ChatColors(
    val pageBg: Color,
    val contentBg: Color,
    val placeholder: Color,
    val title: Color,
    val hairline: Color,
    val inputBg: Color,
    val accent: Color,
    val accentDisabled: Color,
    val accentSoft: Color,
    val track: Color,
    val iconLine: Color,
    val dim: Color,
    val rise: Color,
    val fall: Color,
    val surface: Color,
    val onAccent: Color,
) {
    companion object {
        val Light = ChatColors(
            pageBg = Color(0xFFFFFFFF),
            contentBg = Color(0xFFF7F7F8),
            placeholder = Color(0xFF8E8E93),
            title = Color(0xFF1A1A1A),
            hairline = Color(0xFFE5E5EA),
            inputBg = Color(0xFFF2F2F7),
            accent = Color(0xFF4D6BFE),
            accentDisabled = Color(0xFFB7C4FF),
            accentSoft = Color(0xFFEEF3FF),
            track = Color(0xFFF3F4F6),
            iconLine = Color(0xFF222222),
            dim = Color(0x80000000),
            rise = Color(0xFFE64545),
            fall = Color(0xFF12B76A),
            surface = Color(0xFFFFFFFF),
            onAccent = Color(0xFFFFFFFF),
        )
        val Dark = ChatColors(
            pageBg = Color(0xFF111113),
            contentBg = Color(0xFF1C1C1E),
            placeholder = Color(0xFF8E8E93),
            title = Color(0xFFF5F5F7),
            hairline = Color(0xFF3A3A3C),
            inputBg = Color(0xFF2C2C2E),
            accent = Color(0xFF5B78FF),
            accentDisabled = Color(0xFF3A4580),
            accentSoft = Color(0xFF1C2548),
            track = Color(0xFF2C2C2E),
            iconLine = Color(0xFFF5F5F7),
            dim = Color(0x99000000),
            rise = Color(0xFFFF6B6B),
            fall = Color(0xFF32D583),
            surface = Color(0xFF1C1C1E),
            onAccent = Color(0xFFFFFFFF),
        )
    }
}

internal val LocalChatColors = staticCompositionLocalOf { ChatColors.Light }

/**
 * 当前主题色。由 [ProvideChatColors] 写入，Canvas 绘制作用域也能读。
 * [white] 保留给强调色上的字；卡片背景用 [surface]。
 */
internal object ChatComposeTheme {
    var colors: ChatColors = ChatColors.Light
        internal set

    val pageBg: Color get() = colors.pageBg
    val contentBg: Color get() = colors.contentBg
    val placeholder: Color get() = colors.placeholder
    val title: Color get() = colors.title
    val hairline: Color get() = colors.hairline
    val inputBg: Color get() = colors.inputBg
    val accent: Color get() = colors.accent
    val accentDisabled: Color get() = colors.accentDisabled
    val accentSoft: Color get() = colors.accentSoft
    val track: Color get() = colors.track
    val iconLine: Color get() = colors.iconLine
    val dim: Color get() = colors.dim
    val rise: Color get() = colors.rise
    val fall: Color get() = colors.fall
    val surface: Color get() = colors.surface
    val onAccent: Color get() = colors.onAccent
    val white: Color get() = colors.onAccent
}

@Composable
internal fun ProvideChatColors(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) ChatColors.Dark else ChatColors.Light
    ChatComposeTheme.colors = palette
    CompositionLocalProvider(
        LocalChatColors provides palette,
        content = content,
    )
}

@Composable
internal fun chatMarkdownColors(
    text: Color = ChatComposeTheme.title,
): MarkdownColors = DefaultMarkdownColors(
    text = text,
    codeText = Color.Unspecified,
    inlineCodeText = Color.Unspecified,
    linkText = ChatComposeTheme.accent,
    codeBackground = ChatComposeTheme.inputBg,
    inlineCodeBackground = ChatComposeTheme.inputBg,
    dividerColor = ChatComposeTheme.hairline,
    tableText = Color.Unspecified,
    tableBackground = ChatComposeTheme.inputBg,
    tableHeaderBackground = ChatComposeTheme.contentBg,
    tableStroke = ChatComposeTheme.hairline,
)

@Composable
internal fun chatMarkdownTypography(
    textColor: Color = ChatComposeTheme.title,
): MarkdownTypography {
    val body = TextStyle(
        color = textColor,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
    val heading = TextStyle(
        color = textColor,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 24.sp,
    )
    val link = TextStyle(
        color = ChatComposeTheme.accent,
        fontSize = 15.sp,
    )
    return DefaultMarkdownTypography(
        h1 = heading,
        h2 = heading,
        h3 = heading,
        h4 = heading,
        h5 = heading,
        h6 = heading,
        text = body,
        quote = body.copy(color = ChatComposeTheme.placeholder),
        code = body.copy(fontSize = 13.sp),
        inlineCode = body.copy(fontSize = 13.sp),
        paragraph = body,
        ordered = body,
        bullet = body,
        list = body,
        link = link,
        textLink = TextLinkStyles(style = link.toSpanStyle()),
        table = body,
    )
}
