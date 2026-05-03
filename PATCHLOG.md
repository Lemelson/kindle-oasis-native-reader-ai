# Kindle Native Reader Patch Log

Device target: Kindle Oasis firmware 5.18.2.

## Safety / rollback

Original reader jar is preserved in multiple places:

- Mac copy: `/Users/lemelson/kindle-oasis-jailbreak/native-reader-research/from-kindle/native-reader-research/jars/Reader-plugin.jar`
- Kindle USB/root-access backup: `/mnt/us/native-reader-research/backups/Reader-plugin.jar.orig.20260504-013002`

Rollback over SSH:

```sh
mntroot rw
cp /mnt/us/native-reader-research/backups/Reader-plugin.jar.orig.20260504-013002 /opt/amazon/ebook/lib/Reader-plugin.jar
chown root:root /opt/amazon/ebook/lib/Reader-plugin.jar
chmod 0644 /opt/amazon/ebook/lib/Reader-plugin.jar
sync
reboot
```

## Hashes

- Original `Reader-plugin.jar`: `b5d1c9b6e580e89bb70758e42b7f465049e404c97b278b8920ba8e8dc6b0f64b`
- Patch v1 `Reader-plugin.jar`: `d232b2c407621e79f63b05d39dc08221ee60d6d65c955ad41f60e607232ec62d`
- Patch v2 `Reader-plugin.jar`: `aa991071fa84ceb5dc097e2f63abc6c1e7f210176fe31bbd9e5d09af70630609`

## Patch v1

Changed `com.amazon.ebook.booklet.reader.plugin.share.ShareSelectedTextAction` so the visible Share slot becomes `Trans`.

Behavior observed on device: pressing `Trans` removed the command buttons but did not open the bottom Translation card. The normal More menu -> Translation path still worked.

Probable cause: calling `SelectionDialog.c(card)` from the top action refreshes/closes the command bar but does not follow the same smart-card opening path used by the overflow Translation action.

## Patch v2

Still replaces the Share slot with `Trans`, but opens `TRANSLATOR_CARD` through the internal `SmartCardDialogImpl.a(SelectionDialogCard, boolean)` path using reflection:

- find `TRANSLATOR_CARD` from `Registry.MM()` card providers
- access selection dialog field `bBS` (`SmartCardDialogImpl`)
- call protected `bN(false)` to compute the layout flag
- call `SmartCardDialogImpl.a(card, compactFlag)`

Patch v2 has been built locally and copied to:

- Mac artifact: `/Users/lemelson/kindle-oasis-jailbreak/native-reader-research/patch-work/patched-v2/Reader-plugin.jar`
- Kindle USB-visible artifact: `/Volumes/Kindle/native-reader-research/patched/Reader-plugin.v2.jar`

Patch v2 has not yet been installed to `/opt/amazon/ebook/lib/Reader-plugin.jar` because USBNet/SSH is currently not reachable.

## KUAL installer

Because USBNet was not reachable after the first reboot, a KUAL extension was added:

- Kindle path: `/mnt/us/extensions/nativepatch/`
- Menu: `Native Reader Patch`
- Action: `Install Share->Trans v2`
- Action: `Rollback Reader Plugin`

The installer applies `Reader-plugin.v2.jar`, stores a pre-v2 backup, writes `/mnt/us/native-reader-research/nativepatch.log`, and reboots. The rollback action restores the original `Reader-plugin.jar.orig.20260504-013002` backup and reboots.
