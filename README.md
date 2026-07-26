# Kindle Oasis Native Reader AI

An experimental, firmware-specific modification that adds contextual AI help
inside the stock Kindle Oasis reader.

Select a passage, tap `AI`, and receive an in-place Russian explanation from
Gemini or OpenRouter without leaving the book. The project also exposes Kindle's
built-in Translation card in the main selection toolbar.

[Latest release notes](https://github.com/Lemelson/kindle-oasis-native-reader-ai/releases/latest)
· [Architecture](docs/NATIVE_READER_AI_ARCHITECTURE.md)
· [Privacy](docs/PRIVACY_AND_DATA_FLOW.md)
· [Recovery](docs/OPERATIONS_AND_ROLLBACK.md)

> [!WARNING]
> This is an expert research project for a user-owned Kindle Oasis on firmware
> 5.18.2. It is not a general Kindle app, not a turnkey installer, and not
> verified on other models or firmware. A failed system-JAR modification can
> break the reader UI and require recovery.

## Why This Exists

Kindle's stock reader is good for uninterrupted reading, but understanding a
difficult passage often means switching to a phone or another app. This project
keeps that interaction inside the book:

```text
select text -> tap AI -> read a contextual explanation -> continue reading
```

The assistant can explain meaning, language, references, narrative purpose, or a
deeper idea. It receives the selected fragment together with book metadata and
surrounding context, then renders the answer in Kindle's native smart-card area.

This is a modification of the existing reader interface, not a replacement
reader and not a KOReader plugin.

## What It Changes

- Replaces the visible `Note` action with an `AI` action.
- Opens an embedded card with `Explain`, `Words`, `Wiki`, `Why`, and `Deep`.
- Starts `Explain` automatically for a one-tap normal workflow.
- Uses Gemini first for a normal press and DeepSeek Pro first for a long press.
- Cancels an older stream and its retries when a different mode is selected.
- Keeps model and fallback status visible while answers stream.
- Moves Kindle's native Translation action into the visible toolbar.
- Sets the native Translation target to Russian.

The project does not replace the Kindle reader, modify books, decrypt content,
or bypass Kindle DRM.

## Status and Compatibility

| Component | Status |
| --- | --- |
| Target hardware | Kindle Oasis, likely KOA3 / KOA3W32C |
| Target firmware | 5.18.2 only |
| Stable device checkpoint | AI Note v25 was installed and used on the target device |
| Current device build | v26 was installed on 2026-07-27 |
| v26 runtime result | Large AI geometry works; outside-tap Undo is broken because Swing reports the visible Kindle card as hidden |
| Current local candidate | v27 uses Kindle's actual SmartCard state, removes redundant repaint/layout work, and rechecks late geometry |
| Other Kindle models or firmware | Untested and unsupported |

Version tags preserve the development checkpoints. GitHub Releases contain
release notes only; patched Amazon JARs are not distributed. Build outputs must
be produced locally from original JARs obtained from a compatible device that
you own or are authorized to modify.

## Data and Privacy

An AI request may send the following to the configured provider:

- selected text;
- up to roughly 5,000 words before and 3,000 words after the selection;
- book title, authors, language, chapter, and Kindle positions;
- the selected AI mode.

Depending on configuration, requests go to Google Gemini and/or OpenRouter. The
project contains no analytics or developer telemetry, but the external AI
providers receive the request content under their own terms.

The Kindle also stores the latest prompt, response, and error as plaintext files
on USB-visible storage. API keys are stored in a local plaintext properties
file. Do not use the AI feature with confidential documents, and treat the
Kindle storage as sensitive.

Read [Privacy and data flow](docs/PRIVACY_AND_DATA_FLOW.md) before configuring
an API key.

## What Is and Is Not in This Repository

Tracked:

- original project Java source and patch logic;
- bytecode patch/build scripts;
- KUAL install and rollback scripts;
- tests, architecture notes, operations documentation, and version history.

Not tracked or distributed:

- Amazon firmware or original Amazon JARs;
- generated or modified Amazon JARs;
- Kindle books or decrypted book content;
- API keys, device dumps, logs, or reading history;
- private Xray/VLESS configuration;
- the Nosebleed exploit or its implementation.

The local ignored path
`from-kindle/native-reader-research/jars/` is where an authorized user may place
JARs copied from their own device for local building. Those files are not part
of the Git repository.

## Requirements

This project assumes:

- a compatible user-owned Kindle Oasis running firmware 5.18.2;
- an existing jailbreak and KUAL/MRPI environment;
- recoverable backups of the original reader JARs;
- local access to those device-owned JARs;
- Java with `javac --release 8` support and Python 3;
- your own Gemini and/or OpenRouter credentials;
- acceptance of the device, account, provider, warranty, and legal risks.

The repository does not jailbreak a Kindle. The historical setup path is
documented at [Jailbreak and device setup](docs/JAILBREAK_AND_DEVICE_SETUP.md),
but it is not an exploit guide.

## Build Model

The source tooling creates local patched outputs from the user's own original
JARs:

```text
your compatible Kindle JARs
        +
project source and bytecode patcher
        |
        v
local ReaderSDK-impl and Reader-plugin build outputs
        |
        v
hash-pinned KUAL install with rollback backups
```

See [Operations and rollback](docs/OPERATIONS_AND_ROLLBACK.md) for the current
build shape and recovery procedure. Do not install a build when its expected
hashes, source checkpoint, or target firmware do not match.

## Repository Map

```text
README.md
    Product purpose, status, safety boundary, and navigation.

PATCHLOG.md
    Chronological experiments, device results, hashes, and rollback history.

patch-work/src/
    Original project Java source and integration code.

patch-work/scripts/
    Local bytecode patch and build tooling.

patch-work/tests/
    Host-side tests for patch shape, geometry, request handling, and session logic.

kual-extension/
    KUAL menus and guarded install/rollback scripts.

docs/
    Architecture, privacy, device setup, operations, and historical cleanup notes.
```

## Safety and Legal Boundary

This repository is published for interoperability research, personal study, and
modification of devices the user owns or is authorized to modify.

- It does not include Amazon firmware, proprietary Amazon JARs, books, DRM
  circumvention, decryption, protected-content extraction, or a jailbreak
  exploit.
- It does not grant rights to Amazon software, firmware, services, trademarks,
  or third-party models.
- It is not affiliated with, endorsed by, or supported by Amazon.
- Kindle and Amazon are trademarks of Amazon.com, Inc. or its affiliates.
- Device modification may break functionality, affect warranty or service
  access, and create recovery risk.
- Laws, contracts, and platform terms vary by jurisdiction. Repository
  availability is not legal advice or a representation that every use is
  permitted.

Use only your own lawfully obtained files and devices, preserve original
backups, and evaluate your local obligations before proceeding.

## License

The author's original project code, scripts, tests, and documentation are
source-available under the
[PolyForm Noncommercial License 1.0.0](LICENSE.md).

This allows noncommercial use, study, modification, and distribution under the
license terms. It is not the MIT License and is not an OSI-approved open-source
license because commercial use is restricted.

Commercial use, paid redistribution, resale, or commercial integration requires
a separate written license. Contact:
[Telegram @Lemelson](https://t.me/Lemelson).

The license applies only to rights the licensor owns. Amazon firmware, JARs,
APIs, trademarks, and other third-party material are excluded. See
[NOTICE.md](NOTICE.md).

## Contact and Responsible Disclosure

- Project and commercial licensing: [Telegram @Lemelson](https://t.me/Lemelson)
- GitHub: [@Lemelson](https://github.com/Lemelson)
- Security reports: follow [SECURITY.md](SECURITY.md)

Please do not open a public issue containing API keys, device dumps, private book
text, VPN configuration, or other sensitive material.

## Documentation

- [Privacy and data flow](docs/PRIVACY_AND_DATA_FLOW.md)
- [Native reader AI architecture](docs/NATIVE_READER_AI_ARCHITECTURE.md)
- [Operations and rollback](docs/OPERATIONS_AND_ROLLBACK.md)
- [Jailbreak and device setup](docs/JAILBREAK_AND_DEVICE_SETUP.md)
- [Patch history](PATCHLOG.md)
- [Historical device cleanup](docs/DEVICE_CLEANUP_2026-07-24.md)
