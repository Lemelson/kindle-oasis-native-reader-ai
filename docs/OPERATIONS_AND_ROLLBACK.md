# Operations and Rollback

This document explains how to operate the current patch set and how to recover
from bad builds.

## Current Recommended Install

If the Kindle is connected over USB, current artifacts live under:

```text
/Volumes/Kindle/native-reader-research/patched/
/Volumes/Kindle/extensions/nativepatch/
```

On Kindle, install through:

```text
KUAL -> Native Reader Patch -> Install AI Note v26 large card + dismiss
```

The installer:

1. verifies the exact SHA-256 of both v26 jars and both rollback jars
2. backs up the current Reader plugin and SDK jars under
   `/mnt/us/native-reader-research/backups`
3. remounts root read-write
4. stages and atomically activates only `Reader-plugin.jar` and
   `ReaderSDK-impl.jar`
5. verifies both installed hashes and restores the backups if activation fails
6. makes a best-effort read-only remount and reboots

The v26 installer does not rewrite `translator.jar`, AI configuration, or VPN
scripts.

## Current Config

Kindle path:

```text
/mnt/us/native-reader-research/ai-assistant.properties
```

USB path on macOS:

```text
/Volumes/Kindle/native-reader-research/ai-assistant.properties
```

Current shape:

```text
api_key=...
model=deepseek/deepseek-v4-flash
pro_model=deepseek/deepseek-v4-pro
pro_fallback_model=xiaomi/mimo-v2.5-pro
base_url=https://openrouter.ai/api/v1/chat/completions
temperature=0.2
max_tokens=6750
timeout_ms=45000
reasoning_effort=high
reasoning_exclude=false
vpn_enabled=true
vpn_proxy_host=127.0.0.1
vpn_proxy_port=18080
vpn_start_script=/mnt/us/native-reader-research/vpn/start-ai-vpn.sh
vpn_stop_script=/mnt/us/native-reader-research/vpn/stop-ai-vpn.sh
diagnostics_enabled=false
diagnostic_attempts=2
gemini_api_key=...
gemini_api_keys=...
gemini_base_url=https://generativelanguage.googleapis.com/v1beta
gemini_models=gemini-3.6-flash
gemini_thinking_level=high
gemini_daily_request_limit=20
gemini_quota_window_ms=86400000
gemini_quota_cooldown_ms=3600000
```

Do not commit API keys. Do not paste API keys into logs.

## Logs

Runtime logs and debug snapshots are written under:

```text
/mnt/us/native-reader-research/
```

Important files:

```text
nativepatch.log
ai-last-prompt.txt
ai-last-response.txt
ai-last-error.txt
```

`ai-last-prompt.txt` stores the last prompt and model name, not the API key.

`ai-last-response.txt` can contain model output and reasoning previews, so treat
it as potentially sensitive reading data.

## Normal Rollback

Use this first for AI-specific problems:

```text
KUAL -> Native Reader Patch -> Rollback AI Note Patch
```

This restores the original `ReaderSDK-impl.jar` and the confirmed stable v3
Reader plugin.

If translation-specific changes are suspected:

```text
KUAL -> Native Reader Patch -> Rollback Translator Jar
```

If the selection toolbar patch itself is suspected:

```text
KUAL -> Native Reader Patch -> Rollback Reader Plugin
```

## Stable Non-AI State

The stable Trans-only checkpoint is:

```text
git tag: stable-trans-ru-v3-confirmed
git commit: 87780e9
```

That state keeps:

- visible `Trans`
- native Translation card
- Russian target behavior

It removes:

- AI button
- OpenRouter calls
- AI smart-card

## Current AI States

The connected Kindle currently has the confirmed v25 build installed:

```text
AI Note v25 instant session + latest-request-wins
```

The current local candidate is:

```text
AI Note v26 large card + outside-tap Undo
```

It keeps all v25 behavior and adds:

- one embedded AI smart-card with top prompt buttons and lower output area
- approximately 2x stock height for AI only, with the native Translate size
  unchanged
- native Undo when the book is tapped outside an open AI or Translate card
- unchanged outside-tap behavior for ordinary selections
- one-tap automatic Explain through Gemini 3.6 Flash
- Gemini and OpenRouter final-answer streaming
- short tap for the Gemini-first chain
- long press for DeepSeek V4 Pro
- cancellation of the previous stream/retries when a new mode is selected
- one Xray session kept alive until the AI card closes

The v26 jars require physical on-device validation after installation; host JVM
tests cannot prove E-Ink layout or the Kindle touch event path.

## If Reader UI Freezes

Try in this order:

1. Hold Kindle power button for a long restart.
2. Open KUAL if possible.
3. Run `Rollback AI Note Patch`.
4. If KUAL is unreachable but USB storage mounts, inspect
   `/mnt/us/native-reader-research/nativepatch.log` after reconnecting.
5. If SSH/USBNet is available, restore original jars manually from backups.

Avoid factory reset unless the jailbreak/OTA state is understood. A factory
reset can remove documents and homebrew launchers.

## Build Locally

The AI SDK jar and Reader plugin are built with:

```sh
rm -rf patch-work/classes-ai-v26 patch-work/patched-ai-v26
mkdir -p patch-work/classes-ai-v26
CP=$(printf '%s:' from-kindle/native-reader-research/jars/*.jar)
javac --release 8 -cp "$CP" -d patch-work/classes-ai-v26 \
  $(find patch-work/stubs patch-work/src/com/amazon/ebook/booklet/reader/plugin/notesandmarks -name '*.java' -print)
javac --release 8 -cp "$CP:patch-work/classes-ai-v26" \
  -d patch-work/classes-ai-v26 \
  patch-work/src/com/amazon/ebook/booklet/reader/plugin/share/ShareSelectedTextAction.java
python3 patch-work/scripts/build_ai_note_patch.py
```

Then copy both jars and the KUAL extension files to the mounted Kindle USB
partition.

## Git Notes

Private remote:

```text
https://github.com/Lemelson/kindle-oasis-native-reader-ai
```
