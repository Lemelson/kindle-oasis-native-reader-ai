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
- AI Note v2 `ReaderSDK-impl.jar`: `b5bb99381b3ef3e86560534a09572ce81e834686d950f8cf7178e496545d3727`
- AI Note v3 `ReaderSDK-impl.jar`: `99f6a9633784bd37ee31d207c898f12b0b01c3babb7d9cd1bd011e44f3a5bfd6`
- AI Note v4 `ReaderSDK-impl.jar`: `4d30bceba7a6e1b386d3ac1269e6f7dd308c54b3cf6c10e4c7fcddbad1585092`
- AI Note v5 `ReaderSDK-impl.jar`: `51f6234fe1e4fbc45e54e96a0734047a95371a308260bc5f2b7aec98d3160bf6`
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

Device result: unsafe. Pressing `AI` did not show the chooser and left reader/KUAL UI actions unresponsive until restart. The likely causes are:

- top-level `JOptionPane` / `JDialog` inside Kindle reader's event loop
- best-effort context extraction via `Book.a(start, 5000)` before the first useful UI/log event

Do not install v1 again.

## AI Note v2

Replaces the add-note action class like v1, but opens an embedded `SelectionDialogCard` through the same `SmartCardDialogImpl.a(SelectionDialogCard, boolean)` path that already works for the visible `Trans` button.

Safety changes from v1:

- no `JOptionPane`
- no `JDialog`
- no distant context extraction
- logs immediately at action entry
- captures selected text only through `Book.r(start, end)`, with title/authors/language/positions as metadata

The card contains preset buttons: Explain, Translate, Words, Grammar, Short, and a small embedded Custom input. Responses are written into the same embedded card.

## AI Note v3

Refines the working v2 card:

- keeps only `Explain`, `Short`, and `Custom`
- removes `Translate`, `Words`, and `Grammar`
- reduces output/input/button fonts to 12pt
- changes Custom input to a single compact row
- tries to release focus from Custom input before sending, so the on-screen keyboard is more likely to disappear
- tries to enlarge the smart-card dialog height by 1.5x through reflection; if the Kindle dialog API refuses this, the resize is skipped and logged
- adds a large best-effort context window: about 5000 words before the selected fragment and 3000 words after it, capped at 60000 characters

Prompt shape:

```text
Context window around the selected fragment:
The Kindle patch tries to include about 5000 words before the selected fragment and about 3000 words after it. The selected fragment is also repeated separately below.

<CONTEXT>
...
</CONTEXT>

<SELECTED_FRAGMENT>
...
</SELECTED_FRAGMENT>

Task:
...

Answer in Russian. Be concise but useful.

Book metadata:
Title: ...
Authors: ...
Book language: ...
Start position: ...
End position: ...
```

`Explain` asks for a concise Russian explanation focused on the selected fragment, with specific behavior for philosophy, self-help, and fiction.

`Short` asks for a 1-3 sentence Russian explanation without an introduction or long summary.

## AI Note v4

Refines v3:

- output font reduced to 6pt
- control fonts reduced to 7pt
- top preset buttons, Custom input, Send button, and Hide button use compact heights
- output switches from plain `JTextArea` to HTML `JEditorPane`
- basic Markdown is rendered: `**bold**` and `__bold__` become bold without showing the marker characters; inline backticks become code-style text
- adds `Hide` next to `Send` to release focus from the Custom field and help close the on-screen keyboard
- OpenRouter requests are retried once after HTTP/network/timeout/empty-response errors, including 429
- last prompt, raw response, and final error are saved without API keys:
  - `/mnt/us/native-reader-research/ai-last-prompt.txt`
  - `/mnt/us/native-reader-research/ai-last-response.txt`
  - `/mnt/us/native-reader-research/ai-last-error.txt`

Prompt changes:

- `Explain` now explicitly explains in the context of the specific book and asks the model to infer genre/situation when it is not obvious.
- `Short` now asks for 3-10 short sentences instead of 1-3.

## AI Note v5

Fixes v4 UI oversizing/undersizing problems:

- returns output rendering to stable plain `JTextArea`
- output font is 8pt
- controls are 10pt
- buttons are 24px high
- Custom row is 28px high
- keeps `Hide`, retry, and prompt/response/error snapshot logging from v4
- strips basic Markdown markers from model output (`**`, `__`, and backticks) instead of trying to render HTML

This intentionally drops rich bold rendering because the Kindle reader rendered the HTML markup literally in v4.

## AI Note v6

Refines the working v5 card:

- removes Custom input, Send, and Hide so the Kindle keyboard is not involved
- shows four preset buttons: `Explain`, `Short`, `Point`, and `Recall`
- keeps the stable plain `JTextArea` output at 8pt
- increases controls to 11pt and preset button height to 32px
- keeps retry and prompt/response/error snapshot logging from v5
- keeps the context window: about 5000 words before the selected fragment and 3000 words after it, capped at 60000 characters

Prompt changes:

- `Explain` is shorter and no longer enumerates philosophy/self-help/fiction rules inside the preset prompt.
- A shared wrapper now tells the model to answer in Russian, adapt to the attached context, book metadata, likely genre/situation, selected fragment, and selected mode.
- `Point` explains what role the selected fragment plays in the argument, scene, or idea.
- `Recall` explains what background the reader should remember to understand the selected fragment.

Built artifact:

- Mac artifact: `patch-work/patched-ai-v6/ReaderSDK-impl.ai-v6.jar`
- SHA-256: `903862eb435479f468e29782da580a352d635d65d18782ed6b7e1c1bd9f6f3f3`

## AI Note v7

Refines v6:

- increases preset button height from 32px to 48px and control font from 11pt to 12pt
- keeps output text at 8pt
- adds per-mode cached answers inside the currently open AI card
- switching from one generated mode to another generated mode shows the existing answer instead of sending a new request
- tapping the already active mode sends a fresh request and replaces that mode's cached answer
- OpenRouter requests now use up to 6 attempts: first immediately, then retries after 0.5s, 0.5s, 0.5s, 2s, and 5s
- the AI card shows compact attempt/retry status text and only shows the final error after all retry attempts fail

Built artifact:

- Mac artifact: `patch-work/patched-ai-v7/ReaderSDK-impl.ai-v7.jar`
- SHA-256: `8ea1c783be16812f1ff323ade5b804ec3a58bcf654164a326a54117717f06791`

## AI Note v8

Refines v7 into a universal reading-intention assistant:

- replaces `Short` and `Point` with five modes: `Explain`, `Words`, `Recall`, `Why`, and `Deep`
- keeps the v7 button sizing, per-mode cache, retry behavior, and plain 8pt output rendering
- upgrades the OpenRouter system prompt into a spoiler-safe Kindle reading assistant prompt
- changes the user prompt from one generic `<CONTEXT>` block to separate `<before_context>`, `<selected_fragment>`, and `<after_context silent="true">` blocks
- treats `before_context` as already read and `after_context` as silent disambiguation only
- adds explicit reader progress, adaptation, mode instruction, and output rule blocks
- keeps model fallback disabled in the OpenRouter payload

The context extractor now attempts to fetch about 5000 words before the selected fragment and about 3000 words after it as separate blocks. If either side fails, the prompt still includes the selected fragment and marks that side as unavailable.

Built artifact:

- Mac artifact: `patch-work/patched-ai-v8/ReaderSDK-impl.ai-v8.jar`
- SHA-256: `cb3cf0e864f3fe8ad8a257265de2b39e439382f6757b9cfe5fad384336f3e72d`

## AI Note v9

Refines v8 OpenRouter/DeepSeek reasoning settings:

- adds explicit OpenRouter reasoning payload:
  - `reasoning.effort = high`
  - `reasoning.exclude = true`
- keeps reasoning hidden from the response while still asking OpenRouter to use reasoning internally
- raises default `max_tokens` from 900 to 3000 so reasoning tokens and the final answer have enough output budget
- keeps `provider.allow_fallbacks = false`, so OpenRouter should not silently route the request to another model
- keeps `temperature = 0.2` in config, although DeepSeek thinking mode may ignore sampling controls
- adds config keys:
  - `reasoning_effort`
  - `reasoning_exclude`

Built artifact:

- Mac artifact: `patch-work/patched-ai-v9/ReaderSDK-impl.ai-v9.jar`
- SHA-256: `26fb8731049ac9c2552f77872a96c774cb0cd6bf06a8a3ae168bd7437a37e5fe`

## AI Note v10

Experimental streaming reasoning build:

- switches OpenRouter requests from `stream=false` to `stream=true`
- switches default `reasoning_exclude` from `true` to `false` so reasoning chunks can be received
- replaces the Amazon reader `HttpRequest` wrapper with a direct `HttpURLConnection` SSE reader
- streams `delta.reasoning` / text `reasoning_details` into the AI card while the model is thinking
- when final `delta.content` starts, the card clears the thinking view and streams the final response without a label prefix
- keeps v7/v9 retry behavior, per-mode cache, `reasoning.effort=high`, `max_tokens=3000`, and `provider.allow_fallbacks=false`
- caps the visible thinking text to the most recent 12000 characters to avoid freezing the Kindle UI on very long reasoning traces
- adds prompt rules asking the model to use plain text only and avoid Markdown bold/italic markers, asterisks, and underscores
- removes the old client-side Markdown stripping fallback, so the final answer is displayed exactly as returned by the model

Risk: this is the first build using Java `HttpURLConnection` on the Kindle instead of Amazon's internal HTTP helper. If TLS/network behavior differs on device, use `Rollback AI Note Patch` or reinstall v9/v8.

Built artifact:

- Mac artifact: `patch-work/patched-ai-v10/ReaderSDK-impl.ai-v10.jar`
- SHA-256: `0029656bc526db9fb0db06b42192c4df75d133cbb153a16a1bb0d54308f2dd8b`

## AI Note v11

Refines v10 streaming UI performance:

- keeps streaming reasoning and streaming final answer behavior from v10
- throttles visible stream updates by time instead of updating on every token
- updates thinking/final text at most once every 1.5 seconds while streaming continues
- still writes the full final answer into the card when the stream completes
- changes the thinking display header from `OpenRouter request ... Thinking:` to just `Thinking:`
- keeps `reasoning.effort=high`, `reasoning_exclude=false`, `max_tokens=3000`, and `provider.allow_fallbacks=false`

The purpose is to reduce Kindle UI lag caused by frequent `JTextArea.setText()`,
`revalidate()`, and repaint calls during long streams.

Built artifact:

- Mac artifact: `patch-work/patched-ai-v11/ReaderSDK-impl.ai-v11.jar`
- SHA-256: `279191e14892fcabb9d9714df60ecc2567817f649a688862e5de9c447bdd800e`

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
