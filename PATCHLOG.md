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
- Original `translator.jar`: `32fec956281a3dcb6cd6895ce7c58308f425766c0bdbe0edd8b420181ff61052`
- Russian target default `translator.jar`: `3fd5e1ef3c27620fcc577035b9eca1da43eea3e2357d6628048c3ab74eda3259`

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

Patch v2 was installed through KUAL and verified on-device: the visible `Trans` button opens the native bottom Translation panel.

## Translator Russian Target Default

Changed `com.amazon.ebook.booklet.reader.plugin.translator.ui.TranslatorStorage.avp()` in `translator.jar` so a missing saved target language, or a saved `en` target, returns `ru` instead of letting the translator default to English.

This keeps the source-language behavior unchanged. The source can still be detected by the native translator; only the initial target language changes to Russian. If the Kindle has already saved another non-English target language for a given book, the saved value still wins.

Built artifact:

- Mac artifact: `/Users/lemelson/kindle-oasis-jailbreak/native-reader-research/patch-work/patched-translator-ru/translator.jar`
- Kindle USB-visible artifact: `/Volumes/Kindle/native-reader-research/patched/translator.ru.jar`

## KUAL installer

Because USBNet was not reachable after the first reboot, a KUAL extension was added:

- Kindle path: `/mnt/us/extensions/nativepatch/`
- Menu: `Native Reader Patch`
- Action: `Install Share->Trans v2`
- Action: `Install Russian target default`
- Action: `Rollback Reader Plugin`
- Action: `Rollback Translator Jar`

The installers store pre-change backups, write `/mnt/us/native-reader-research/nativepatch.log`, and reboot. The reader rollback restores the original `Reader-plugin.jar.orig.20260504-013002` backup. The translator rollback restores `/mnt/us/native-reader-research/jars/translator.jar`.
