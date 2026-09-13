package com.hfad.stockapplication.data.chat

import com.hfad.stockapplication.debug.AgentDebugLog
import com.hfad.stockapplication.infra.SseModule
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * DeepSeek Chat Completions：多轮上下文 + SSE 流式 Markdown（失败回退一次 HTTP）。
 *
 * 界面先停在「思考中」，直到正文开始流出再刷新气泡。
 */
class DeepSeekChatRepository(
    private val network: NetworkModule,
    private val sse: SseModule?,
    private var apiKey: String,
) : StockAiRepository {

    private var cancelled = false

    /**
     * 向模型提问。
     *
     * @param onDelta 每次给出「到目前为止的完整 Markdown」；思考阶段没有正文时不回调
     * @param onResult 结束回调；成功为 [StockAskResult]，失败为异常信息
     */
    override fun askStockContext(
        question: String,
        history: List<ChatMessage>,
        onDelta: (String) -> Unit,
        onResult: (Result<StockAskResult>) -> Unit,
        marketContext: String,
        think: Boolean,
    ) {
        if (apiKey.isBlank()) {
            onResult(Result.failure(IllegalStateException("未配置 API Key")))
            return
        }
        cancelled = false
        val headers = JSONObject()
            .put("Authorization", "Bearer $apiKey")
            .put("Content-Type", "application/json")
        if (sse != null) {
            startStream(
                buildBody(question, history, marketContext, stream = true, think = think),
                headers,
                onDelta,
                onResult,
            )
        } else {
            requestOnce(
                buildBody(question, history, marketContext, stream = false, think = think),
                headers,
                onResult,
            )
        }
    }

    /** 用户新开局或切历史时打断进行中的请求。 */
    override fun cancel() {
        cancelled = true
        sse?.cancel()
    }

    override fun updateApiKey(apiKey: String) {
        this.apiKey = apiKey.trim()
    }

    /**
     * 走原生 [SseModule]：按 event 拼字。
     * delta 累加后回调全文；done 收尾；error 时若已有字当成功，否则改打一次普通 HTTP。
     */
    private fun startStream(
        body: JSONObject,
        headers: JSONObject,
        onDelta: (String) -> Unit,
        onResult: (Result<StockAskResult>) -> Unit,
    ) {
        var accumulated = ""
        var finished = false
        sse?.start(URL, headers, body) { data ->
            if (finished || data == null) {
                return@start
            }
            when (data.optString("event")) {
                SseModule.EVENT_DELTA -> {
                    val piece = data.optString("text").orEmpty()
                    accumulated += piece
                    if (accumulated.isNotBlank()) {
                        onDelta(accumulated)
                    }
                }
                SseModule.EVENT_DONE -> {
                    finished = true
                    complete(accumulated, onResult)
                }
                SseModule.EVENT_ERROR -> {
                    finished = true
                    if (cancelled) {
                        if (accumulated.isNotBlank()) {
                            complete(accumulated, onResult)
                        }
                        return@start
                    }
                    if (accumulated.isNotBlank()) {
                        complete(accumulated, onResult)
                    } else {
                        requestOnce(buildBodyWithoutStream(body), headers, onResult)
                    }
                }
            }
        }
    }

    /** 一次 POST 拿完整 `choices[0].message.content`。 */
    private fun requestOnce(
        body: JSONObject,
        headers: JSONObject,
        onResult: (Result<StockAskResult>) -> Unit,
    ) {
        network.httpRequest(
            url = URL,
            isPost = true,
            param = body,
            headers = headers,
            cookie = null,
            timeout = 90,
        ) { data, success, errorMsg, response ->
            if (cancelled) {
                return@httpRequest
            }
            if (!success) {
                val code = response.statusCode?.toString() ?: "?"
                val msg = errorMsg ?: ""
                onResult(Result.failure(IllegalStateException(msg.ifBlank { "请求失败($code)" })))
                return@httpRequest
            }
            complete(extractContent(data), onResult)
        }
    }

    /** 从全文里抽出 `# 标题`，交给 Store 写顶栏和气泡。 */
    private fun complete(content: String, onResult: (Result<StockAskResult>) -> Unit) {
        val parsed = StockAskResult.fromMarkdown(content)
        if (parsed == null) {
            onResult(Result.failure(IllegalStateException("解析模型回包失败")))
        } else {
            onResult(Result.success(parsed))
        }
    }

    /** 把已拼好的流式 body 改成 `stream=false`，供失败回退复用同一组 messages。 */
    private fun buildBodyWithoutStream(streamBody: JSONObject): JSONObject {
        val copy = JSONObject(streamBody.toString())
        copy.put("stream", false)
        return copy
    }

    /**
     * 组装 DeepSeek `chat/completions` 请求体：
     * system 提示 + 历史（user/assistant）+ 本轮 user；需要流式时再加 `stream: true`。
     */
    private fun buildBody(
        question: String,
        history: List<ChatMessage>,
        marketContext: String,
        stream: Boolean,
        think: Boolean,
    ): JSONObject {
        val messages = JSONArray()
        messages.put(
            JSONObject()
                .put("role", "system")
                .put("content", SYSTEM_PROMPT)
        )
        history.forEach { item ->
            messages.put(
                JSONObject()
                    .put("role", if (item.fromUser) "user" else "assistant")
                    .put("content", item.body)
            )
        }
        val userContent = if (marketContext.isBlank()) {
            question
        } else {
            "$question\n\n$marketContext"
        }
        // #region agent log
        AgentDebugLog.emit(
            "B",
            "DeepSeekChatRepository.buildBody",
            "user-content",
            mapOf(
                "qLen" to question.length.toString(),
                "ctxLen" to marketContext.length.toString(),
                "userLen" to userContent.length.toString(),
                "hasDaily" to userContent.contains("【日K选点】").toString(),
                "hasMinute" to userContent.contains("【分时选点】").toString(),
                "hasLive" to userContent.contains("【行情快照】").toString(),
                "ctxHead" to marketContext.take(90).replace("\n", "|"),
            ),
            runId = "post-fix",
        )
        // #endregion
        messages.put(
            JSONObject()
                .put("role", "user")
                .put("content", userContent)
        )
        return JSONObject()
            .put("model", if (think) MODEL_REASONER else MODEL)
            .put("messages", messages)
            .put("stream", stream)
    }

    /** 非流式回包：优先 `choices[0].message.content`，否则退回顶层 `content`。 */
    private fun extractContent(data: JSONObject): String {
        val choices = data.optJSONArray("choices")
        return if (choices != null && choices.length() > 0) {
            choices.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
        } else {
            data.optString("content").orEmpty()
        }
    }

    companion object {
        const val URL = "https://api.deepseek.com/chat/completions"
        const val MODEL = "deepseek-chat"
        const val MODEL_REASONER = "deepseek-reasoner"
        /** 约束模型：先回答问题；仅在问到具体 A 股或常用指数时才给可解析的目标与关联列表。 */
        const val SYSTEM_PROMPT = """你是A股助手。只用 Markdown，不要输出 JSON，不要用代码块包住全文。
第一行必须是：# 后面跟一句短会话标题。
用 6 到 10 个汉字点出本轮主题，像历史列表里的条目，例如「宁德时代走势」「白酒板块怎么看」「上证怎么看」。
能更短就更短，不要凑满字数，不要句子，不要句号，不要解释，不要写成分析。写完标题立刻换行再写正文。

第二段起必须直接回答用户的问题（原因、结论、注意点等），不要只丢一个股票名。正文可用普通 Markdown（段落、列表、### 小标题）。

【目标股票 / 目标指数是可选项，不是每轮必出】
仅当用户本轮明确问到可对应的沪深A股正股，或问到常用指数（上证、深成指、创业板指、沪深300、上证50、科创50）时，才在正文之后追加下方结构，供系统解析；不要在正文里重复展开名单。
宏观、指标、教学、策略、没有点名公司或指数等一般问题：不要写「## 目标股票」或「## 目标指数」，不要写关联三段，不要用「暂无」占位，不要猜测一只股票凑数。标题和回答仍要给出。
用户点名两只及以上（对比、都看看、这几只）时：必须写「## 目标股票」，每行一只，只写点名的正股，最多4只，不要用一只凑数。

【大盘 / 指数】
用户问上证、沪指、大盘、深成指、创业板指等：写「## 目标指数」，不要猜一只龙头正股代替。
示例：
## 目标指数
上证指数（000001）
可用 sh000001、sz399001、sz399006 这种带市场前缀的写法。本产品只覆盖上述常用指数，不要编造其他指数代码。指数详情不要写行业/产业链/同业三段。

【概念 / 板块，但没有点名公司】
不要写「## 目标股票」，不要硬猜龙头。在正文之后写「## 相关标的」，列出 2 到 3 只可点的相关正股（必须带 6 位代码），不要解释。
## 相关标的
- 宁德时代（300750）
- 比亚迪（002594）

需要追加正股目标时用：
## 目标股票
宁德时代（300750）
两只及以上时每行一只，前面可加「-」：
- 宁德时代（300750）
- 比亚迪（002594）
（名称后必须带 6 位 A 股代码，全角或半角括号均可）
## 行业板块
- 股票名（6位代码）
## 产业链与供应链
- 股票名（6位代码）
## 同行业与同概念
- 股票名（6位代码）
若用户名称不是准确A股简称，先纠正为最接近的上市股票（例如「宇德时代」应理解为「宁德时代」），按纠正后的公司填写。
关联三段每组最多8个，每条必须是「- 名称（6位代码）」，不要解释。只在问到具体正股时才写这三段；指数和纯概念问不要写。
正股只列沪深A股上市公司（代码以6/0/3开头的6位数字）；不要 ETF、基金、板块名、未上市公司或编造的代码。不确定该股是否在市，就不要写进去。
多轮对话时：只有本轮仍在讨论那只已确定的目标股或指数，才继续带上对应段落；换了话题就不要再带。
若本轮用户消息末尾附有【日K选点】或【分时选点】，价格、涨跌、成交、时间只以该选点为准，不要混用最新现价或其他交易日。
若只有【行情快照】没有选点，现价、涨跌、成交额、时间以该快照为准。
没有上述快照时不要编造精确现价。

【投资场景】
用户问买卖、走势、风险、信号时：第一段给一句话结论，随后分「驱动因素」「主要风险」两小节。必须写明「不构成投资建议」，不要给仓位、点位保证或必涨承诺。"""
    }
}
