# Security Policy

## Supported Scope

This is an experimental, firmware-specific research project rather than a
supported consumer product. Security reports are accepted for the current
`main` branch and the latest tagged source checkpoint.

The maintainer cannot provide support for Amazon firmware, jailbreak tools,
third-party AI providers, VPN software, or devices and firmware outside the
documented target.

## Reporting a Vulnerability

Do not open a public issue when a report contains:

- API keys, tokens, credentials, or private URLs;
- Kindle device dumps, serial numbers, logs, or configuration;
- book text, private documents, prompts, or model responses;
- unpublished exploit details;
- personal or third-party data.

Send a concise private report to
[Telegram @Lemelson](https://t.me/Lemelson). Include:

1. the affected source commit or tag;
2. the target Kindle model and firmware without a serial number;
3. the security impact;
4. minimal reproduction steps using redacted data;
5. whether the issue is already public.

Please allow reasonable time for confirmation and remediation before public
disclosure.

## Secret Handling

The repository must never contain real Gemini/OpenRouter keys, private
Xray/VLESS configuration, device dumps, reading logs, original Amazon JARs, or
generated patched Amazon JARs.

Before contributing:

- use only placeholder credentials;
- inspect the staged diff;
- run a secret scanner against the staged changes and Git history;
- verify that ignored local device material is not staged;
- redact paths or metadata that identify a personal device.

If a real credential is committed or published, revoke it immediately. Removing
it in a later commit does not remove it from Git history.

## Device Safety

System-JAR changes can make the native reader unusable. Preserve verified
original backups and a recovery path before installing any build. Do not treat
host-side compilation or tests as proof of on-device safety.
