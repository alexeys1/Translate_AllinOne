# Translate All in One

<div align="center">

**English** | [简体中文](./README.zh.md)

</div>

> **Preview Notice**
>
> - Current target version: **Minecraft 26.1.x**
> - Platform: **Fabric (Client-side)**
> - Java: **25+**

An in-game AI translation mod for Minecraft that supports chat output, chat input, item tooltips, scoreboard translation, a dedicated WynnCraft integration category, multi-provider routing, editable local dictionaries, an AI chat-input assistant panel, automatic cache backups, and a complete in-game configuration workflow.

---

## English

## Current Features (Complete)

### Translation modules

| Module | What it does | Highlights |
| --- | --- | --- |
| Chat Output Translation | Translates incoming chat messages | Supports automatic translation and manual `[T]` click translation, with streaming display |
| Chat Input Translation | Translates the input box content before sending | Hotkey-triggered translation plus AI rewrite panel (Translate/Professional/Friendly/Expand/Concise/Restore), with optional streaming refill into the input box |
| Item Translation | Translates item names and lore | Template/style-preserving, async cache queue, tooltip cache refresh |
| Scoreboard Translation | Translates sidebar text | Prefix/suffix and player names are replaced in real time based on config |
| Dictionary Config | Provides a dedicated in-game dictionary section, independent toggles, and file selectors | Master switch, independent `Item/Skill` / `Dialogue` / `Quest` toggles, mixed multi-select/single-select dictionary file selection, and direct directory access |

### WynnCraft

| Feature | What it does | Highlights |
| --- | --- | --- |
| Wynn Item Compatibility | Handles compatibility flows for Wynn-related item tooltips | Reuses the existing style-preserving tooltip translation pipeline and supports the local `wynncraft_skills.json` dictionary for fixed-format skill text |
| Wynn NPC Dialogue Translation | Translates WynnCraft NPC dialogue and clickable options from chat and overlay into a dedicated HUD | Local dictionary priority, cache + AI fallback, line-by-line option rendering with animation, HUD editor |
| Quest Tracking Support | Translates quest title/type/description in WynnCraft-related UI integrations such as Wynntils `ContentTracker` | Shared WynnCraft target language, forced cache refresh support, cache + AI fallback |

### Provider and routing capabilities

- Multiple provider profiles are supported at the same time.
- Supported provider types:
  - `OPENAI_COMPAT` (`/chat/completions`)
  - `OPENAI_RESPONSE` (`/responses`)
  - `OLLAMA` (`/api/chat`)
- Route models can be set independently for each module:
  - Chat Output
  - Chat Input
  - Item Translation
  - Scoreboard Translation
  - Wynn NPC Dialogue Translation
  - WynnCraft Quest Tracking Translation
- Chat Output/Input, Item, Scoreboard, and WynnCraft can each be assigned their own target language.

### Model-level settings

- Model ID
- Temperature
- Ollama keep_alive (Ollama only)
- Whether System messages are supported
- If System messages are not supported, whether to inject the prompt into the user message
- Structured output toggle (with compatibility fallback)
- Prompt suffix
- Custom parameters (JSON tree editor)
- Per-module prompt editing

### In-game command

```text
/taio opens the mod configuration screen
```

### Runtime behavior and stability

- The translation pipeline preserves style markers, placeholders, and key tokens as much as possible.
- Item, scoreboard, Wynn NPC dialogue, and WynnCraft quest tracking use persistent template caches and support configurable automatic backups.
- Supported WynnCraft text paths prefer local dictionary hits first, then cache, then remote AI fallback.
- `Dictionary Config` provides a master switch and per-slot enable toggles. When enabled and a dictionary file is selected, supported paths follow `dictionary -> cache -> remote AI`. If no file is selected for a slot, dictionary lookup for that slot is skipped.
- The `Item/Skill` dictionary selector supports multi-select and loads multiple dictionary files together; the `Dialogue` and `Quest` dictionary selectors remain single-select.
- Item tooltip translation supports an independent refresh-cache hotkey, which forces the current cache to refresh and immediately requeues translation.
- `missing key` / `key mismatch` trigger prioritized retries and provide clearer in-game status fallback and feedback.
- Version changes automatically back up existing config and cache files to reduce upgrade risk.

### Configuration UI features

- Full custom configuration UI based on ModMenu.
- Group-box layout (Basic / Hotkey / Performance / Route / Providers).
- Scroll, clipping, and scrollbar dragging support for long lists and small windows.
- In-game provider/model management: add/remove providers, test connection, set route models, set default model, custom parameter tree, and per-module temperature editing.
- Module hotkeys can be captured and cleared directly in the config screen, including dedicated refresh hotkeys for tooltip cache and WynnCraft quest tracking cache.
- The WynnCraft section includes NPC dialogue toggles and an in-game preview editor for HUD position and scale.
- The Cache Backup section supports backup strategy settings, cache statistics for item / scoreboard / Wynn NPC dialogue / WynnCraft quest tracking, and one-click opening of the cache directory.
- The config-screen update prompt modal can open the latest release page directly.

## Requirements

- Minecraft `26.1.x`
- Fabric Loader `>= 0.19.3`
- Java `>= 25`
- Fabric API
- ModMenu `>= 18.0.0`

## Quick Setup Suggestions

1. Add at least one provider in `Providers`.
2. Add models for the provider and set route models for the modules you want to use.
3. Fill in the target language for each module.
4. If needed, enable Wynn NPC dialogue translation and/or quest tracking translation, and adjust the dialogue HUD position and scale.
5. If you want supported paths to try the local dictionary first, keep the dictionary switch enabled in `Dictionary Config`.
6. Configure the hotkeys and modes you need; if necessary, also set the tooltip and WynnCraft quest tracking cache refresh hotkeys.

## Config and Cache Files

- Main config:
  - `config/translate_allinone/translate_allinone.json`
  - contains `dictionary.enabled`, each slot's independent enable state, and `item_skill_dictionary_files` / `wynncraft_dialogue_dictionary_file` / `wynncraft_quest_dictionary_file`
- Cache files:
  - `config/translate_allinone/item_translate_cache.json`
  - `config/translate_allinone/scoreboard_translate_cache.json`
  - `config/translate_allinone/wynncraft_dialogue_translate_cache.json`
  - `config/translate_allinone/wynncraft_quest_translate_cache.json`
- WynnCraft local dictionary directory:
  - `config/translate_allinone/dictionary/`
  - usually includes `items.json` and `README.txt`
- Automatic cache backup directory (timestamped snapshot directories):
  - `config/translate_allinone/translate_cache_backup/`
- Version-change safety backup directory:
  - `config/translate_allinone/translate_update_backup/`

## Build From Source

```bash
./gradlew build
```

Common commands:

```bash
./gradlew check
./gradlew runClient
```

## License

This project is licensed under the [MIT License](./LICENSE).
