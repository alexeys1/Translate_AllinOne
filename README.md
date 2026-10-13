# Translate All in One

<div align="center">

[![Downloads](https://img.shields.io/modrinth/dt/WEuWEFmQ?label=Downloads&logo=modrinth&style=flat-square)](https://modrinth.com/mod/translate_allinone(fork))

**English** | [简体中文](./README.zh.md)

An in-game AI translation mod for the Minecraft client

</div>

It translates chat messages, the text you are about to send, item tooltips, the scoreboard, signs, entity names, books, and text on other mods' screens — plus WynnCraft NPC dialogue and quest text.

---

## Installation

1. Install Fabric Loader, Fabric API, and ModMenu.
2. Download the jar for your game version from [Modrinth](https://modrinth.com/mod/translate_allinone(fork)) and drop it into `mods/` (or build it from source yourself).
3. Launch the game and run `/taio`, or open the settings screen from the mod list in ModMenu.

## Configuration

Add a provider — OpenAI-compatible, Responses API, or Ollama — then add a model to it, then choose which modules use that model and which language each module translates into.

Sign, entity, book, and screen translation are off by default; enable them under `Other Translations`. Local dictionaries can answer before the model does. The statistics screen records token usage per model.

## Feature Overview

| Module | What it does |
| --- | --- |
| Chat Output | Translates received chat messages |
| Chat Input | Translates the input box content before sending |
| Item | Translates item names and lore |
| Scoreboard | Translates scoreboard text |
| Sign | Translates the front and back text of visible signs within range |
| Entity Text | Translates custom names of non-player entities, plus optional text-display entities and item-entity hover names |
| Book | Translates the book reading screen |
| Screen | Translates text on other mods' configuration screens |
| Dictionary | Provides a dedicated dictionary section with file selectors |

All module toggles, target languages, and routing are configured in the in-game `/taio` settings screen.

## WynnCraft Support

- **Wynn items**: Wynn item tooltips reuse the same style-preserving tooltip pipeline; fixed-format skill text can use the local `wynncraft_skills.json` dictionary.
- **NPC dialogue**: translates NPC dialogue and clickable options from chat and overlay into a dedicated HUD (optional), replacing the original overlay text by default; local dictionary first, cache + AI fallback, HUD position and scale editable in game.
- **Quest tracking**: translates quest title, type, and description in WynnCraft integrations such as Wynntils `ContentTracker`, sharing the target language with dialogue.

## Providers and Models

- Multiple provider profiles can be configured at the same time.
- Three interface types: `OPENAI_COMPAT` (`/chat/completions`), `OPENAI_RESPONSE` (`/responses`), `OLLAMA` (`/api/chat`).
- Chat output/input, item, scoreboard, sign, book, entity text, WynnCraft dialogue, and quest tracking can each use their own route model and target language.
- Model-level settings: model ID, temperature, Ollama `keep_alive`, whether System messages are supported (the prompt can be injected into the user message when they are not), structured output toggle, prompt suffix, custom parameters (JSON tree editor), and per-module prompts.

## Custom Prompts

Prompts are configured per provider. Open a provider's detail page and click `Prompts` to edit them; the built-in system prompt can be overridden per module:

- Item Tooltip, Scoreboard, Chat Output, Chat Input
- Wynn NPC Dialogue, Wynntils Task Tracker
- Signs and Books, Entity Text, Screen UI

Rules:

- An empty field uses the built-in default prompt and is marked `(empty — using built-in default prompt)`. `Reset` restores the default, and unsaved changes are marked `(unsaved)`.
- The `{target_language}` placeholder is replaced with the module's currently configured target language when the request is sent.
- The editor lists the runtime contracts as read-only (`Runtime forced contracts`): the output shape (plain text or a JSON object) and the protected-token rules are appended by the mod when the request is sent and are not affected by your custom prompt.
- A warning is shown before the editor opens: you need to understand how prompts work, so do not modify them unless necessary.
- The model-level `Prompt Suffix` is still appended after the prompt.
- Custom content is stored per provider in `config/translate_allinone/prompts.json`.

When the built-in prompts change in a version update, existing customizations are reset to the defaults, and the affected providers are reported in chat with a link that opens the config screen to set them again.

## File Locations

Everything lives under `config/translate_allinone/`:

- `translate_allinone.json`: settings
- `prompts.json`: custom prompts
- `token_usage.json`: token usage per model
- `dictionary/`: local dictionaries
- `translate_cache/`: translation caches
- `translate_cache_backup/`, `translate_update_backup/`: timestamped automatic backups and the pre-upgrade safety copy

## Building From Source

```bash
./gradlew build
./gradlew check
./gradlew runClient
```

## License

This project is licensed under the [MIT License](./LICENSE).
