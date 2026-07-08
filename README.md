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
  `Explain`, `Words`, `Recall`, `Why`, and `Deep`.
- The AI card sends the selected text, book metadata, and surrounding context to
  OpenRouter using `deepseek/deepseek-v4-flash` by default.
- A short tap on a prompt uses Flash; a long press uses DeepSeek V4 Pro.
- The current build streams model reasoning/status, retry/fallback notices, and
  the final answer in the same lower output area.

Current local git checkpoint:

```text
current: AI Note v15 VPN proxy
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
select text -> AI -> Explain / Words / Recall / Why / Deep
```

The AI card starts with an empty output area. Short-tapping a mode starts a
Flash request immediately. Long-pressing a mode for about 0.8 seconds starts a
DeepSeek V4 Pro request instead.

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
patch-work/patched-ai-v19/ReaderSDK-impl.ai-v19.jar
SHA-256: 2a5736e17e2a66bbb475279dfe8fbd80bcdd799c726b6c5e3701d6c1fda1587d

patch-work/patched-v3/Reader-plugin.jar
SHA-256: d978e1e97c7d2042176aa8d12e40e24582d335770e6141a14dffb9659fe1f907
```

Current KUAL install action:

```text
KUAL -> Native Reader Patch -> Install AI Note v19 Gemini 3.5
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
- We did not push this repository to GitHub from this environment. There is no
  configured git remote at the time of writing; commits are local.

More detail:

- [Jailbreak and device setup](docs/JAILBREAK_AND_DEVICE_SETUP.md)
- [Native reader AI architecture](docs/NATIVE_READER_AI_ARCHITECTURE.md)
- [Operations and rollback](docs/OPERATIONS_AND_ROLLBACK.md)
