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
    Gemini/OpenRouter provider chain.
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
select text -> AI -> embedded card -> Explain starts automatically
```

Opening the card starts `Explain` with Gemini 3.7 Flash, making the normal path
one tap. Short-tapping another mode immediately cancels the active HTTP stream
and pending retries, then uses the normal Gemini-first chain. Long-pressing a
mode for about 800 ms cancels the active request and starts with DeepSeek V4
Flash instead. The active mode button uses bold text and a gray background.

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

Xray warm-up starts before context capture. Context extraction and tunnel
startup therefore run in parallel; the provider request begins when both are
ready.

## Provider Configuration

Configuration path on Kindle:

```text
/mnt/us/native-reader-research/ai-assistant.properties
```

Current important values:

```text
model=deepseek/deepseek-v4-flash-0731
base_url=https://openrouter.ai/api/v1/chat/completions
temperature=0.2
max_tokens=6750
timeout_ms=45000
reasoning_effort=high
reasoning_exclude=false
gemini_models=gemini-3.7-flash
gemini_thinking_level=high
```

The shared `max_tokens` value is sent as OpenRouter `max_tokens` and Gemini
`maxOutputTokens`. v29 requests Gemini thought summaries with
`includeThoughts=true`; those summaries and OpenRouter reasoning are displayed
only during the thinking phase. They are cleared when final-answer streaming
starts. OpenRouter reasoning is returned when `reasoning_exclude=false`.

For a quieter production build, set:

```text
reasoning_exclude=true
```

That keeps reasoning enabled but prevents reasoning text from being returned.

## Request Payload

The current OpenRouter request uses:

```json
{
  "model": "deepseek/deepseek-v4-flash-0731",
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
  "max_tokens": 6750,
  "stream": true
}
```

`allow_fallbacks=false` is important because it prevents OpenRouter from silently
routing to another model. The Gemini/DeepSeek order is handled explicitly by
this patch and shown in the UI.

## Retry And Fallback

Normal tap order:

```text
Gemini 3.7 Flash: up to 4 attempts
DeepSeek V4 Flash 0731: up to 4 attempts
```

Long-press order:

```text
DeepSeek V4 Flash 0731: up to 5 attempts
Gemini 3.7 Flash: up to 4 attempts
```

Retry/fallback status remains visible in the card.

Only one provider-chain request is active globally. A new short or long press
disconnects the previous HTTP connection, interrupts any retry wait, and
invalidates its callbacks before starting the new request. Repeated taps for the
same active mode and model are ignored; changing the mode or changing short tap
to long press replaces the request.

## Streaming Behavior

The current build uses `HttpURLConnection` and reads Server-Sent Events from
both Gemini `streamGenerateContent` and OpenRouter.

Expected stream order from testing:

```text
Gemini: final-answer chunks only
OpenRouter: reasoning chunks, then content chunks
```

UI behavior:

1. Show request-sent and provider-accepted states for Gemini and DeepSeek.
2. Stream Gemini thought summaries or OpenRouter reasoning during thinking.
3. Clear the thinking view when the first final-answer token arrives.
4. Stream final-answer chunks directly, without a `Final answer:` prefix.
5. Throttle e-ink text updates to at most once every 600 ms.
6. Cache the completed final answer for the selected mode and selection.
7. Keep the existing 8pt output font.

## Direct Networking

v29 opens ordinary direct HTTPS connections:

```text
AI pressed -> capture context
request -> direct HTTPS to Gemini or OpenRouter
AI card closes -> cancel the active HTTP request
```

The current runtime has no Xray start/stop lifecycle, HTTP proxy construction,
or VPN configuration fields. v27 remains in Git history as the final VPN-based
release for users who explicitly need that older route.

Reasoning text is capped visually to the most recent 12000 characters so very
long thinking traces do not grow the UI without bound.

## Prompt Rules

The prompt asks for:

- Russian answers
- the most important relevant information first
- secondary context, nuance, examples, or interesting facts in descending order
  of usefulness
- model-chosen structure and detail instead of fixed response templates
- no generic preamble, question repetition, or padding after the explanation is
  complete
- focus on the selected fragment
- no future plot spoilers beyond before context and selected fragment
- plain-text readable formatting
- no dependency on HTML

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
and wants an engaging encyclopedia-style mini-article or strong search result.
It leads with a clear identification and real-world knowledge, may add memorable
facts, and connects the item to the book when that genuinely helps without
forcing the connection or revealing unread information. This replaces the
earlier `Recall` mode and absorbs its book-internal use case.

`Why`

Use when the words are understood but the function of the fragment is unclear.
This mode gives surrounding context more weight and explains why the reader may
have stopped at this exact point.

`Deep`

Use when the passage feels dense, important, philosophical, symbolic, or
difficult. This mode chooses its own depth and structure without a word limit or
mandatory sections.

## Known Tradeoffs

- v14 keeps the v11-style embedded AI card because it is clearer on-device than
  the v12 action row and v13 popup-menu experiments.
- The streaming path uses direct `HttpURLConnection` rather than Amazon's
  internal HTTP helper, so device runtime testing remains required.
- Gemini thoughts stay hidden. OpenRouter reasoning can still be noisy when a
  fallback is used; set `reasoning_exclude=true` to hide it.
