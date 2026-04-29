# Translate All in One

<div align="center">

[English](./README.md) | **简体中文**

</div>

> **预览提示**
>
> - 当前目标版本：**Minecraft 1.21.11**
> - 平台：**Fabric（客户端）**
> - Java：**21+**
> - 本项目仍在积极开发中，欢迎在 Issues 中提交反馈与错误报告。

一款 Minecraft 游戏内 AI 翻译模组，支持聊天输出、聊天输入、物品 Tooltip、计分板、WynnCraft NPC 对话翻译，以及独立的 WynnCraft 相关集成分类，提供多服务商路由、可编辑本地字典、AI 聊天输入助手面板、自动缓存备份和完整的游戏内配置流程。

---

## 简体中文

## 当前已实现功能（完整）

### 翻译模块

| 模块 | 功能 | 主要特点 |
| --- | --- | --- |
| 聊天输出翻译 | 翻译收到的聊天消息 | 支持自动翻译和手动 `[T]` 点击翻译，支持流式显示 |
| 聊天输入翻译 | 发送前翻译输入框内容 | 快捷键触发翻译 + AI 改写面板（翻译/专业/友好/扩写/简化/还原），可流式回填输入框 |
| 物品翻译 | 翻译物品名称与 Lore | 模板/样式保留，异步缓存队列，支持 Tooltip 缓存刷新 |
| 计分板翻译 | 翻译侧边栏显示文本 | 前后缀与玩家名按配置实时替换 |
| Wynn NPC 对话翻译 | 将 WynnCraft NPC 对话与可点击选项从聊天栏与 overlay 翻译到独立 HUD | 本地字典优先，缓存 + AI 回退，选项逐行渲染与动画，HUD 编辑器 |

### WynnCraft

| 功能 | 作用 | 主要特点 |
| --- | --- | --- |
| Wynn 物品兼容 | 处理 Wynn 系物品 Tooltip 的兼容链路 | 复用本模组现有的样式保留 Tooltip 翻译管线，并支持固定格式技能文本的 `wynncraft_skills.json` 本地字典 |
| 任务追踪支持 | 翻译 WynnCraft 相关 UI 集成里的任务标题/类型/描述，例如 Wynntils `ContentTracker` | 共享 WynnCraft 目标语言、支持缓存强制刷新、缓存 + AI 回退 |
| 字典配置 | 提供独立的游戏内字典分区、独立开关与文件选择器 | 支持总开关、`物品/技能` / `对话` / `任务` 三个独立开关，以及混合的复选/单选字典文件选择与目录直达入口 |

### 服务商与路由能力

- 同时支持多个服务商配置档案。
- 支持的 provider 类型：
  - `OPENAI_COMPAT`（`/chat/completions`）
  - `OPENAI_RESPONSE`（`/responses`）
  - `OLLAMA`（`/api/chat`）
- 可为每个功能模块独立设置路由模型：
  - 聊天输出
  - 聊天输入
  - 物品翻译
  - 计分板翻译
  - Wynn NPC 对话翻译
  - WynnCraft 任务追踪翻译
  - 聊天输出/输入、物品、计分板与 WynnCraft 可分别配置目标语言。

### 模型级参数（Model Settings）

- 模型 ID
- Temperature
- Ollama keep_alive（仅 Ollama）
- 是否支持 System 消息
- 当不支持 System 消息时，是否将提示词注入用户消息
- 结构化输出开关（带兼容回退）
- 提示词后缀
- 自定义参数（JSON 树编辑）
- 设为默认模型

### 运行时特性与稳定性

- 翻译流程尽量保留样式标记、占位符与关键 token。
- 物品、计分板、Wynn NPC 对话与 WynnCraft 任务追踪采用持久化模板缓存，并支持可配置的自动备份。
- 启动时会将内置 WynnCraft 字典复制到 `config/translate_allinone/dictionary/`，且不会覆盖已存在的用户编辑文件。
- 支持的 WynnCraft 文本链路会优先命中本地字典，其次查缓存，最后再走远端 AI 回退。
- `字典配置` 分区提供字典总开关和每个槽位的独立启用开关；当启用且已选择字典文件时，支持的链路按 `字典 -> 缓存 -> 远端 AI` 顺序处理。若某槽位未选择文件，则该槽位的字典查找会被跳过。
- `物品/技能` 字典选择器支持复选并会同时加载多个字典文件；`对话` 与 `任务` 字典选择器保持单选。。
- 物品 Tooltip 支持独立刷新缓存快捷键，可强制刷新当前缓存并立即重新排队翻译。
- WynnCraft 任务追踪支持独立刷新缓存快捷键，只会强制刷新当前追踪任务相关的缓存 key。
- 失败重试任务使用更高优先级（进入队列前部）。
- 物品/计分板支持批处理与并发参数配置，WynnCraft 任务追踪也具备独立运行时翻译链路。
- 会话 Epoch 防护：切换世界/会话后，旧异步回调不会回写过期翻译结果。
- missing key / key mismatch 会触发优先重试，并提供更明确的游戏内状态回退与反馈。
- 版本变更时会自动备份现有配置与缓存文件，降低升级过程中的风险。

### 命令与更新提醒

- 启动时自动检查当前仓库 GitHub 最新版本（tag）。
- 在聊天栏与配置界面内提供更新提示，并支持一键打开 Release 页面。
- 提供客户端命令：`translate_allinone translatechatline <messageId>`，用于手动重翻译聊天行（`[T]` 流程会使用该链路）。

### 配置界面特性

- 基于 ModMenu 的完整自定义配置界面。
- 分组框布局（Basic / Hotkey / Performance / Route / Providers）。
- 支持滚动、裁剪、滚动条拖动，长列表/小窗口可正常使用。
- 可在游戏内完成 provider/model 管理：新增/删除服务商、测试连接、设置路由模型、设为默认模型、自定义参数树编辑。
- 关闭配置界面时，若存在未保存修改，会弹出保存/丢弃/返回确认弹窗。
- 顶部动作统一：
  - **完成** = 保存并关闭
  - **取消** = 离开界面；若存在未保存修改，可选择保存、丢弃或返回继续编辑
  - **重置**（红色按钮）= 二次确认后恢复默认配置
- 模块快捷键支持在配置界面内直接捕获与清除，包含独立的 Tooltip 缓存刷新快捷键，以及 WynnCraft 任务追踪的缓存刷新快捷键。
- WynnCraft 分组已包含 NPC 对话开关，以及 HUD 位置/缩放的游戏内预览编辑器。
- 缓存备份分区支持配置备份策略，查看物品 / 计分板 / Wynn NPC 对话 / WynnCraft 任务追踪四类缓存统计，并一键打开缓存目录。
- 配置界面的更新提示弹窗支持直接打开最新版本发布页。

## 运行环境要求

- Minecraft `1.21.11`
- Fabric Loader `>= 0.18.1`
- Java `>= 21`
- Fabric API
- ModMenu `>= 16.0.0`

## 安装步骤

1. 安装适用于 Minecraft `1.21.11` 的 Fabric Loader。
2. 将以下文件放入 `mods` 文件夹：
   - `translate-all-in-one-*.jar`
   - Fabric API
   - ModMenu
3. 启动游戏并在 ModMenu 中打开本模组配置。
4. 配置服务商、模型与路由后保存。
5. 如果你安装了 Wynntils 等 WynnCraft 相关模组，可继续在 `WynnCraft` 中启用 NPC 对话翻译、任务追踪翻译与 HUD 相关配置。
6. 如需让支持的链路优先尝试本地字典，请在 `字典配置` 中保持字典总开关开启。
7. 首次启动后，如需扩展本地匹配，可编辑 `config/translate_allinone/dictionary/` 下的 JSON 字典文件。

## 快速配置建议

1. 在 `Providers` 中先添加至少一个服务商。
2. 为服务商添加模型，并为需要使用的模块设置路由模型。
3. 分别填写各模块的目标语言。
4. 如有需要，启用 Wynn NPC 对话翻译和/或任务追踪翻译，并调整对话 HUD 的位置与缩放。
5. 如果希望支持的链路先命中本地字典，请在 `字典配置` 中保持字典开关开启。
6. 配置需要的快捷键与模式；如有需要，也可以设置 Tooltip 与 WynnCraft 任务追踪的缓存刷新快捷键。
7. 点击 **完成** 保存。

## 配置与缓存文件

- 主配置：
  - `config/translate_allinone/translate_allinone.json`
  - 包含 `dictionary.enabled`、每个槽位的独立启用状态，以及 `item_skill_dictionary_files` / `wynncraft_dialogue_dictionary_file` / `wynncraft_quest_dictionary_file`
- 缓存文件：
  - `config/translate_allinone/item_translate_cache.json`
  - `config/translate_allinone/scoreboard_translate_cache.json`
  - `config/translate_allinone/wynncraft_dialogue_translate_cache.json`
  - `config/translate_allinone/wynncraft_quest_translate_cache.json`
- WynnCraft 本地字典目录：
  - `config/translate_allinone/dictionary/`
  - 通常会包含 `items.json`、`README.txt`
- 自动缓存备份目录（按时间戳目录快照）：
  - `config/translate_allinone/translate_cache_backup/`
- 版本变更安全备份目录：
  - `config/translate_allinone/translate_update_backup/`

## 从源码构建

```bash
./gradlew build
```

常用命令：

```bash
./gradlew check
./gradlew runClient
```

## 许可证

本项目采用 [MIT License](./LICENSE)。
