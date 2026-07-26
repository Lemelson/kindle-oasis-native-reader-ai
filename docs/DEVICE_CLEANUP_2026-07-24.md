# Kindle cleanup on 2026-07-24

The connected Kindle remained on AI Note v22. AI Note v24 was built locally but
was not copied to or installed on the device.

## Preserved on Kindle

- `ReaderSDK-impl.ai-v22.jar`, matching the retained local v22 artifact:
  `b96bf390aafe90d39c922f41406a26c6e9998f8bf6440a874eadfbe06a15fb52`
- original `Reader-plugin.jar`
- stable translation `Reader-plugin.v2.jar`
- current AI toolbar `Reader-plugin.v3.jar`
- original ReaderSDK, Reader plugin, and translator JARs under
  `native-reader-research/jars`
- `Reader-plugin.jar.orig.20260504-013002` and its checksum for emergency
  rollback
- active v22 installer, diagnostics, VPN controls, stable translation actions,
  and rollback scripts

## Removed from Kindle

- generated AI JARs v1 through v21
- unused `Reader-plugin.v4.jar`
- obsolete AI installers v1 through v21
- two filesystem-recovery copies of the missing v2/v7 installers
- macOS `._*` resource-fork files associated with those artifacts
- dated before-install backup duplicates; only the required original Reader
  plugin rollback copy and checksum remain

The KUAL menu was rewritten to expose only the active v22 install, stable
translation, diagnostics, VPN controls, and rollback actions.

Storage changed approximately as follows:

```text
native-reader-research/patched: 49 MB -> 4.2 MB
extensions/nativepatch:         416 KB -> 128 KB
native-reader-research/backups: 70 MB -> 696 KB
native-reader-research total:   162 MB -> 48 MB
```

## Local cleanup and recovery

Locally, generated AI JARs v1 through v21 and the superseded v23 build were
removed. The active v22 artifact and candidate v24 artifact remain. Generated
class directories were removed.

Source history, build tooling, documentation, and version tags are archived in
the project repository:

```text
https://github.com/Lemelson/kindle-oasis-native-reader-ai
```

Original Amazon JARs, generated JARs, API keys, device dumps, and the private
Xray/VLESS configuration are intentionally excluded from Git. Rebuilding an old
tag therefore requires the preserved local original JARs from this device.
