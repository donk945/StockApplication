package com.hfad.stockapplication.data.chat

/**
 * 目标股票 / 指数卡片下方的常见追问。芯片展示 [label]，发送时带上名称与代码。
 */
data class FollowUpPrompt(
    val label: String,
    val question: String,
)

object FollowUpPrompts {

    fun forTargets(stocks: List<TargetStock>): List<FollowUpPrompt> {
        if (stocks.isEmpty()) {
            return emptyList()
        }
        if (stocks.all { it.isIndex }) {
            return if (stocks.size == 1) {
                val name = named(stocks.first())
                listOf(
                    FollowUpPrompt(
                        label = "今天怎么走",
                        question = "结合最新行情，${name}今天怎么走？先给结论，再说明驱动和风险。",
                    ),
                    FollowUpPrompt(
                        label = "量能怎么看",
                        question = "${name}近期量能怎么看？不要编造精确成交数字。",
                    ),
                    FollowUpPrompt(
                        label = "和深成指比",
                        question = "把${name}和深证成指（399001）近期表现对比一下，结论先说。",
                    ),
                )
            } else {
                val list = stocks.joinToString("、", transform = ::named)
                listOf(
                    FollowUpPrompt(
                        label = "谁更强",
                        question = "对比${list}近期走势，谁相对更强？结论先说。",
                    ),
                    FollowUpPrompt(
                        label = "怎么分化",
                        question = "说明${list}当前分化的主要原因，不要编造。",
                    ),
                )
            }
        }
        return if (stocks.size == 1) {
            val name = named(stocks.first())
            listOf(
                FollowUpPrompt(
                    label = "现在能买吗",
                    question = "结合最新行情，${name}现在适合买入吗？先给结论，再说明理由和主要风险。",
                ),
                FollowUpPrompt(
                    label = "有什么风险",
                    question = "${name}当前最主要的风险有哪些？按重要性简要列出，不要编造。",
                ),
                FollowUpPrompt(
                    label = "同业怎么比",
                    question = "把${name}和同行业龙头比估值、增长和风险，结论先说。",
                ),
                FollowUpPrompt(
                    label = "近期催化剂",
                    question = "${name}近一季有哪些催化或利空？只写能对应到公开信息的事项。",
                ),
            )
        } else {
            val list = stocks.joinToString("、", transform = ::named)
            listOf(
                FollowUpPrompt(
                    label = "谁更值得关注",
                    question = "对比${list}，谁更值得关注？给出排序和理由。",
                ),
                FollowUpPrompt(
                    label = "核心差异",
                    question = "对比${list}的业务和估值差异，结论先说。",
                ),
                FollowUpPrompt(
                    label = "各自风险",
                    question = "分别说明${list}当前最主要的风险，不要编造。",
                ),
            )
        }
    }

    fun forRelatedPicks(picks: List<TargetStock>): List<FollowUpPrompt> {
        if (picks.isEmpty()) {
            return emptyList()
        }
        val list = picks.joinToString("、", transform = ::named)
        return listOf(
            FollowUpPrompt(
                label = "这几只谁更强",
                question = "对比${list}，谁更值得关注？结论先说，不要编造没点名的公司。",
            ),
            FollowUpPrompt(
                label = "各自风险",
                question = "分别说明${list}当前最主要的风险，不要编造。",
            ),
        )
    }

    private fun named(stock: TargetStock): String {
        return "${stock.name}（${stock.code}）"
    }
}
