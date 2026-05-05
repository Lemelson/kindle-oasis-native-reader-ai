# Kindle Oasis Native Reader AI Patch

This repository documents and builds the native Kindle reader patches used on a
Kindle Oasis running firmware 5.18.2.

The current working product is not a KOReader plugin. It is a native Amazon
reader modification:

- The visible selection toolbar has a `Translate` button for the built-in Amazon
  Translation card.
- The translation target is set to Russian by default.
- The visible `Note` action is replaced with an `AI` action.
- The `AI` action opens a compact two-column prompt menu:
  `Explain`, `Words`, `Recall`, `Why`, `Deep`, and `Close`.
- The AI card sends the selected text, book metadata, and surrounding context to
  OpenRouter using `deepseek/deepseek-v4-flash`.
- The current build streams DeepSeek reasoning text first, then clears the card
  and streams the final answer into a response-only card.
- Final answers render a safe Markdown subset for bold, italic, headings,
  inline code, and normal lists.

Current local git checkpoint:

```text
tag: ai-note-v13-popup-menu
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

Each AI action opens a response-only smart-card. The card shows streamed
reasoning while the model is thinking. When the final answer starts, the
thinking view is replaced by the final answer text.

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
patch-work/patched-ai-v13/ReaderSDK-impl.ai-v13.jar
SHA-256: 3b93bebd0a7a9c6d4848996089a204a28fd004fc0b1f791ec077169dea220e4e

patch-work/patched-v4/Reader-plugin.jar
SHA-256: ee547187d985b8b0391bf02165b093c6d9a3a6d799eee4e2b2e399b9bd8e8811
```

Current KUAL install action:

```text
KUAL -> Native Reader Patch -> Install AI Note v13 popup menu
```

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
