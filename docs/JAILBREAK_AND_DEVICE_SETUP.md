# Jailbreak and Device Setup

This document records what was done to make native Kindle reader patches
possible. It is intentionally operational, not an exploit writeup.

## Goal

The goal was to keep the Kindle on firmware 5.18.2 and install enough homebrew
infrastructure to patch native reader jar files safely.

The final path was:

```text
Kindle Oasis 5.18.2
-> Nosebleed jailbreak
-> hotfix
-> KUAL / MRPI
-> OTA blocker
-> USBNet / root access during development
-> native reader jar patches
```

## Why Firmware 5.18.2 Was Preserved

Firmware 5.18.2 was valuable because public jailbreak routes existed for this
generation and firmware range. Updating to newer firmware could remove that
option.

Operational rule:

```text
Do not update the Kindle unless the jailbreak status of the target firmware is
known and intentionally accepted.
```

## Jailbreak Route

Nosebleed was used because this Kindle did not have Special Offers / ads
enabled. AdBreak was considered only as a fallback because it depends on ads.

High-level Nosebleed flow:

1. Put Kindle in Airplane Mode.
2. Back up USB-visible storage.
3. Delete pending update files from the USB root.
4. Fill most free storage temporarily to reduce OTA update risk.
5. Copy Nosebleed files to the Kindle USB root.
6. Temporarily enable Wi-Fi.
7. Use the Kindle browser to trigger Nosebleed.
8. Install the jailbreak hotfix immediately.

This repository does not contain the Nosebleed exploit. It records only the
post-jailbreak patching project.

## Hotfix

The hotfix was installed immediately after jailbreak. Its purpose is to preserve
jailbreak functionality across reboots and normal Kindle operation.

The user-visible result was a hotfix booklet that was run after the update step.
An `Application Error` after running the hotfix booklet was observed and did not
block later KUAL/MRPI installation.

## KUAL / MRPI

KUAL and MRPI were installed to make Kindle-side package installation and
scripts practical.

KUAL is now used for:

- installing native reader patches
- rolling back native reader patches
- installing or restoring helper components

The current KUAL extension is:

```text
/mnt/us/extensions/nativepatch/
```

USB-visible path on macOS:

```text
/Volumes/Kindle/extensions/nativepatch/
```

## OTA Blocking

OTA update blocking was installed with the `renameotabin` KUAL extension. This
renames Amazon OTA update binaries so the Kindle should not silently install a
new firmware update.

Important:

```text
Before factory reset, manual update, or downgrade workflows, restore OTA
binaries first through KUAL.
```

## Filesystem Access

Normal USB exposes mainly:

```text
/mnt/us/
```

On macOS this appears as:

```text
/Volumes/Kindle/
```

The actual Amazon reader jars live in the internal root filesystem, for example:

```text
/opt/amazon/ebook/lib/Reader-plugin.jar
/opt/amazon/ebook/lib/ReaderSDK-impl.jar
/opt/amazon/ebook/lib/translator.jar
```

Development required root-capable install scripts because ordinary USB access
cannot directly overwrite those system jars.

## What Was Backed Up

The USB-visible Kindle storage was backed up before patching. Original JARs were
also copied into the local, Git-ignored workspace under:

```text
from-kindle/native-reader-research/jars/
```

Install scripts also create timestamped on-device backups in:

```text
/mnt/us/native-reader-research/backups/
```

## Safety Boundary

This project modifies Kindle software behavior on the user's own device. It does
not include DRM bypass code, book decryption workflows, or instructions for
extracting protected book content.

This repository does not distribute the Nosebleed exploit, Amazon firmware,
original or modified Amazon JARs, device dumps, or books. The setup record is
historical and firmware-specific, not a guarantee that the same route remains
available or lawful for every device, account, or jurisdiction.
