# Kindle Native Reader Patch Log

Device target: Kindle Oasis firmware 5.18.2.

## Safety / rollback

Original reader jar is preserved in multiple places:

- Local ignored copy: `from-kindle/native-reader-research/jars/Reader-plugin.jar`
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

- Local artifact: `patch-work/patched-v2/Reader-plugin.jar`
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

## AI Note v12

Refines v11 into a direct action-row UX:

- changes the patched add-note slot from `AI` to `Explain`
- injects additional native selection actions: `Words`, `Recall`, `Why`, and `Deep`
- each action opens the AI response card directly, without an intermediate prompt chooser
- rewrites the card as a response-only smart-card with title, streamed response, and `Close`
- switches final answer display from plain `JTextArea` to `JTextPane` with a safe Markdown subset
- supports bold, italic, headings, inline code, and normal list text without using HTML
- keeps v11 streaming, 1.5s UI throttling, retry behavior, logs, `reasoning.effort=high`,
  `reasoning_exclude=false`, `max_tokens=3000`, and `provider.allow_fallbacks=false`

The action injection is done by replacing `AnnotationsSelectionDialogProvider.getActions()`
with a small bytecode stub that calls `AiPresetAction.syncActions(...)`, while keeping
the original `update()` logic for Kindle highlight/delete/note state.

Built artifact:

- Mac artifact: `patch-work/patched-ai-v12/ReaderSDK-impl.ai-v12.jar`
- SHA-256: `f419216bd20b76b2b3fcb4856c739d75ce77c1425591745e78d1b06549387ca3`

Device result: usable but not the desired two-row toolbar. Amazon's native
selection toolbar kept only a few actions visible and moved the rest into the
overflow menu instead of wrapping into a stable second row.

## AI Note v13

Reverts the risky v12 action-list injection and moves to a popup-menu UX:

- restores `AnnotationsSelectionDialogProvider.class` to the original Kindle
  class in the v13 jar
- patches only `AnnotationsSelectionDialogProvider$AddNoteAction` so the native
  `Note` slot becomes one visible `AI` action
- pressing `AI` opens a compact `SelectionDialogCard` with a two-column prompt
  menu: `Explain`, `Words`, `Recall`, `Why`, `Deep`, and `Close`
- choosing a prompt opens the existing response-only AI card directly
- keeps v12 Markdown rendering, retries, logs, streaming, 1.5s UI throttling,
  `reasoning.effort=high`, `reasoning_exclude=false`, `max_tokens=3000`, and
  `provider.allow_fallbacks=false`
- updates the reader plugin label from `Trans` to `Translate` while preserving
  the same native Amazon Translation-card behavior and Russian target default

Built artifacts:

- Mac artifact: `patch-work/patched-ai-v13/ReaderSDK-impl.ai-v13.jar`
- SHA-256: `3b93bebd0a7a9c6d4848996089a204a28fd004fc0b1f791ec077169dea220e4e`
- Mac artifact: `patch-work/patched-v4/Reader-plugin.jar`
- SHA-256: `ee547187d985b8b0391bf02165b093c6d9a3a6d799eee4e2b2e399b9bd8e8811`

Device result: the popup-menu experiment was not the desired reading workflow.
It separated mode selection from the response area and was less usable than the
older embedded v11 card.

## AI Note v14

Current recommended build. This is an upgrade from the successful v11 UI rather
than a continuation of the v12/v13 UX experiments.

- restores the v11-style embedded AI smart-card: prompt buttons at the top and
  one lower output area for status, thinking, retries, and final answer
- removes the v12 action-row helper and the v13 popup-menu card from the v14 jar
- starts Flash immediately on a short tap
- starts DeepSeek V4 Pro on a long press of about 800 ms
- keeps `deepseek/deepseek-v4-flash` as the default model
- adds `deepseek/deepseek-v4-pro` as the Pro model
- adds `xiaomi/mimo-v2.5-pro` as the final Pro-failure fallback
- makes retry/fallback stages explicit in the output area
- preserves OpenRouter `reasoning.effort=high`, streaming, prompt/response/error
  snapshots, and `provider.allow_fallbacks=false`

Retry behavior:

- Flash primary gets 6 attempts with delays `0.5s, 0.5s, 0.5s, 2s, 2s`
- if Flash fails completely, DeepSeek V4 Pro gets 3 fallback attempts at 2s
- Pro primary gets 6 attempts with the same primary delays
- if Pro fails completely, Pro gets 4 extra attempts at 2s
- if those fail, Xiaomi MiMo V2.5 Pro gets 3 final fallback attempts at 2s

Built artifacts:

- Mac artifact: `patch-work/patched-ai-v14/ReaderSDK-impl.ai-v14.jar`
- SHA-256: `7c076c866c79d03423ae33d0e77a24f1d9bd381e83192891b05ae200cd84d44d`
- Mac artifact: `patch-work/patched-v3/Reader-plugin.jar`
- SHA-256: `d978e1e97c7d2042176aa8d12e40e24582d335770e6141a14dffb9659fe1f907`

## AI Note v15

Adds optional on-demand VPN proxy support for OpenRouter requests while keeping
the v14/v11-style embedded UI.

- adds `vpn_enabled`, `vpn_proxy_host`, `vpn_proxy_port`, `vpn_start_script`,
  and `vpn_stop_script` config keys
- when enabled, starts a local HTTP proxy before each OpenRouter attempt
- routes only the AI HTTP request through the local proxy, not the whole Kindle
- stops the proxy after the attempt finishes, which avoids a continuously
  running VPN process for normal reading
- adds KUAL entries for `Install AI Note v15 VPN`, `Start AI VPN Proxy`, and
  `Stop AI VPN Proxy`

The real VLESS/Xray config is intentionally not tracked in git because it
contains subscription credentials.

Gemini API testing note: direct Google connectivity from the Mac timed out, and
the provided VLESS endpoint later returned TLS resets for Google API tests.
Gemini fallbacks were therefore not added to the patch.

Built artifacts:

- Mac artifact: `patch-work/patched-ai-v15/ReaderSDK-impl.ai-v15.jar`
- SHA-256: `3e03feadcec71cfe665ea45f2327f4b42f9cb29a49a211c05b7659f1158d33eb`
- Mac artifact: `patch-work/patched-v3/Reader-plugin.jar`
- SHA-256: `d978e1e97c7d2042176aa8d12e40e24582d335770e6141a14dffb9659fe1f907`

## AI Note v16

Adds an optional in-card connectivity diagnostics mode on top of v15.

- adds `diagnostics_enabled`, `diagnostic_attempts`, `gemini_api_key`,
  `gemini_base_url`, and `gemini_models` config keys
- when diagnostics are enabled, pressing any AI preset shows a short provider
  report in the normal lower AI output block instead of sending a reading prompt
- checks OpenRouter `/api/v1/models`
- checks Gemini `models.list`
- checks Gemini `generateContent` for the configured model list, with at most
  three attempts per model to avoid aggressive rate-limit probing
- adds KUAL entries for `Install AI Note v16 Diagnostics`,
  `Enable AI Diagnostics`, and `Disable AI Diagnostics`

Default Gemini diagnostic model list:

- `gemini-3.5-flash`
- `gemini-3-flash-preview`
- `gemini-3.1-pro-preview`
- `gemini-3.0-flash`

The UK VLESS/Xray config and Gemini API key are intentionally not tracked in git.

Built artifacts:

- Mac artifact: `patch-work/patched-ai-v16/ReaderSDK-impl.ai-v16.jar`
- SHA-256: `7a2bbf7897c657f084efc2cfa9d37c64250393ae4005911e7a5ab7152bd318a7`
- Mac artifact: `patch-work/patched-v3/Reader-plugin.jar`
- SHA-256: `d978e1e97c7d2042176aa8d12e40e24582d335770e6141a14dffb9659fe1f907`

## AI Note v17

Adds Gemini API key rotation to diagnostics mode.

- adds `gemini_api_keys` as a comma-separated key list
- keeps `gemini_api_key` as a single-key fallback
- tries each configured key once per Gemini model in diagnostics mode
- labels attempts as `key 1/3`, `key 2/3`, etc. without printing key values
- uses explicit display labels for Gemini model ids to avoid ambiguous output
- updates the diagnostic model order to 3.1, 3.0, 3.5, then 3 preview

Built artifacts:

- Mac artifact: `patch-work/patched-ai-v17/ReaderSDK-impl.ai-v17.jar`
- SHA-256: `903d28fd510e6a2508fc70795e9497120cdaaccecc76166c3fb720fd63110339`
- Mac artifact: `patch-work/patched-v3/Reader-plugin.jar`
- SHA-256: `d978e1e97c7d2042176aa8d12e40e24582d335770e6141a14dffb9659fe1f907`

## AI Note v18

Adds a persisted diagnostics report for provider checks.

- writes the full in-card diagnostics output to
  `/mnt/us/native-reader-research/ai-diagnostics-last.txt`
- keeps Gemini keys out of logs and git
- adds KUAL entry `Install AI Note v18 Gemini Report`

Built artifacts:

- Mac artifact: `patch-work/patched-ai-v18/ReaderSDK-impl.ai-v18.jar`
- SHA-256: `890bb1633aaa64005c0a12ee62745435d9abda59941fe14e305b9688234332bd`
- Mac artifact: `patch-work/patched-v3/Reader-plugin.jar`
- SHA-256: `d978e1e97c7d2042176aa8d12e40e24582d335770e6141a14dffb9659fe1f907`

## AI Note v19

Narrows Gemini diagnostics to Gemini 3.5 Flash only.

- removes Gemini Pro, Gemini 3.0 Flash, and Gemini 3 Flash Preview from the
  default diagnostics model list
- suppresses the `models.list` response body so unrelated model names do not
  appear in the UI
- adds `gemini_thinking_level`, defaulting to `medium`
- sends Gemini `thinkingConfig.thinkingLevel=medium`
- uses the existing `max_tokens` value for Gemini `maxOutputTokens`; on the
  current Kindle config this is `3000`

Local Mac smoke test with the active key, `gemini-3.5-flash`,
`thinkingLevel=medium`, and `maxOutputTokens=3000` returned HTTP 200 with `OK`.

Built artifacts:

- Mac artifact: `patch-work/patched-ai-v19/ReaderSDK-impl.ai-v19.jar`
- SHA-256: `2a5736e17e2a66bbb475279dfe8fbd80bcdd799c726b6c5e3701d6c1fda1587d`
- Mac artifact: `patch-work/patched-v3/Reader-plugin.jar`
- SHA-256: `d978e1e97c7d2042176aa8d12e40e24582d335770e6141a14dffb9659fe1f907`

## AI Note v20

Changes the normal AI provider chain and VPN lifetime.

- normal tap chain: Gemini 3.5 Flash gets 4 attempts; if it fails, DeepSeek V4
  Pro gets 5 attempts; if that fails, Gemini 3.5 Flash gets one final attempt
- long press chain: DeepSeek V4 Pro gets 5 attempts first; if it fails,
  Gemini 3.5 Flash gets 4 attempts
- DeepSeek V4 Flash is no longer used by the new normal request path
- Gemini diagnostics remain available through `diagnostics_enabled=true`, but
  normal mode uses the provider chain above
- VPN is started once at the beginning of the AI request chain and stopped once
  after the chain succeeds or finally fails
- Gemini uses `gemini-3.5-flash`, `thinkingLevel=medium`, and the shared
  `max_tokens` value as `maxOutputTokens`

Built artifacts:

- Mac artifact: `patch-work/patched-ai-v20/ReaderSDK-impl.ai-v20.jar`
- SHA-256: `3276a5bbaa55df97f19605da91b7f137ffe92fb184e8a63e21b373d8472b4e51`
- Mac artifact: `patch-work/patched-v3/Reader-plugin.jar`
- SHA-256: `d978e1e97c7d2042176aa8d12e40e24582d335770e6141a14dffb9659fe1f907`

## AI Note v21

Adds Gemini key rotation and local quota tracking.

- supports multiple Gemini keys in `gemini_api_keys`
- stores only key hashes, successful request timestamps, and cooldown timestamps
  in `/mnt/us/native-reader-research/gemini-quota.properties`
- counts only successful Gemini answers against the local daily request tracker
- skips a key after 20 successful Gemini answers inside a rolling 24-hour
  window
- marks a key on 1-hour cooldown when Gemini returns quota/rate-limit style
  errors such as HTTP 429 / RESOURCE_EXHAUSTED / quota exceeded
- short tap chain remains Gemini 3.5 Flash first, then DeepSeek V4 Pro
- long press chain is DeepSeek V4 Pro first, then Gemini 3.5 Flash, then
  DeepSeek V4 Flash

Google's official Gemini rate-limit documentation says Gemini limits are
measured across RPM, TPM, and RPD, that exceeding any can trigger a rate-limit
error, and that RPD quotas reset at midnight Pacific time. The local tracker
uses a conservative rolling 24-hour window plus a 1-hour cooldown for quota
errors because Kindle clock/timezone and account-side enforcement can differ.

Built artifacts:

- Mac artifact: `patch-work/patched-ai-v21/ReaderSDK-impl.ai-v21.jar`
- SHA-256: `203ed993deb039967d3c700c87b3e2966aaddccc7968a78f1f8977d68e06e3ec`
- Mac artifact: `patch-work/patched-v3/Reader-plugin.jar`
- SHA-256: `d978e1e97c7d2042176aa8d12e40e24582d335770e6141a14dffb9659fe1f907`

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

- v1 local artifact: `patch-work/patched-translator-ru/translator.jar`
- v2 local artifact: `patch-work/patched-translator-ru-v2/translator.jar`
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

## AI Note v22 Wiki, chapter context, VPN fix, larger max_tokens

Built on v21. Changes:

- Prompt buttons are now `Explain`, `Words`, `Wiki`, `Why`, `Deep`. The `Recall`
  button is replaced by `Wiki`. `Wiki` is an encyclopedia-style identify mode:
  it leads with real-world knowledge about a highlighted person, term, place,
  event, work, or concept, and falls back to before-context only when the item
  is specific to the book. This absorbs the old `Recall` use case (book-internal
  referents) while covering the more common "who/what is this real thing"
  intent for non-fiction reading. Output scaffold: `Что это / Кратко / Здесь`.
- System prompt gains one non-fiction line: for a highlighted person, term,
  place, event, work, or concept, answer like a short encyclopedia entry and do
  not withhold public factual information as if it were a spoiler. The existing
  narrative spoiler policy is unchanged.
- Current chapter/section is now extracted from the book's own table of contents
  instead of the hardcoded `unknown`. Path: `Book.mA()` (TableOfContents) →
  `TableOfContents.f(Position)` (resolve the selection start to its entry) →
  `TableOfContentsEntry.getLabel()`. Wrapped in try/catch; on any failure the
  prompt still says `unknown`. This uses the book's native TOC, so it works
  generically for any book that has chapters, with no per-book code.
- VPN error message clarified; there is no direct-connection fallback. The
  "VPN" is Xray: `start-ai-vpn.sh` runs `xray run` with an HTTP inbound on
  `127.0.0.1:18080` and a VLESS outbound abroad, so port 18080 is the local
  entrance to the tunnel (this is what the code calls the "proxy" — same thing
  as the VPN, not a separate hop). From Russia a direct request to
  Gemini/OpenRouter is blocked, so falling back to a direct connection is
  pointless. When the tunnel does not come up, the request now fails with a
  clear message ("VPN (Xray) did not come up on 127.0.0.1:18080. Check the
  connection and try again.") instead of the old "VPN proxy did not become
  ready" wrapped as an "OpenRouter error". The generic error line in the AI card
  changed from "OpenRouter error after retries:" to "AI request failed:" so a
  VPN failure no longer reads as an OpenRouter failure. `openConnection` again
  routes through the tunnel whenever `vpn_enabled=true`.
- `max_tokens` default raised from 3000 to 5000 in `OpenRouterConfig` and in
  `ai-assistant.example.properties`. One knob feeds both the OpenRouter
  `max_tokens` and the Gemini `maxOutputTokens`. The on-device
  `/mnt/us/native-reader-research/ai-assistant.properties` was updated to 5000.

Build:

```sh
rm -rf patch-work/classes-ai-v22 patch-work/patched-ai-v22
mkdir -p patch-work/classes-ai-v22
CP=$(printf '%s:' from-kindle/native-reader-research/jars/*.jar)
javac --release 8 -cp "$CP" -d patch-work/classes-ai-v22 \
  $(find patch-work/stubs patch-work/src/com/amazon/ebook/booklet/reader/plugin/notesandmarks -name '*.java' -print)
python3 patch-work/scripts/build_ai_note_patch.py
```

Built artifact:

- Mac artifact: `patch-work/patched-ai-v22/ReaderSDK-impl.ai-v22.jar`
- SHA-256: `a052b8459fef79fa8887e4f6e61b21a833969d08a7c645cb55fdcea6bb55ebaf`

KUAL install action (new menu entry, keeps v21 available for rollback):

```text
KUAL -> Native Reader Patch -> Install AI Note v22 Wiki + VPN fix
```

Device deployment (USB): jar copied to
`/mnt/us/native-reader-research/patched/ReaderSDK-impl.ai-v22.jar`, install
script to `/mnt/us/native-reader-research/extensions/nativepatch/bin/`, and the
extension `menu.json` refreshed.

## AI Note v23 Gemini 3.6 Flash

Built on v22. Changes:

- replaces the Gemini model ID `gemini-3.5-flash` with the stable
  `gemini-3.6-flash`
- updates Gemini provider-chain status text, persisted response labels, and
  connectivity-diagnostics labels to `Gemini 3.6 Flash`
- migrates the on-device `gemini_models` property during v23 installation and
  backs up the previous config first
- keeps `thinkingLevel=medium`, the existing quota rotation, retry order, VPN
  lifecycle, prompts, and DeepSeek fallbacks unchanged
- adds a separate v23 KUAL installer while keeping v22 available for rollback

Build:

```sh
rm -rf patch-work/classes-ai-v23 patch-work/patched-ai-v23
mkdir -p patch-work/classes-ai-v23
CP=$(printf '%s:' from-kindle/native-reader-research/jars/*.jar)
javac --release 8 -cp "$CP" -d patch-work/classes-ai-v23 \
  $(find patch-work/stubs patch-work/src/com/amazon/ebook/booklet/reader/plugin/notesandmarks -name '*.java' -print)
python3 patch-work/scripts/build_ai_note_patch.py
```

Built artifact:

- Mac artifact: `patch-work/patched-ai-v23/ReaderSDK-impl.ai-v23.jar`
- SHA-256: `f8389a2526a6a7f4a75cd0e14c6e5248d3cffdeb8c18303842526e2a39c184ae`

KUAL install action:

```text
KUAL -> Native Reader Patch -> Install AI Note v23 Gemini 3.6
```

Device deployment: intentionally deferred. No v23 files were copied to the
mounted Kindle.

## AI Note v24 Gemini streaming, one-tap start, and VPN reliability

Built on the uninstalled v23 source. Changes:

- keeps `gemini-3.6-flash` and raises `thinkingLevel` from `medium` to `high`
- raises the shared output limit from `5000` to `6750` tokens (exactly 1.35x);
  the same value feeds Gemini `maxOutputTokens` and OpenRouter `max_tokens`
- switches Gemini from `generateContent` to
  `streamGenerateContent?alt=sse`, forwarding final-answer chunks to the
  existing throttled e-ink UI while filtering any response part marked
  `thought=true`
- automatically starts the normal `Explain` request after the AI card opens,
  reducing the normal selection workflow to one tap on `AI`
- disables preset buttons while a request is active, preventing concurrent
  requests from racing the shared Xray process
- removes the `netstat` readiness shortcut that could mistake a `TIME_WAIT`
  socket for a live proxy; an existing managed but unready Xray process is
  terminated and restarted, with a bounded wait and forced-stop fallback
- keeps only current v22, candidate v24, diagnostics, VPN, stable translation,
  and rollback actions in the KUAL menu

Device deployment: intentionally deferred. v22 remained installed.

Built artifact:

- Mac artifact: `patch-work/patched-ai-v24/ReaderSDK-impl.ai-v24.jar`
- SHA-256: `71e879929c7c49a09cc83bfaad082c219caecb0c491ceabd6dcb200102ab9647`

## AI Note v25 instant session and latest-request-wins

Built on the uninstalled v24 source. Changes:

- keeps the existing 8pt answer text and embedded smart-card location
- starts Xray warm-up immediately when `AI` is selected, in parallel with
  before/after context extraction
- keeps that Xray session alive across Explain, Words, Wiki, Why, and Deep;
  it stops in a background thread only when the AI card closes
- automatically starts Explain exactly as v24 did
- replaces disabled-in-flight buttons with latest-request-wins cancellation:
  another short or long press disconnects the previous HTTP stream, interrupts
  retry waits, prevents future fallbacks, and starts only the selected request
- ignores repeat taps for the same active mode/model so accidental double or
  triple taps do not create duplicate API calls
- preserves long press as the explicit DeepSeek V4 Pro-first gesture
- highlights the active mode with bold text and a gray button background
- shortens user-visible provider statuses while keeping technical failures in
  the diagnostic logs
- keeps Gemini 3.6 Flash first for normal taps, DeepSeek Pro first for long
  presses, Gemini/OpenRouter SSE streaming, `thinkingLevel=high`, and the shared
  `6750` output-token limit

Device deployment was initially deferred, then v25 was installed on 2026-07-24.

Built artifact:

- Mac artifact: `patch-work/patched-ai-v25/ReaderSDK-impl.ai-v25.jar`
- SHA-256: `fd9c02e517b6b04f1969f682a666fa81715b52bbee48ad321cba326da1da2124`

Device result: installed and used on the connected Oasis. The install log
records the same SDK SHA-256.

## AI Note v26 large card and outside-tap Undo

Built on the installed v25 source. Changes:

- doubles the AI-only smart-card height, capped to the physical screen, while
  preserving its width
- applies the target bounds to both Kindle's stored dialog geometry and the
  already-posted live frame; Translate retains Amazon's stock geometry
- arms a one-shot session only after AI or Translate opens successfully
- intercepts the single firmware-5.18.2 book-tap close call that otherwise uses
  `SelectionDialog.bQ(false)`
- reuses Kindle's enabled `DeleteHighlightAction` for the exact native Undo
  behavior, with `bQ(true)` as the safe selection-clear fallback
- leaves ordinary selections, hidden/stale cards, drag handling, and the
  existing quick-highlight `bQ(true)` branch unchanged
- adds a fail-closed bytecode shape/offset assertion and JVM tests for geometry,
  native Undo, fallback, hidden-card, and one-shot behavior
- adds a hash-pinned v26 installer that replaces only the Reader plugin and SDK
  jars, plus two-jar normal and emergency rollback

Device-staged artifacts:

- `patch-work/patched-ai-v26/ReaderSDK-impl.ai-v26.jar`
- SHA-256: `33e2a68fc9813c043ad6958be2495ed358a41dd1786e6b205975e7ea60b1ca1a`
- `patch-work/patched-ai-v26/Reader-plugin.v26.jar`
- SHA-256: `ec70bfbcdf276b071437e9fe8e1a4e50b68c2f1c03a2bf2eca6012edc3136466`

An earlier local SDK package accidentally included host test classes and is not
a canonical build. Patched Amazon JARs are no longer distributed through GitHub
Releases. v26 was installed on 2026-07-27. The approximately 2x AI geometry
works, but tapping the book does not invoke Undo and leaves the highlight
selected.

## AI Note v27 reliable outside dismiss

Built from the device-tested v26 source. Root cause and changes:

- device logs consistently contained `auto undo armed` but never
  `auto undo matched outside book tap`
- the v26 matcher incorrectly trusted Swing `SelectionDialogCard.isShowing()`;
  Kindle's visible SmartCard can return false through that generic Swing API
- v27 stores both the selection dialog and card, then matches the book tap
  against Kindle's native `SelectionDialog.VN()` state and the exact current
  card returned by `SmartCardDialog.YO()`
- stale, closed, replaced, unarmed, or unrelated cards retain Amazon's original
  `bQ(false)` behavior
- the native enabled `DeleteHighlightAction` remains the preferred exact Undo,
  with `bQ(true)` as fallback
- removes explicit frame `invalidate()` and `repaint()` plus a redundant dialog
  validation
- performs one deferred bounds verification; an already-correct frame is a
  no-op, while a late stock-size reset is corrected once

Network behavior:

- v27 is the last VPN-based release
- with the installed `vpn_enabled=true` configuration, v27 starts the local
  Xray tunnel before Gemini or OpenRouter requests and stops it when the AI card
  closes
- the repository contains only the generic tunnel launch/stop scripts; the
  private Xray endpoint configuration and all API keys remain device-local and
  are not included in Git

Performance evidence from the device log:

- successful v26 action-to-card times were approximately 2.0-2.2 seconds
- the final recorded v25 opening took approximately 3.0 seconds
- v26 resize code itself took approximately 10-27 milliseconds

The logs therefore do not show a Java-side slowdown. The larger E-Ink redraw
can still feel slower, so v27 removes redundant redraw/layout requests.

Regression coverage:

- visible Kindle SmartCard with Swing `isShowing() == false` invokes Undo
- closed, replaced, and unarmed cards preserve original behavior
- missing native Undo clears through `bQ(true)`
- the armed session is one-shot
- late stock bounds are corrected exactly once
- already-expanded bounds do not trigger another layout
- no explicit full-frame repaint is requested

Built artifacts:

- `patch-work/patched-ai-v27/ReaderSDK-impl.ai-v27.jar`
- SHA-256: `780cc1e4909e17f1931597c53baa9f368e7628d1ae43d675c83fc8787c781a2d`
- `patch-work/patched-ai-v27/Reader-plugin.v27.jar`
- SHA-256: `e43a30553e5fc1f28b83493da222772de14b4e80a91fc8120bee9bfd39c80f4e`

Device deployment: both v27 artifacts and the hash-pinned KUAL installer were
copied to the Kindle USB partition and verified byte-for-byte on 2026-07-27.
The installer completed successfully on 2026-07-27 and was re-run successfully
on 2026-08-03; the system currently runs v27.
