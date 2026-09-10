package com.hfad.stockapplication.page.quotes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.component.theme.ProvideChatColors
import com.hfad.stockapplication.data.chat.StockQuote
import com.hfad.stockapplication.data.chat.TencentMarketRepository
import com.hfad.stockapplication.data.chat.UserSettingsRepository
import com.hfad.stockapplication.infra.AppPages
import com.hfad.stockapplication.infra.BaseComposePager
import com.hfad.stockapplication.infra.bridgeModule
import com.hfad.stockapplication.infra.closeAppPage
import com.hfad.stockapplication.infra.openAppPage
import com.hfad.stockapplication.infra.systemBottomInset
import com.hfad.stockapplication.state.detail.DetailStore
import com.hfad.stockapplication.state.quotes.QuotesStore
import com.hfad.stockapplication.state.quotes.WatchQuoteRow
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.material3.TextField
import com.tencent.kuikly.compose.material3.TextFieldDefaults
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import kotlinx.coroutines.delay

/**
 * 任务 1：实时行情页。指数 + 热门股列表，点行进入 [AppPages.DETAIL]。
 */
@Page("stock_quotes", supportInLocal = true)
internal class StockQuotesPage : BaseComposePager() {

    private lateinit var store: QuotesStore

    override fun willInit() {
        super.willInit()
        setContent { QuotesScreen() }
    }

    override fun created() {
        super.created()
        val network = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
        val sp = acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
        val theme = UserSettingsRepository(sp).loadThemeMode()
        store = QuotesStore(TencentMarketRepository(network))
        store.loadInitial(dark = theme.isDark(isNightMode()))
    }

    @Composable
    private fun QuotesScreen() {
        val bottomInset = systemBottomInset()
        ProvideChatColors(dark = store.darkTheme) {
            LaunchedEffect(store.darkTheme) {
                bridgeModule.setNightBars(store.darkTheme)
            }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(5_000)
                    store.refresh()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ChatComposeTheme.pageBg),
            ) {
                Spacer(modifier = Modifier.height(pagerData.statusBarHeight.dp))
                QuotesTitleBar(
                    onBack = { closeAppPage() },
                )
                QuotesSearchBar(
                    query = store.query,
                    hint = store.hint,
                    onQuery = { store.query = it },
                    onSubmit = {
                        if (!store.submitQuery()) {
                            bridgeModule.toast(store.hint.ifBlank { "请输入 6 位代码" })
                        }
                    },
                )
                Text(
                    text = "腾讯网页行情快照，约 5 秒刷新。点一行进入分时 / 日 K 详情。",
                    fontSize = 12.sp,
                    color = ChatComposeTheme.placeholder,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(store.rows.toList(), key = { it.item.symbol }) { row ->
                        QuoteRow(
                            row = row,
                            onClick = {
                                openAppPage(
                                    AppPages.DETAIL,
                                    DetailStore.fromWatchItem(
                                        name = row.item.name,
                                        code = row.item.code,
                                        market = row.item.market,
                                        isIndex = row.item.isIndex,
                                    ),
                                )
                            },
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height((bottomInset + 12f).dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotesTitleBar(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(ChatComposeTheme.pageBg),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable(onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("返回", fontSize = 16.sp, color = ChatComposeTheme.accent)
        }
        Text(
            text = "实时行情",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = ChatComposeTheme.title,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun QuotesSearchBar(
    query: String,
    hint: String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text("输入 6 位代码", color = ChatComposeTheme.placeholder, fontSize = 14.sp)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ChatComposeTheme.inputBg,
                unfocusedContainerColor = ChatComposeTheme.inputBg,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(ChatComposeTheme.accent)
                .clickable(onClick = onSubmit)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text("查询", fontSize = 14.sp, color = ChatComposeTheme.onAccent)
        }
    }
    if (hint.isNotBlank()) {
        Text(
            text = hint,
            fontSize = 12.sp,
            color = ChatComposeTheme.fall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun QuoteRow(
    row: WatchQuoteRow,
    onClick: () -> Unit,
) {
    val quote = row.quote
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.item.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChatComposeTheme.title,
            )
            Text(
                text = "${row.item.code}  ${if (row.item.isIndex) "指数" else "A股"}",
                fontSize = 12.sp,
                color = ChatComposeTheme.placeholder,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = quote?.price?.ifBlank { "--" } ?: "--",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = changeColor(quote),
            )
            Text(
                text = listOfNotNull(
                    quote?.change?.takeIf { it.isNotBlank() },
                    quote?.changePercent?.takeIf { it.isNotBlank() },
                ).joinToString("  ").ifBlank { "等待行情" },
                fontSize = 12.sp,
                color = changeColor(quote),
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(ChatComposeTheme.hairline),
    )
}

private fun changeColor(quote: StockQuote?): Color {
    val percent = quote?.changePercent.orEmpty()
    return when {
        percent.startsWith("+") -> ChatComposeTheme.rise
        percent.startsWith("-") -> ChatComposeTheme.fall
        else -> ChatComposeTheme.title
    }
}
