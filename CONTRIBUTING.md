# Contributing

Contributions are welcome when they preserve the project's safety, privacy, and
third-party distribution boundaries.

## Before Opening a Change

Open an issue first for:

- support for another Kindle model or firmware;
- changes to bytecode offsets or proprietary integration points;
- provider, logging, privacy, or credential-handling changes;
- installer or rollback changes;
- licensing or distribution changes.

Never attach Amazon JARs, firmware, books, device dumps, API keys, private
network configuration, or reading logs.

## Pull Request Expectations

A pull request should:

1. explain the user-visible behavior and exact compatibility assumptions;
2. keep unrelated changes out of the diff;
3. include host-side tests where they can prove the behavior;
4. distinguish compilation, host tests, installation, and physical-device
   runtime validation;
5. preserve rollback and fail-closed behavior;
6. update README, architecture, operations, privacy, or patch-log documentation
   when their contracts change;
7. use placeholders for all credentials and personal paths;
8. contain only material the contributor has the right to submit.

## Validation

At minimum:

- run `git diff --check`;
- compile against JARs obtained from your own compatible device;
- run the focused host tests for the changed code;
- build from a clean production class directory;
- inspect the output JAR contents for accidental test or private files;
- run a staged and full-history secret scan;
- verify shell syntax and JSON for changed KUAL files.

On-device claims require a real compatible Kindle test. If physical validation
was not performed, say so prominently.

## License

By contributing, you agree that your contribution is submitted under the
project's [PolyForm Noncommercial License 1.0.0](LICENSE.md) and that the
required notice and third-party exclusions in [NOTICE.md](NOTICE.md) apply.

Commercial licensing remains separate. Contact
[Telegram @Lemelson](https://t.me/Lemelson) before contributing work that cannot
be licensed under these terms.
