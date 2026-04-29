# Translate All in One

<div align="center">

**English** | [简体中文](./README.zh.md)

</div>

> **Preview Notice**
>
> - Current target version: **Minecraft 1.21.11**
> - Platform: **Fabric (Client-side)**
> - Java: **21+**
> - This project is actively evolving. Feedback and bug reports are welcome in Issues.

An in-game AI translation mod for Minecraft that supports chat, chat input, item tooltip, scoreboard, WynnCraft NPC dialogue, and a dedicated WynnCraft category for related integrations with multi-provider routing, editable local dictionaries, an AI chat-input assistant panel, automatic cache backup, and a fully in-game configuration workflow.

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
| Wynn NPC Dialogue | Translates WynnCraft NPC dialogue and clickable options from chat and overlay into a dedicated HUD | Local dictionary priority, cache + AI fallback, per-row options rendering with animation, in-game HUD editor |

### WynnCraft

| Feature | What it does | Highlights |
| --- | --- | --- |
| Wynn item compatibility | Handles Wynn-specific tooltip compatibility flows | Reuses the mod's style-preserving tooltip pipeline, local `wynncraft_skills.json` dictionary support for fixed-format lines |
| Tracked quest support | Translates tracked quest title/type/description from WynnCraft-related UI integrations such as Wynntils `ContentTracker` | Shared WynnCraft target language, refresh-cache, cache + AI fallback |
| Dictionary config | Exposes a dedicated in-game dictionary section, per-slot toggles, and file selectors | Supports a master switch, independent toggles for item/skill, dialogue, and quest dictionaries, mixed multi/single file selection, and direct open-directory access |

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
  - Wynn NPC Dialogue
  - WynnCraft tracked quest translation
- Chat Output/Input, Item, Scoreboard, and the WynnCraft  have an independent **Target Language** field.

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
- Item, scoreboard, Wynn NPC dialogue, and WynnCraft tracked-quest template caches persisted on disk with configurable automatic backups.
- Bundled WynnCraft dictionaries are installed to `config/translate_allinone/dictionary/` at startup without overwriting existing edited files.
- Supported WynnCraft flows prefer local dictionary hits first, then cache, then remote AI fallback.
- `Dictionary Config` includes a master switch and per-slot enable toggles; when enabled with a dictionary file selected, supported flows use `dictionary -> cache -> remote AI`. When no file is selected for a slot, that slot's dictionary lookup is skipped.
- `Item / Skill` supports multi-select dictionary files that are loaded together on the shared item/skill local-dictionary path; `Dialogue` and `Quest` keep independent single-file selection.
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
- WynnCraft config includes NPC dialogue options, local-hit logging toggles, and an in-game HUD editor for dialogue scale/offset preview.
- Cache Backup section exposes backup interval/count, current cache stats for item / scoreboard / Wynn NPC dialogue / WynnCraft tracked quest, and one-click open cache directory.
- Config-side update notice modal supports opening the latest release page directly.

## Requirements

- Minecraft `1.21.11`
- Fabric Loader `>= 0.18.1`
- Java `>= 21`
- Fabric API
- ModMenu `>= 16.0.0`

## Installation

1. Install Fabric Loader for Minecraft `1.21.11`.
2. Put these jars into your `mods` folder:
   - `translate-all-in-one-*.jar`
   - Fabric API
   - ModMenu
3. Launch the game and open ModMenu.
4. Open **Translate All in One** config and set provider/model routes.
5. If you use WynnCraft-related mods such as Wynntils, configure the `WynnCraft` section to enable NPC dialogue translation, tracked-quest translation, and HUD-related options.
6. In `Dictionary Config`, leave the master switch enabled if you want supported flows to check local dictionary matches before cache/AI fallback.
7. After the first launch, optionally edit the local JSON files inside `config/translate_allinone/dictionary/` to extend dictionary matches.

## Quick Start

1. Add at least one provider profile in `Providers`.
2. Add models under the provider and set route models for each module you want to use.
3. Set module target language.
4. If needed, enable Wynn NPC dialogue and/or tracked-quest translation and adjust the dialogue HUD position/scale.
5. In `Dictionary Config`, keep the master switch on if you want supported flows to try local dictionary matches first.
6. Configure hotkeys/modes where needed, including refresh-cache hotkeys for item tooltip and WynnCraft tracked quests if you want manual cache invalidation.
7. Click **Done** to save and close.

## Config and Runtime Files

- Main config:
  - `config/translate_allinone/translate_allinone.json`
  - includes `dictionary.enabled`, per-slot enable fields, and selected dictionary file fields for item/skill, dialogue, and quest routing
- Caches:
  - `config/translate_allinone/item_translate_cache.json`
  - `config/translate_allinone/scoreboard_translate_cache.json`
  - `config/translate_allinone/wynncraft_dialogue_translate_cache.json`
  - `config/translate_allinone/wynncraft_quest_translate_cache.json`
- WynnCraft local dictionaries:
  - `config/translate_allinone/dictionary/`
  - typically includes files such as `items.json` and `README.txt`
- Automatic cache backups (timestamped snapshot directories):
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
