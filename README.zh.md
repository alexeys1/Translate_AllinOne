# Translate All in One

<div align="center">

[English](./README.md) | **简体中文**

</div>

> **预览提示**
>
> - 当前目标版本：**Minecraft 26.1.x**
> - 平台：**Fabric（客户端）**
> - Java：**25+**

一款 Minecraft 游戏内 AI 翻译模组，支持聊天输出、聊天输入、物品 Tooltip、计分板、告示牌、实体名称与文本展示实体、书本，以及独立的 WynnCraft 相关集成分类；提供多服务商路由、可编辑本地字典、AI 聊天输入助手面板、模块化缓存、自动备份和完整的游戏内配置流程。

---

## 简体中文

## 当前已实现功能（完整）

### 翻译模块

| 模块         | 功能                                           | 主要特点                                                                                                    |
| ------------ | ---------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| 聊天输出翻译 | 翻译收到的聊天消息                             | 支持自动翻译和手动 `[T]` 点击翻译，支持流式显示                                                           |
| 聊天输入翻译 | 发送前翻译输入框内容                           | 快捷键触发翻译 + AI 改写面板（翻译/专业/友好/扩写/简化/还原），可流式回填输入框                             |
| 物品翻译     | 翻译物品名称与 Lore                            | 模板/样式保留，异步缓存队列，支持 Tooltip 缓存刷新                                                          |
| 计分板翻译   | 翻译侧边栏显示文本                             | 前后缀与玩家名按配置实时替换                                                                                |
| 告示牌翻译   | 翻译半径内可见告示牌的正反面文字               | 仅替换客户端渲染结果，不修改世界中的原文；支持连续翻译、原文/译文显示模式与缓存刷新                         |
| 实体文本翻译 | 翻译非玩家实体的自定义名称，以及可选的文本展示实体和掉落物悬浮名称 | 按可见半径处理，可独立启用各类实体文本，并保留原有组件样式                                                   |
| 书本翻译     | 翻译已签名书本阅读界面的当前页                 | 不修改书本原始内容；可限制单页长度、预取相邻页面，并支持原文/译文显示模式与缓存刷新                         |
| 字典配置     | 提供独立的游戏内字典分区、独立开关与文件选择器 | 支持总开关、`物品/技能` / `对话` / `任务` 三个独立开关，以及混合的复选/单选字典文件选择与目录直达入口 |

### WynnCraft

| 功能              | 作用                                                                                | 主要特点                                                                                               |
| ----------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| Wynn 物品兼容     | 处理 Wynn 系物品 Tooltip 的兼容链路                                                 | 复用本模组现有的样式保留 Tooltip 翻译管线，并支持固定格式技能文本的 `wynncraft_skills.json` 本地字典 |
| Wynn NPC 对话翻译 | 将 WynnCraft NPC 对话与可点击选项从聊天栏与 overlay 翻译到独立 HUD                  | 本地字典优先，缓存 + AI 回退，选项逐行渲染与动画，HUD 编辑器                                           |
| 任务追踪支持      | 翻译 WynnCraft 相关 UI 集成里的任务标题/类型/描述，例如 Wynntils `ContentTracker` | 共享 WynnCraft 目标语言、支持缓存强制刷新、缓存 + AI 回退                                              |

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
  - 告示牌翻译
  - 书本翻译
  - 实体文本翻译
  - Wynn NPC 对话翻译
  - WynnCraft 任务追踪翻译
  - 聊天输出/输入、物品、计分板与 WynnCraft 可分别配置目标语言。

### 模型级参数（Model Settings）

- 模型 ID
- 温度
- Ollama keep_alive（仅 Ollama）
- 是否支持 System 消息
- 当不支持 System 消息时，是否将提示词注入用户消息
- 结构化输出开关（带兼容回退）
- 提示词后缀
- 自定义参数（JSON 树编辑）
- 各模块提示词编辑

### 游戏内指令

```text
/taio 打开模组配置面板
```

### 运行时特性与稳定性

- 翻译流程尽量保留样式标记、占位符与关键 token。
- 告示牌、实体文本、书本、物品 Tooltip、计分板与进度文本使用结构化 Minecraft `Component` 翻译管线：仅提交可翻译文本单元，回填译文时保留原组件结构、样式与受保护 token。返回内容不符合结构或缺少 token 时会拒绝写入并优先重试。
- `Component` 缓存已按功能拆分为物品、计分板、告示牌、实体、书本和进度六类独立存储；各类可单独统计，避免不同翻译场景相互影响。
- 物品、计分板、告示牌、实体、书本、Wynn NPC 对话与 WynnCraft 任务追踪均使用持久化缓存，并支持可配置的自动备份。
- 支持的 WynnCraft 文本链路会优先命中本地字典，其次查缓存，最后再走远端 AI 回退。
- `字典配置` 分区提供字典总开关和每个槽位的独立启用开关；当启用且已选择字典文件时，支持的链路按 `字典 -> 缓存 -> 远端 AI` 顺序处理。若某槽位未选择文件，则该槽位的字典查找会被跳过。
- `物品/技能` 字典选择器支持复选并会同时加载多个字典文件；`对话` 与 `任务` 字典选择器保持单选。。
- `其他翻译` 分区可分别开启告示牌、实体文本与书本翻译，设置目标语言、显示模式、作用半径、请求并发数/批量大小，以及翻译/查看原文和刷新缓存快捷键。
- 物品 Tooltip 支持独立刷新缓存快捷键，可强制刷新当前缓存并立即重新排队翻译；`其他翻译` 的刷新快捷键也可用于告示牌、实体文本与书本。
- missing key / key mismatch 会触发优先重试，并提供更明确的游戏内状态回退与反馈。
- 版本变更时会自动备份现有配置与缓存文件，降低升级过程中的风险。


### 配置界面特性

- 基于 ModMenu 的完整自定义配置界面。
- 分组框布局（Basic / Hotkey / Performance / Route / Providers）。
- 支持滚动、裁剪、滚动条拖动，长列表/小窗口可正常使用。
- 可在游戏内完成 provider/model 管理：新增/删除供应商、测试连接、设置路由模型、设为默认模型、自定义参数树、各模块温度编辑。
- 模块快捷键支持在配置界面内直接捕获与清除，包含独立的 Tooltip、其他翻译与 WynnCraft 任务追踪缓存刷新快捷键。
- WynnCraft 分组已包含 NPC 对话开关，以及 HUD 位置/缩放的游戏内预览编辑器。
- 缓存备份分区支持配置备份策略，查看物品 / 计分板 / 告示牌 / 实体 / 书本 / Wynn NPC 对话 / WynnCraft 任务追踪等缓存统计，并一键打开缓存目录。
- 配置界面的更新提示弹窗支持直接打开最新版本发布页。

## 运行环境要求

- Minecraft `26.1.x`
- Fabric Loader `>= 0.19.3`
- Java `>= 25`
- Fabric API
- ModMenu `>= 18.0.0`

## 快速配置建议

1. 在 `供应商` 中先添加至少一个供应商。
2. 为供应商添加模型，并为需要使用的模块设置路由模型。
3. 分别填写各模块的目标语言；如需告示牌、实体或书本翻译，请在 `其他翻译` 中配置其目标语言和开关。
4. 如有需要，启用 Wynn NPC 对话翻译和/或任务追踪翻译，并调整对话 HUD 的位置与缩放。
5. 如果希望支持的链路先命中本地字典，请在 `字典配置` 中保持字典开关开启。
6. 配置需要的快捷键与显示模式；如有需要，也可以设置 Tooltip、其他翻译与 WynnCraft 任务追踪的缓存刷新快捷键。

## 配置与缓存文件

- 主配置：
  - `config/translate_allinone/translate_allinone.json`
  - 包含 `dictionary.enabled`、每个槽位的独立启用状态，以及 `item_skill_dictionary_files` / `wynncraft_dialogue_dictionary_file` / `wynncraft_quest_dictionary_file`
- 缓存文件：
  - `config/translate_allinone/item_translate_cache.json`
  - `config/translate_allinone/translate_cache/wynncraft_dialogue_translate_cache.json`
  - `config/translate_allinone/translate_cache/wynncraft_quest_translate_cache.json`
- `Component` 缓存目录：
  - `config/translate_allinone/translate_cache/component_item_translate_cache.json`
  - `config/translate_allinone/translate_cache/component_scoreboard_translate_cache.json`
  - `config/translate_allinone/translate_cache/component_sign_translate_cache.json`
  - `config/translate_allinone/translate_cache/component_entity_translate_cache.json`
  - `config/translate_allinone/translate_cache/component_book_translate_cache.json`
  - `config/translate_allinone/translate_cache/component_advancement_translate_cache.json`
- 缓存布局迁移记录与迁移前快照：
  - `config/translate_allinone/translate_cache/migration/`
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
