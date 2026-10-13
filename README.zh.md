# Translate All in One

<div align="center">

[![Downloads](https://img.shields.io/modrinth/dt/WEuWEFmQ?label=Downloads&logo=modrinth&style=flat-square)](https://modrinth.com/mod/translate_allinone(fork))

[English](./README.md) | **简体中文**

一个运行在客户端的 Minecraft AI 翻译模组

</div>

翻译范围覆盖聊天消息、发送前的输入内容、物品 Tooltip、计分板、告示牌、实体名称、书本，以及其他模组界面上的文字；WynnCraft 的 NPC 对话和任务文本。

---

## 安装

1. 安装 Fabric Loader、Fabric API 与 ModMenu。
2. 从 [Modrinth](https://modrinth.com/mod/translate_allinone(fork)) 下载对应游戏版本的 jar 放进 `mods/`（也可以从源码自行构建）。
3. 启动游戏，输入 `/taio`，或在 ModMenu 的模组列表中打开设置界面。

## 设置

在「供应商」中添加一个供应商——OpenAI 兼容接口、Responses API 或 Ollama——再为它添加模型，然后指定哪些模块使用该模型、分别翻译成哪种语言。

告示牌、实体文本、书本和界面翻译默认关闭，可在「其他翻译」中开启。本地字典能优先于模型给出译文。统计界面会按模型记录 Token 用量。

## 功能一览

| 模块 | 作用 |
| --- | --- |
| 聊天输出 | 翻译收到的聊天消息 |
| 聊天输入 | 翻译发送前的输入框内容 |
| 物品 | 翻译物品名称与 Lore |
| 计分板 | 翻译计分板文本 |
| 告示牌 | 翻译范围内可见告示牌的正反面文字 |
| 实体文本 | 翻译非玩家实体的自定义名称，以及可选的文本展示实体与掉落物悬浮名称 |
| 书本 | 翻译书本阅读界面 |
| 界面 | 翻译其他模组配置界面上的文字 |
| 字典 | 提供独立的字典分区与文件选择器 |

以上模块的开关、目标语言与路由均在游戏内 `/taio` 设置界面中配置。

## WynnCraft 支持

- **Wynn 物品**：Wynn 系物品 Tooltip 复用同一套保留样式的 Tooltip，固定格式的技能文本可用 `wynncraft_skills.json` 本地字典。
- **NPC 对话**：把 NPC 对话与可点击选项从聊天栏和 overlay 翻译到独立 HUD（可选），默认为替换原有 overlay 文本；本地字典优先，缓存 + AI 回退，HUD 位置与缩放可在游戏内编辑。
- **任务追踪**：翻译 Wynntils `ContentTracker` 等 WynnCraft 集成里的任务标题、类型与描述，与对话共用目标语言。

## 服务商与模型

- 可同时配置多个供应商档案。
- 支持三种接口类型：`OPENAI_COMPAT`（`/chat/completions`）、`OPENAI_RESPONSE`（`/responses`）、`OLLAMA`（`/api/chat`）。
- 聊天输出／输入、物品、计分板、告示牌、书本、实体文本、WynnCraft 对话与任务追踪，均可独立指定路由模型与目标语言。
- 模型级参数包括：模型 ID、温度、Ollama `keep_alive`、是否支持 System 消息（不支持时可把提示词注入用户消息）、结构化输出开关、提示词后缀、自定义参数（JSON 树编辑），以及各模块的提示词。

## 自定义提示词

提示词按供应商分别配置。在供应商详情页点击「提示词」进入编辑器，可逐个模块覆盖内置的系统提示词：

- 物品 Tooltip、计分板、聊天输出、聊天输入
- Wynn NPC 对话、Wynntils 任务追踪
- 告示牌与书本、实体文本、界面文本

编辑规则：

- 留空即使用内置默认提示词。
- 支持 `{target_language}` 占位符，发送时会替换为该模块当前配置的目标语言。
- 编辑器下方以「运行时强制契约（只读）」列出输出格式（纯文本或 JSON 对象）与受保护 token 规则，这些内容由模组在发送请求时自动追加，不受自定义提示词影响。

内置提示词随版本更新调整时，已有的自定义会被重置回默认值，并在聊天栏提示受影响的供应商，可点击直接打开配置界面重新设置。

## 文件位置

所有文件都位于 `config/translate_allinone/`：

- `translate_allinone.json`：设置
- `prompts.json`：自定义提示词
- `token_usage.json`：按模型的 Token 用量统计
- `dictionary/`：本地字典
- `translate_cache/`：翻译缓存
- `translate_cache_backup/`、`translate_update_backup/`：带时间戳的自动备份与升级前安全副本

## 从源码构建

```bash
./gradlew build
./gradlew check
./gradlew runClient
```

## 许可证

本项目采用 [MIT License](./LICENSE)。
