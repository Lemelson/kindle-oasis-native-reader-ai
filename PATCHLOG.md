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
- Patch v3 `Reader-plugin.jar`: `d978e1e97c7d2042176aa8d12e40e24582d335770e6141a14dffb9659fe1f907`
- AI Note v1 `ReaderSDK-impl.jar`: `3b921ef24f9b0f1c8b61cf5abcbbce620dd30c5a56e38b1308c5e1c9a3912499`
- Original `translator.jar`: `32fec956281a3dcb6cd6895ce7c58308f425766c0bdbe0edd8b420181ff61052`
- Russian target default v1 `translator.jar`: `3fd5e1ef3c27620fcc577035b9eca1da43eea3e2357d6628048c3ab74eda3259`
- Russian target default v2 `translator.jar`: `53397351c254817305065cd9d245f9d65ab94f8408a2c9200823ea074defd711`

## Stable checkpoint

The confirmed working native reader state before AI changes is:

- Git commit: `87780e9 Checkpoint stable Trans Russian patch`
- Git tag: `stable-trans-ru-v3-confirmed`

That state keeps the visible `Trans` button and Russian target behavior, without any AI/Note changes.

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

## Patch v3

Keeps the visible `Trans` button behavior from patch v2, but before opening the native translator card it calls the original Amazon `TranslatorStorage.mA("ru")` through reflection.

This uses Kindle's own storage path for target-language persistence and leaves `translator.jar` original. It is intended to avoid the crashes and selection-dialog suppression caused by patching translator classes directly.

## AI Note v1

Replaces the add-note action class in `ReaderSDK-impl.jar` so the visible `Note` slot becomes `AI`.

Behavior intended:

- `Trans` remains handled by `Reader-plugin.v3.jar`.
- `AI` opens a small prompt chooser: Explain, Translate, Words, Grammar, Custom.
- The selected text is collected through Kindle's own `Book.r(start, end)` path.
- Book title, authors, language, start/end positions, and best-effort nearby text are included in the OpenRouter prompt.
- The OpenRouter provider is configured by `/mnt/us/native-reader-research/ai-assistant.properties`.
- Requests use `provider.allow_fallbacks=false` and default to `deepseek/deepseek-v4-flash`.
- Responses are shown in a non-fullscreen Swing dialog over the book.

Build notes:

- Helper classes are compiled from `patch-work/src/com/amazon/ebook/booklet/reader/plugin/notesandmarks/`.
- `AnnotationsSelectionDialogProvider$AddNoteAction.class` is bytecode-patched from the original class by `patch-work/scripts/build_ai_note_patch.py`, because it extends a non-static Kindle inner class and the Java source compiler otherwise emits the wrong constructor descriptor.

Rollback:

- KUAL -> Native Reader Patch -> `Rollback AI Note Patch` restores the original `/opt/amazon/ebook/lib/ReaderSDK-impl.jar`.
- KUAL -> Native Reader Patch -> `Install Trans + Russian target v3` returns to the stable Trans-only behavior.

## Translator Russian Target Default v1

Changed `com.amazon.ebook.booklet.reader.plugin.translator.ui.TranslatorStorage.avp()` in `translator.jar` so a missing saved target language, or a saved `en` target, returns `ru` instead of letting the translator default to English.

This keeps the source-language behavior unchanged. The source can still be detected by the native translator; only the initial target language changes to Russian. If the Kindle has already saved another non-English target language for a given book, the saved value still wins.

Device result: unsafe. This full-class replacement caused Application Error / KPPMainApp crash while opening books. Do not install v1 again.

## Translator Russian Target Default v2

Uses a smaller binary patch against the original Amazon classes instead of replacing `TranslatorStorage`.

Changes:

- `TranslationView.auW()` keeps its original control flow, but the branch that used the current book language as target now reads `default.device.language` from `TranslatorResources`.
- `TranslatorResources` changes `default.device.language` from `en` to `ru`.

This should preserve English labels and source detection while making the initial target language Russian.

Device result: unsafe. This did not crash, but the selection menu stopped appearing after text selection. Do not install v2 again.

Built artifact:

- v1 Mac artifact: `/Users/lemelson/kindle-oasis-jailbreak/native-reader-research/patch-work/patched-translator-ru/translator.jar`
- v2 Mac artifact: `/Users/lemelson/kindle-oasis-jailbreak/native-reader-research/patch-work/patched-translator-ru-v2/translator.jar`
- v2 Kindle USB-visible artifact: `/Volumes/Kindle/native-reader-research/patched/translator.ru.v2.jar`

## KUAL installer

Because USBNet was not reachable after the first reboot, a KUAL extension was added:

- Kindle path: `/mnt/us/extensions/nativepatch/`
- Menu: `Native Reader Patch`
- Action: `Install Share->Trans v2`
- Action: `Install Trans + Russian target v3`
- Action: `Install AI Note v1`
- Action: `Install stable Trans only`
- Action: `Rollback Reader Plugin`
- Action: `Rollback AI Note Patch`
- Action: `Rollback Translator Jar`

The installers store pre-change backups, write `/mnt/us/native-reader-research/nativepatch.log`, and reboot. The reader rollback restores the original `Reader-plugin.jar.orig.20260504-013002` backup. The translator rollback restores `/mnt/us/native-reader-research/jars/translator.jar`. `Install stable Trans only` installs the working `Reader-plugin.v2.jar` while restoring the original `translator.jar`.
