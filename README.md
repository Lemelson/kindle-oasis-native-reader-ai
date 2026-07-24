# Kindle Oasis Native Reader AI Patch

This repository documents and builds the native Kindle reader patches used on a
Kindle Oasis running firmware 5.18.2.

The current working product is not a KOReader plugin. It is a native Amazon
reader modification:

- The visible selection toolbar has a `Translate` button for the built-in Amazon
  Translation card.
- The translation target is set to Russian by default.
- The visible `Note` action is replaced with an `AI` action.
- The `AI` action opens one embedded smart-card with prompt buttons at the top:
  `Explain`, `Words`, `Wiki`, `Why`, and `Deep`. `Wiki` is an encyclopedia-style
  identify mode for a highlighted person, term, place, event, work, or concept.
- The AI card sends the selected text, book metadata, and surrounding context to
  Gemini using `gemini-3.6-flash` first, with DeepSeek fallbacks.
- A short tap on a prompt uses Flash; a long press uses DeepSeek V4 Pro.
- Opening the AI card immediately starts `Explain`, so the normal path is one
  tap on `AI`.
- Pressing another mode cancels the current HTTP stream and all future retries,
  then starts only the newly selected request. The selected button is shown in
  bold on a gray background.
- Repeated taps on the same active mode/model are ignored, preventing accidental
  duplicate API calls. A long press is a different model choice and can replace
  an active short-tap request.
- Xray starts in parallel with Kindle context extraction and remains available
  until the AI card closes, so switching modes does not restart the tunnel.
- Gemini streams only final-answer chunks into the card. Hidden Gemini thinking
  stays hidden; status and fallback notices remain visible.

Current local git checkpoint:

```text
current: AI Note v25 instant session + latest-request-wins
```

## Device State

Target device:

```text
Model: Kindle Oasis, likely KOA3 / KOA3W32C
Firmware: 5.18.2
Special Offers: disabled
```

Installed foundation:

- Nosebleed jailbreak
- jailbreak hotfix
- KUAL / MRPI
- OTA blocker through `renameotabin`
- USB-visible project files under `/mnt/us/native-reader-research`
- KUAL extension under `/mnt/us/extensions/nativepatch`

The project intentionally keeps the native Amazon reader. KOReader was discussed
as the lower-risk plugin route, but the final project went after the native
reader UI because the desired workflow was:

```text
select text in the normal Kindle reader -> press AI or Translate -> get help in-place
```

## Current User Workflow

Native translation:

```text
select text -> Translate -> Amazon Translation card -> Russian target language
```

AI assistant:

```text
select text -> AI -> Explain starts automatically
```

The AI card automatically starts the normal Flash `Explain` request.
Short-tapping another mode cancels the previous request and starts the normal
Gemini-first chain. Long-pressing a mode for about 0.8 seconds cancels the
previous request and starts with DeepSeek V4 Pro. Only the latest request can
retry or update the card.

## Repository Layout

```text
PATCHLOG.md
    Detailed chronological patch log and hashes.

from-kindle/native-reader-research/jars/
    Local copies of original Kindle jar files pulled from the device.

patch-work/src/com/amazon/ebook/booklet/reader/plugin/notesandmarks/
    Java sources for the AI card, OpenRouter client, config loader, logging,
    and controller code injected into ReaderSDK-impl.jar.

patch-work/scripts/
    Build and bytecode patch scripts.

kual-extension/nativepatch/
    KUAL menu and install/rollback shell scripts copied to the Kindle.

docs/
    Human-readable project documentation.
```

## Current Build Artifacts

Current AI jar:

```text
patch-work/patched-ai-v25/ReaderSDK-impl.ai-v25.jar
SHA-256: fd9c02e517b6b04f1969f682a666fa81715b52bbee48ad321cba326da1da2124

patch-work/patched-v3/Reader-plugin.jar
SHA-256: d978e1e97c7d2042176aa8d12e40e24582d335770e6141a14dffb9659fe1f907
```

Current KUAL install action:

```text
KUAL -> Native Reader Patch -> Install AI Note v25 instant session
```

Use `Enable AI Diagnostics` before a provider routing test. In diagnostics
mode, pressing any AI preset runs connectivity checks instead of a normal AI
answer and writes `/mnt/us/native-reader-research/ai-diagnostics-last.txt`.
Use `Disable AI Diagnostics` to return the same build to normal AI answers.

Safe rollback action:

```text
KUAL -> Native Reader Patch -> Rollback AI Note Patch
```

## What We Did Not Do

- We did not bypass Kindle DRM.
- We did not modify Amazon book content.
- We did not replace the whole Kindle reader.
- We did not implement the Nosebleed exploit ourselves in this repository.
- We did not prove or document Nosebleed internals beyond the public jailbreak
  workflow. Treat exploit mechanism details such as memory corruption claims as
  external research unless verified from primary sources.
- Generated JARs, original Amazon JARs, API keys, and the private Xray/VLESS
  configuration are intentionally excluded from Git. The source history,
  installers, patch documentation, and reproducible build tooling are tracked.

More detail:

- [Jailbreak and device setup](docs/JAILBREAK_AND_DEVICE_SETUP.md)
- [Native reader AI architecture](docs/NATIVE_READER_AI_ARCHITECTURE.md)
- [Operations and rollback](docs/OPERATIONS_AND_ROLLBACK.md)
- [Device cleanup on 2026-07-24](docs/DEVICE_CLEANUP_2026-07-24.md)
