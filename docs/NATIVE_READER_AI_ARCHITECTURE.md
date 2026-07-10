# Native Reader AI Architecture

This document explains the current native Kindle reader patch at a product and
implementation level.

## Product Logic

Most highlights happen for a few repeated reading reasons:

- the meaning is unclear
- the wording is unclear
- a person, concept, or event needs recall
- the sentence is understood but its function is unclear
- the passage feels important and deserves a deeper explanation

The AI interface therefore uses universal reading intentions, not book genres:

```text
Explain | Words | Wiki | Why | Deep
```

The model is expected to infer the text type and situation silently, then answer
in Russian.

## Native Reader Integration

The patch modifies the native Amazon reader rather than adding a separate app.

Two main jar files matter:

```text
Reader-plugin.jar
    Selection toolbar patch. Adds the visible Trans action.

ReaderSDK-impl.jar
    AI smart-card patch. Replaces the Note action with AI and implements the
    OpenRouter integration.
```

The translator jar is kept original in the stable working path. Instead of
replacing translator classes, the working translation patch calls Kindle's own
translator storage path to set the target language to Russian.

## Trans Button

The native Kindle selection menu already has Translation under the overflow menu.
The first successful native patch moved that action into the visible toolbar.

Current behavior:

```text
select text -> Trans -> native Amazon Translation card
```

Before opening the translator card, the patch sets target language to:

```text
ru
```

The source language remains Amazon's normal detection behavior.

## AI Button

The visible `Note` slot is replaced with the `AI` action.

Current behavior:

```text
select text -> AI -> embedded SelectionDialogCard with Explain / Words / Wiki / Why / Deep
```

Short-tapping a mode sends the request immediately with `model`. Long-pressing
a mode for about 800 ms sends the request with `pro_model` instead and shows an
immediate Pro status line in the lower output area.

The card is not a separate fullscreen application. It opens through the same
native smart-card path used by other Kindle reader cards.

v14 intentionally returns to the v11-style card: prompt buttons are inside the
AI card, and status/reasoning/retry/final answer text streams into the lower
plain `JTextArea`. v12 action-row injection and v13 popup-menu were useful
experiments, but did not match the desired on-device workflow.

## Context Capture

When AI is opened, the patch captures:

- selected text
- before context
- after context
- title
- authors
- book language
- start position
- end position

Current context targets:

```text
before_context: about 5000 words before the selected fragment
after_context: about 3000 words after the selected fragment
context cap: 60000 characters per extracted context block
```

The prompt treats context asymmetrically:

- `before_context` is assumed to be already read
- `selected_fragment` is the main truth
- `after_context` is marked as silent disambiguation only

This is meant to reduce spoiler risk in chronological or plot-driven books.

If context extraction fails, the prompt still includes the selected fragment and
marks the missing side as unavailable.

## OpenRouter Configuration

Configuration path on Kindle:

```text
/mnt/us/native-reader-research/ai-assistant.properties
```

Current important values:

```text
model=deepseek/deepseek-v4-flash
pro_model=deepseek/deepseek-v4-pro
pro_fallback_model=xiaomi/mimo-v2.5-pro
base_url=https://openrouter.ai/api/v1/chat/completions
temperature=0.2
max_tokens=3000
timeout_ms=45000
reasoning_effort=high
reasoning_exclude=false
```

`reasoning_exclude=false` is intentional because we want streaming
reasoning text to appear while the model is thinking.

For a quieter production build, set:

```text
reasoning_exclude=true
```

That keeps reasoning enabled but prevents reasoning text from being returned.

## Request Payload

The current OpenRouter request uses:

```json
{
  "model": "deepseek/deepseek-v4-flash",
  "provider": {
    "allow_fallbacks": false
  },
  "reasoning": {
    "effort": "high",
    "exclude": false
  },
  "messages": [
    { "role": "system", "content": "..." },
    { "role": "user", "content": "..." }
  ],
  "temperature": 0.2,
  "max_tokens": 3000,
  "stream": true
}
```

`allow_fallbacks=false` is important because it prevents OpenRouter from silently
routing to another model. Any Flash/Pro fallback is handled explicitly by this
patch and shown in the UI.

## Retry And Fallback

Each primary request gets up to 6 attempts. Retry delays are:

```text
0.5s, 0.5s, 0.5s, 2s, 2s
```

If all Flash attempts fail, the client performs 3 final fallback attempts with
`pro_model`, delayed 2 seconds each.

If all Pro attempts fail, the client first performs 4 extra attempts with
`pro_model`, delayed 2 seconds each. If those also fail, the client performs 3
final fallback attempts with `pro_fallback_model` (`xiaomi/mimo-v2.5-pro` by
default), also delayed 2 seconds each.

The response view keeps a visible line such as
`Extra retry with DeepSeek V4 Pro (...)`,
`Final fallback retry with DeepSeek V4 Pro (...)`, or
`Final fallback retry with Xiaomi MiMo V2.5 Pro (...)`.

## Streaming Behavior

The current build uses `HttpURLConnection` and reads Server-Sent Events from
OpenRouter.

Expected stream order from testing:

```text
reasoning chunks first
content chunks after reasoning
```

UI behavior:

1. Show `Thinking:` while reasoning text streams.
2. When final content starts, clear the thinking view.
3. Stream the final answer directly, without a `Final answer:` prefix.
4. Cache the completed final answer for the selected mode and selection.
5. Render the completed answer with the safe Markdown subset.

Reasoning text is capped visually to the most recent 12000 characters so very
long thinking traces do not grow the UI without bound.

## Prompt Rules

The prompt asks for:

- Russian answers
- direct answer first
- focus on the selected fragment
- no future plot spoilers beyond before context and selected fragment
- concise readable formatting
- no dependency on HTML
- safe Markdown subset if the model returns Markdown

Hyphen bullets are allowed.

The client renders common Markdown markers itself. It does not use a browser or
HTML renderer inside the Kindle reader.

## Mode Semantics

`Explain`

Use when the reader does not understand what the fragment means.

`Words`

Use when the wording, idiom, grammar, metaphor, tone, or structure is confusing.
This is different from translation: it explains how the language works.

`Wiki`

Use when the reader highlights a person, term, place, event, work, or concept
and wants a short encyclopedia-style answer. It leads with real-world knowledge
and only falls back to before-context when the item is specific to the book.
This replaces the earlier `Recall` mode and absorbs its book-internal use case.

`Why`

Use when the words are understood but the function of the fragment is unclear.
This mode explains why the fragment matters here.

`Deep`

Use when the passage feels dense, important, philosophical, symbolic, or
difficult. This mode allows a longer and deeper answer.

## Known Tradeoffs

- v14 keeps the v11-style embedded AI card because it is clearer on-device than
  the v12 action row and v13 popup-menu experiments.
- The streaming path is still riskier than v8/v9 because it uses direct
  `HttpURLConnection` streaming instead of Amazon's internal HTTP helper.
- Showing reasoning is useful for experimentation, but it can be noisy and may
  expose model scratch work that is not meant as the final answer.
- For day-to-day reading, a hidden-reasoning build may be calmer:
  `reasoning_exclude=true`.
