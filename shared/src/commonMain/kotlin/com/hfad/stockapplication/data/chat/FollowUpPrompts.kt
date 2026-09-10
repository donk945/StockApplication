package com.hfad.stockapplication.data.chat

/**
 * 目标股票 / 指数卡片下方的常见追问。
 * 芯片展示 [label]；[question] 是用户气泡里的口语，不带给模型的格式指令。
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
                val name = spoken(stocks.first())
                listOf(
                    FollowUpPrompt(
                        label = "今天怎么走",
                        question = "${name}今天怎么走？",
                    ),
                    FollowUpPrompt(
                        label = "量能怎么看",
                        question = "${name}量能怎么样？",
                    ),
                    FollowUpPrompt(
                        label = "和深成指比",
                        question = "${name}跟深成指比怎么样？",
                    ),
                )
            } else {
                val list = spokenList(stocks)
                listOf(
                    FollowUpPrompt(
                        label = "谁更强",
                        question = "${list}这几个谁更强？",
                    ),
                    FollowUpPrompt(
                        label = "怎么分化",
                        question = "${list}现在怎么分化的？",
                    ),
                )
            }
        }
        return if (stocks.size == 1) {
            val name = spoken(stocks.first())
            listOf(
                FollowUpPrompt(
                    label = "现在能买吗",
                    question = "${name}现在能买吗？",
                ),
                FollowUpPrompt(
                    label = "有什么风险",
                    question = "${name}有什么风险？",
                ),
                FollowUpPrompt(
                    label = "同业怎么比",
                    question = "${name}跟同行业比怎么样？",
                ),
                FollowUpPrompt(
                    label = "近期催化剂",
                    question = "${name}最近有什么催化？",
                ),
            )
        } else {
            val list = spokenList(stocks)
            listOf(
                FollowUpPrompt(
                    label = "谁更值得关注",
                    question = "${list}这几个谁更值得关注？",
                ),
                FollowUpPrompt(
                    label = "核心差异",
                    question = "${list}主要差在哪？",
                ),
                FollowUpPrompt(
                    label = "各自风险",
                    question = "${list}各自有什么风险？",
                ),
            )
        }
    }

    fun forRelatedPicks(picks: List<TargetStock>): List<FollowUpPrompt> {
        if (picks.isEmpty()) {
            return emptyList()
        }
        val list = spokenList(picks)
        return listOf(
            FollowUpPrompt(
                label = "这几只谁更强",
                question = "${list}这几只谁更强？",
            ),
            FollowUpPrompt(
                label = "各自风险",
                question = "${list}各自有什么风险？",
            ),
        )
    }

    private fun spoken(stock: TargetStock): String {
        return stock.name.trim().ifBlank { stock.code }
    }

    private fun spokenList(stocks: List<TargetStock>): String {
        return stocks.joinToString("、", transform = ::spoken)
    }
}
