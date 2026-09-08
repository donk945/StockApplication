# 分层约定（Kuikly shared）

单模块 `:shared` 是 Kuikly 的正确形态，不要为「看起来像 Android 多模块」再拆 Gradle。
按包分层，新代码放进对应层。

```
infra/          宿主桥、Page 基类、SSE
data/chat/      模型、解析、仓库（无 Compose）
state/chat/     ChatStore：页面状态与业务流程
page/chat/      @Page 与本页 Compose（可拆文件，不进 component）
component/      可复用 ComposeView；chart 仍属本产品行情 UI
```

`page/chat` 拆文件约定：

| 文件 | 职责 |
|------|------|
| `StockChatPage` | `@Page`、生命周期、DI、`ChatScreen` 编排 |
| `ChatUiDefaults` | 入参 Key、占位文案、空态建议 |
| `ChatChrome` | 顶栏、空态、建议芯片、图标 |
| `ChatComposerBar` | 底部输入条 |
| `ChatTranscript` | 会话行、气泡、Markdown、行情卡 |
| `ChatDrawerUi` | 历史抽屉 |
| `ChatProfileUi` | 账号 / 设置 |

不要把 `ChatStore` 拆进多个 Store：问答页的 `mutableState` 仍集中在一处。

## component

只放 **无 @Page、无 toast/Repository** 的组合组件。

| 包 | 内容 |
|----|------|
| `component.theme` | 问答配色、Markdown 样式 |
| `component.chart` | 行情卡、K 线抽屉、对比条 |
| `component.drawer` | `DrawerItem` 等与会话模型解耦的行数据 |

某一页一次性布局写在 `page/`，不要塞进这里。抽独立 Kit 的前提：第二个 feature 真的要复用，且 API 已稳定。
