package com.hfad.stockapplication.data.chat

/**
 * 会话标题：顶栏和历史列表共用。
 * 去空白标点后取适中字数，像一条精炼主题，不做成整句。
 */
object SessionTitles {
    const val FALLBACK = "股票问答"
    const val MIN_LEN = 2
    /** 顶栏正中能稳住的长度；模型也按这个量级写。 */
    const val MAX_LEN = 10

    fun orDefault(raw: String, fallback: String = FALLBACK): String {
        return display(raw).ifBlank { fallback }
    }

    /** 展示用：去空白与标点，再收到 [MAX_LEN] 字。 */
    fun display(raw: String): String {
        return compact(raw).take(MAX_LEN)
    }

    /** 首问尚未出模型标题时，用问题压缩占位。 */
    fun fromQuestion(question: String, fallback: String = FALLBACK): String {
        val title = display(question)
        return if (title.length >= MIN_LEN) title else fallback
    }

    /**
     * 只认行首一级标题 `# …`，不认 `##`。
     * [requireCompleteLine] 为 true 时，标题行后面必须已有换行，避免 `# 白酒` 这种半截。
     */
    fun fromHeading(markdown: String, requireCompleteLine: Boolean = false): String? {
        val parsed = firstH1(markdown) ?: return null
        if (requireCompleteLine && !parsed.complete) {
            return null
        }
        val title = display(parsed.text)
        return title.takeIf { it.length >= MIN_LEN }
    }

    private data class HeadingLine(val text: String, val complete: Boolean)

    private fun firstH1(markdown: String): HeadingLine? {
        val lines = markdown.lines()
        val index = lines.indexOfFirst { line ->
            val trimmed = line.trim()
            trimmed.startsWith("#") && !trimmed.startsWith("##")
        }
        if (index < 0) {
            return null
        }
        val text = lines[index].trim().trimStart('#').trim()
        val complete = index < lines.lastIndex || markdown.endsWith("\n") || markdown.endsWith("\r")
        return HeadingLine(text = text, complete = complete)
    }

    private fun compact(raw: String): String {
        return buildString {
            raw.forEach { ch ->
                if (ch.isWhitespace() || ch in PUNCTUATION) {
                    return@forEach
                }
                append(ch)
            }
        }
    }

    private const val PUNCTUATION =
        "，。！？、；：…—·「」『』“”‘’（）()[]【】《》<>#*_`~,.!?;:\"'"
}
