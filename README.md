# Translate All in One

> **Preview Notice**
>
> - Current target version: **Minecraft 1.21.10**
> - Platform: **Fabric (Client-side)**
> - Java: **21+**
> - This project is actively evolving. Feedback and bug reports are welcome in Issues.

An in-game AI translation mod for Minecraft that supports chat, chat input, item tooltip, scoreboard, and a dedicated WynnCraft category for related integrations with multi-provider routing, an AI chat-input assistant panel, automatic cache backup, and a fully in-game configuration workflow.

---

## English

## What It Can Do Right Now

### Translation modules

| Module | What it does | Highlights |
| --- | --- | --- |
| Chat Output | Translates incoming chat lines | Auto mode or manual `[T]` click mode, optional streaming display |
| Chat Input | Translates your text before send | Hotkey-driven translation plus AI rewrite actions (Translate/Professional/Friendly/Expand/Concise/Restore), optional streaming update |
| Item Tooltip | Translates item custom name and lore | Template/style-preserving pipeline, async cache queue, refresh-cache |
| Scoreboard Sidebar | Translates prefix/suffix and player name display | Real-time replacement with style-preserving reconstruction |

### WynnCraft

| Feature | What it does | Highlights |
| --- | --- | --- |
| Wynn item compatibility | Handles Wynn-specific tooltip compatibility flows | Reuses the mod's style-preserving tooltip pipeline |
| Tracked quest support | Translates tracked quest title/type/description from WynnCraft-related UI integrations such as Wynntils `ContentTracker` | Independent route + target language, refresh-cache |

### AI provider and routing system

- Multiple provider profiles in one config.
- Provider types:
  - `OPENAI_COMPAT` (`/chat/completions`)
  - `OPENAI_RESPONSE` (`/responses`)
  - `OLLAMA` (`/api/chat`)
- Per-route model selection for:
  - Chat Output
  - Chat Input
  - Item Translation
  - Scoreboard Translation
  - WynnCraft tracked quest translation
- Each module has an independent **Target Language** field.

### Model-level controls

- Model ID
- Temperature
- Ollama keep_alive (for Ollama profiles)
- Supports system message toggle
- Inject system prompt into user message toggle (used when system-message mode is disabled)
- Structured output toggle with compatibility fallback
- Prompt suffix
- Custom parameters (JSON tree editor)
- Set as default model

### Runtime behavior and reliability

- Translation pipeline preserves style markers/placeholders/tokens as much as possible.
- Item, scoreboard, and WynnCraft tracked-quest template caches persisted on disk with configurable automatic backups.
- Dedicated item-tooltip refresh hotkey can invalidate and rebuild the current tooltip cache immediately.
- WynnCraft tracked quest translation has its own refresh-cache hotkey that only invalidates the currently tracked quest keys.
- Retry queue prioritizes requeued failed items (front-of-queue retry).
- Batch translation with configurable batch size/thread count (item/scoreboard, plus a dedicated WynnCraft tracked-quest runtime route).
- Session-epoch guard prevents stale async callbacks from writing outdated results after world/session switches.
- Missing-key and key-mismatch detection triggers prioritized retries with clear in-game fallback/status behavior.
- Version-change safety backup stores existing config/cache files before upgrade-sensitive startup flows.

### Command and update helpers

- Startup update check against the current GitHub repository releases (latest tag detection).
- In-game update notice in chat and config modal, with one-click open release page.
- Client command: `translate_allinone translatechatline <messageId>` for manual chat-line retranslation (used by manual `[T]` workflows).

### In-game config UI features

- Full ModMenu-based custom config screen.
- Structured sections with group boxes.
- Scroll + clipping + scrollbar drag support for long content.
- Provider/model operations inside game: add/remove providers, test connection, manage route models, set default model, edit custom JSON parameters.
- Closing the config screen with unsaved changes now opens a save/discard/cancel confirmation modal.
- Unified action flow:
  - **Done** = save and close
  - **Cancel** = leave the screen; if there are unsaved changes, you can save, discard, or return
  - **Reset** (red button) = reset to defaults after confirmation
- Built-in hotkey capture in config UI (no legacy requirement to bind in Minecraft Controls for these module bindings), including separate refresh bindings for item-tooltip translation and WynnCraft tracked-quest translation.
- Cache Backup section exposes backup interval/count, current cache stats, and one-click open cache directory.
- Config-side update notice modal supports opening the latest release page directly.

## Requirements

- Minecraft `1.21.10`
- Fabric Loader `>= 0.18.1`
- Java `>= 21`
- Fabric API
- ModMenu `>= 16.0.0`

## Installation

1. Install Fabric Loader for Minecraft 1.21.10.
2. Put these jars into your `mods` folder:
   - `translate-all-in-one-*.jar`
   - Fabric API
   - ModMenu
3. Launch the game and open ModMenu.
4. Open **Translate All in One** config and set provider/model routes.
5. If you use WynnCraft-related mods such as Wynntils, configure the `WynnCraft` section to enable tracked-quest translation and related integration options.

## Quick Start

1. Add at least one provider profile in `Providers`.
2. Add models under the provider and set route models for each module.
3. Set module target language (Chat Output/Input, Item, Scoreboard).
4. Configure hotkeys/modes where needed, including refresh-cache hotkeys for item tooltip and WynnCraft tracked quests if you want manual cache invalidation.
5. Click **Done** to save and close.

## Config and Runtime Files

- Main config:
  - `config/translate_allinone/config.json`
- Caches:
  - `config/translate_allinone/item_translate_cache.json`
  - `config/translate_allinone/scoreboard_translate_cache.json`
  - `config/translate_allinone/wynncraft_quest_translate_cache.json`
- Automatic cache backups:
  - `config/translate_allinone/translate_cache_backup/`
- Version-change safety backups:
  - `config/translate_allinone/translate_update_backup/`

## Build From Source

```bash
./gradlew build
```

Useful commands:

```bash
./gradlew check
./gradlew runClient
```

## License

MIT. See [LICENSE](./LICENSE).

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

### WynnCraft

| 功能 | 作用 | 主要特点 |
| --- | --- | --- |
| Wynn 物品兼容 | 处理 Wynn 系物品 Tooltip 的兼容链路 | 复用本模组现有的样式保留 Tooltip 翻译管线 |
| 任务追踪支持 | 翻译 WynnCraft 相关 UI 集成里的任务标题/类型/描述，例如 Wynntils `ContentTracker` | 独立路由与目标语言、缓存强制刷新 |

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
  - WynnCraft 任务追踪翻译
- 每个模块都可独立配置目标语言。

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
- 物品、计分板与 WynnCraft 任务追踪采用持久化模板缓存，并支持可配置的自动备份。
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
- 缓存备份分区支持配置备份策略、查看缓存统计并一键打开缓存目录。
- 配置界面的更新提示弹窗支持直接打开最新版本发布页。

## 运行环境要求

- Minecraft `1.21.10`
- Fabric Loader `>= 0.18.1`
- Java `>= 21`
- Fabric API
- ModMenu `>= 16.0.0`

## 安装步骤

1. 安装 Minecraft 1.21.10 对应的 Fabric Loader。
2. 将以下文件放入 `mods` 文件夹：
   - `translate-all-in-one-*.jar`
   - Fabric API
   - ModMenu
3. 启动游戏并在 ModMenu 中打开本模组配置。
4. 配置服务商、模型与路由后保存。
5. 如果你安装了 Wynntils 等 WynnCraft 相关模组，可继续在 `WynnCraft` 大类中启用任务追踪翻译和相关集成功能。

## 快速配置建议

1. 在 `Providers` 中先添加至少一个服务商。
2. 为服务商添加模型，并设置各模块路由模型。
3. 分别填写各模块的目标语言
4. 配置需要的快捷键与模式；如有需要，也可以设置 Tooltip 与 WynnCraft 任务追踪的缓存刷新快捷键。
5. 点击 **完成** 保存。

## 配置与缓存文件

- 主配置：
  - `config/translate_allinone/config.json`
- 缓存文件：
  - `config/translate_allinone/item_translate_cache.json`
  - `config/translate_allinone/scoreboard_translate_cache.json`
  - `config/translate_allinone/wynncraft_quest_translate_cache.json`
- 自动缓存备份目录：
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
