# KOReader Assistant Plugin Trial

This document records the KOReader assistant installation used for comparison
with the native Kindle reader patch.

## Installed Components

Kindle USB paths:

```text
/mnt/us/koreader/
/mnt/us/extensions/koreader/
/mnt/us/koreader/plugins/assistant.koplugin/
```

Installed versions:

```text
KOReader: v2026.03 kindlehf
assistant.koplugin: v1.10
```

The assistant plugin is installed as a KOReader plugin. It does not replace the
native Kindle reader patch and does not change `ReaderSDK-impl.jar`.

## Configuration

Configuration path:

```text
/mnt/us/koreader/plugins/assistant.koplugin/configuration.lua
```

The config uses the same OpenRouter account as the native reader patch, but the
API key is intentionally not stored in this repository.

Important settings:

```text
provider=openrouter
model=deepseek/deepseek-v4-flash
base_url=https://openrouter.ai/api/v1/chat/completions
temperature=0.2
max_tokens=3000
reasoning.effort=high
reasoning.exclude=true
provider.allow_fallbacks=false
render_markdown=true
```

`reasoning.exclude=true` is used for this KOReader trial because
assistant.koplugin's streaming UI is primarily designed around final answer
streaming. The native Kindle patch still uses visible reasoning in v12.

## Custom Highlight Prompts

The config adds the same five reading intentions used by the native patch:

```text
Explain
Words
Recall
Why
Deep
```

Each is marked with `show_on_main_popup=true`, so KOReader should add direct
`(AI)` buttons to the highlight popup. The prompts answer in Russian and preserve
the same spoiler-safe reading-assistant behavior.

The built-in `Translate` prompt is also overridden to translate to Russian.

## Enabled KOReader Assistant Features

The settings file enables:

```text
response_language=Russian
use_stream_mode=true
large_stream_dialog=true
stream_mode_auto_scroll=true
ai_translate_override=true
dict_popup_show_dictionary=true
dict_popup_show_wikipedia=true
dict_popup_show_term_xray=true
dict_popup_show_custom_prompts=true
```

`ai_translate_override=true` makes KOReader's Translate gesture call the AI
assistant path: longer text uses the Translate prompt, while short word/phrase
lookups use AI Dictionary.

## Local Compatibility Patch

Two small local changes were applied to the installed plugin copy:

- `api_handlers/openrouter.lua` now forwards
  `additional_parameters.provider`, allowing `allow_fallbacks=false`.
- `assistant_querier.lua` recognizes OpenRouter `delta.reasoning` and
  `delta.reasoning_details` in addition to `reasoning_content`.

These changes are only in the installed Kindle plugin copy and the temporary
download directory, not vendored into this repository.
