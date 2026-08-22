# Privacy and Data Flow

This document describes what the current native-reader AI patch can read, send,
and store. Review it before adding an API key or using the patch with personal
documents.

## Summary

The project has no developer analytics or telemetry. It does, however, send
reading content to the AI providers configured by the device owner and stores
diagnostic snapshots on USB-visible Kindle storage.

Do not use the AI action with confidential, legally protected, unpublished, or
otherwise sensitive material unless you accept the provider and local-storage
risks.

## Request Flow

```text
Kindle selection
    |
    +-- selected fragment
    +-- before/after reading context
    +-- title, authors, language, chapter, positions
    +-- selected AI mode
    |
    v
local prompt construction on Kindle
    |
    +--> Google Gemini, when configured
    |
    +--> OpenRouter and the selected model, when configured/fallback is used
    |
    v
streamed response shown in the native reader card
```

v28, v29, and v30 connect directly to the selected providers. The previous v27 release could
route requests through a local Xray process; that changed the network route but
did not make request content private from the selected AI provider.

## Content Collected for a Request

The current patch can include:

- the exact selected fragment;
- approximately 5,000 words before the fragment;
- approximately 3,000 words after the fragment;
- up to 60,000 characters for each extracted context side;
- book title and authors;
- book language;
- current chapter, when available;
- Kindle start and end positions;
- the requested mode and provider/model-routing instructions.

The after-context is labeled for silent disambiguation to reduce spoilers, but
it is still transmitted to the provider.

## External Recipients

Depending on local configuration:

- Google receives Gemini requests;
- OpenRouter receives fallback or explicitly selected OpenRouter requests;
- the model provider selected through OpenRouter may process the request.

Provider retention, training, account, region, and abuse-monitoring rules are
controlled by those providers, not this repository. Review their current terms
and privacy controls before use.

## Local Files

The default project paths can include:

```text
/mnt/us/native-reader-research/ai-assistant.properties
/mnt/us/native-reader-research/ai-last-prompt.txt
/mnt/us/native-reader-research/ai-last-response.txt
/mnt/us/native-reader-research/ai-last-error.txt
/mnt/us/native-reader-research/ai-diagnostics-last.txt
/mnt/us/native-reader-research/nativepatch.log
/mnt/us/native-reader-research/gemini-quota.properties
```

Risks:

- `ai-assistant.properties` contains plaintext API keys;
- the last-prompt file can contain selected text, surrounding book context, and
  metadata;
- the response and error files can contain private reading information;
- diagnostic and install logs can reveal configuration or operational details;
- `/mnt/us/` is visible over USB when the Kindle is connected.

These files are intentionally excluded from Git, but exclusion from Git does not
protect them from someone with device or USB access.

## Safer Operating Practices

1. Use provider keys dedicated to this device and restrict them where the
   provider supports restrictions.
2. Never reuse a high-value personal or production credential.
3. Keep real configuration out of Git, issues, screenshots, and support
   messages.
4. Avoid the AI action on confidential or unpublished documents.
5. Review and remove prompt/response/error snapshots after sensitive use.
6. Remove API keys before selling, lending, servicing, or disposing of the
   Kindle.
7. Redact book text, paths, credentials, and device identifiers before sharing
   diagnostics.
8. Assume anyone with unlocked USB access can read the files under `/mnt/us/`.

## Repository Publication Boundary

The public Git repository and its history were scanned for common secret
patterns. At the time of the public-readiness audit, no real API key, device
serial/DSN, UUID, MAC address, public VPN endpoint, private key, Telegram token,
book file, device dump, original Amazon JAR, or generated Amazon JAR was found in
Git history.

The example OpenRouter key is an explicit non-working placeholder. Historical
v27 references to localhost `127.0.0.1:18080` describe a device-local proxy
endpoint, not a public server.

Public issues and pull requests must not include private runtime files. Use the
private reporting process in [SECURITY.md](../SECURITY.md) for sensitive
findings.
