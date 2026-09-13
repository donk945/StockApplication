package com.hfad.stockapplication.component.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hfad.stockapplication.data.chat.RelatedStockParser
import com.hfad.stockapplication.debug.AgentDebugLog
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.unit.TextUnit
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuiklybase.markdown.compose.Markdown
import com.tencent.kuiklybase.markdown.model.rememberMarkdownState
import kotlinx.coroutines.CancellationException

/**
 * 流式 / 完成后的 Markdown 正文。未解析完时先用纯文本占位，避免半截标记闪星号。
 */
@Composable
internal fun ChatMarkdownBody(
    text: String,
    streaming: Boolean,
    modifier: Modifier = Modifier,
    fallbackFontSize: TextUnit = 15.sp,
) {
    val markdownState = rememberMarkdownState()
    var parsed by remember { mutableStateOf(false) }
    var lastParsedLen by remember { mutableStateOf(0) }
    val plain = RelatedStockParser.streamPlainText(text)
    LaunchedEffect(text, streaming) {
        val parsedBefore = parsed
        val prevLen = lastParsedLen
        val shrink = text.length < prevLen
        if (shrink) {
            lastParsedLen = 0
            parsed = false
            // #region agent log
            AgentDebugLog.emit(
                "A",
                "ChatMarkdownBody",
                "shrink-reset",
                mapOf(
                    "len" to text.length.toString(),
                    "prevLen" to prevLen.toString(),
                    "streaming" to streaming.toString(),
                    "head" to text.take(24).replace("\n", "|"),
                ),
                runId = "md-flicker",
            )
            // #endregion
        }
        val grew = text.length - lastParsedLen
        val ready = RelatedStockParser.streamParseReady(
            length = text.length,
            lastParsedLen = lastParsedLen,
            streaming = streaming,
            endsWithBreak = text.endsWith("\n"),
            hasBreak = text.contains('\n'),
        )
        if (streaming && !ready) {
            // #region agent log
            if (!parsed) {
                AgentDebugLog.emit(
                    "D",
                    "ChatMarkdownBody",
                    "plain-unready",
                    mapOf(
                        "len" to text.length.toString(),
                        "prevLen" to prevLen.toString(),
                        "parsedBefore" to parsedBefore.toString(),
                        "grew" to grew.toString(),
                    ),
                    runId = "md-flicker",
                )
            }
            // #endregion
            return@LaunchedEffect
        }
        val started = DateTime.currentTimestamp()
        try {
            markdownState.parse(RelatedStockParser.streamMarkdown(text), false)
            lastParsedLen = text.length
            parsed = true
            // #region agent log
            AgentDebugLog.emit(
                "C",
                "ChatMarkdownBody",
                if (streaming) "parse-stream" else "parse",
                mapOf(
                    "len" to text.length.toString(),
                    "grew" to grew.toString(),
                    "ms" to (DateTime.currentTimestamp() - started).toString(),
                    "hash" to text.contains("#").toString(),
                    "parsedBefore" to parsedBefore.toString(),
                    "prevLen" to prevLen.toString(),
                    "streaming" to streaming.toString(),
                ),
                runId = "md-flicker",
            )
            // #endregion
        } catch (error: CancellationException) {
            // #region agent log
            AgentDebugLog.emit(
                "H",
                "ChatMarkdownBody",
                "parse-cancel",
                mapOf(
                    "len" to text.length.toString(),
                    "keepParsed" to parsed.toString(),
                    "streaming" to streaming.toString(),
                ),
                runId = "post-fix",
            )
            // #endregion
            throw error
        } catch (error: Throwable) {
            if (!parsed) {
                parsed = false
            }
            // #region agent log
            AgentDebugLog.emit(
                "H",
                "ChatMarkdownBody",
                "parse-throw",
                mapOf(
                    "type" to error::class.simpleName.orEmpty(),
                    "msg" to (error.message ?: ""),
                    "len" to text.length.toString(),
                    "keepParsed" to parsed.toString(),
                ),
                runId = "post-fix",
            )
            // #endregion
        }
    }
    if (!parsed) {
        Text(
            text = plain.ifBlank { "正在分析…" },
            fontSize = fallbackFontSize,
            color = ChatComposeTheme.title,
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        Markdown(
            state = markdownState,
            colors = chatMarkdownColors(),
            typography = chatMarkdownTypography(),
            modifier = modifier.fillMaxWidth(),
        )
    }
}
