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
KUAL -> Native Reader Patch -> Install AI Note v24 streaming
```

The installer:

1. verifies required patched jars and config exist
2. backs up current system jars under `/mnt/us/native-reader-research/backups`
3. remounts root read-write
4. copies patched jars into `/opt/amazon/ebook/lib`
5. restores root read-only
6. reboots

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

This restores the original `ReaderSDK-impl.jar`.

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

## Current Stable AI State

The current AI checkpoint is:

```text
AI Note v14 v11 UI upgrade
```

It includes:

- one embedded AI smart-card with top prompt buttons and lower output area
- streaming reasoning
- streaming final answer
- short tap for DeepSeek V4 Flash
- long press for DeepSeek V4 Pro
- explicit retry/fallback status, including Xiaomi MiMo V2.5 Pro fallback
- optional local Xray/VLESS HTTP proxy for OpenRouter requests in v15

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

The AI jar is built with:

```sh
rm -rf patch-work/classes-ai-v23 patch-work/patched-ai-v23
mkdir -p patch-work/classes-ai-v23
CP=$(printf '%s:' from-kindle/native-reader-research/jars/*.jar)
javac --release 8 -cp "$CP" -d patch-work/classes-ai-v23 \
  $(find patch-work/stubs patch-work/src/com/amazon/ebook/booklet/reader/plugin/notesandmarks -name '*.java' -print)
python3 patch-work/scripts/build_ai_note_patch.py
```

Then copy the jar and KUAL extension files to the mounted Kindle USB partition.

## Git Notes

At the time this documentation was written, the repository had local commits and
tags but no configured remote:

```sh
git remote -v
# no output
```

So the work is committed locally, but not pushed to GitHub from this machine.
