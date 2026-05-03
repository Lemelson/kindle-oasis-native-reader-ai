#!/usr/bin/env python3
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


TRANSLATION_VIEW = Path("com/amazon/ebook/booklet/reader/plugin/translator/ui/TranslationView.class")
TRANSLATOR_RESOURCES = Path("com/amazon/ebook/booklet/reader/plugin/translator/ui/resources/TranslatorResources.class")

# TranslationView.auW(), offsets 93..118 in that method. The original branch
# maps the current book language through BookMetadata.eU(true) and mv(...).
ORIGINAL_VIEW_BRANCH = bytes.fromhex(
    "2a"              # aload_0
    "2a"              # aload_0
    "b4008e"          # getfield #142 aKd
    "b901370100"      # invokeinterface #311 ReaderSDK.mO
    "b901380100"      # invokeinterface #312 Book.mx
    "04"              # iconst_1
    "b600ea"          # invokevirtual #234 BookMetadata.eU
    "b700ca"          # invokespecial #202 mv
    "4e"              # astore_3
    "a7000a"          # goto 126
)

# Replacement: load ResourceBundle local #1, get "default.device.language",
# store it in local #3, then jump to the original join point.
PATCHED_VIEW_BRANCH = bytes.fromhex(
    "2b"              # aload_1
    "1218"            # ldc #24 "default.device.language"
    "b6011a"          # invokevirtual #282 ResourceBundle.getString
    "4e"              # astore_3
    "a7001a"          # goto 126
) + (b"\x00" * (len(ORIGINAL_VIEW_BRANCH) - 10))

# TranslatorResources default.device.language entry. This changes the resource
# value from constant #83 "en" to constant #133 "ru".
ORIGINAL_RESOURCE_DEFAULT = bytes.fromhex(
    "1247"            # ldc #71 "default.device.language"
    "53"              # aastore
    "59"              # dup
    "04"              # iconst_1
    "1253"            # ldc #83 "en"
    "53"              # aastore
)
PATCHED_RESOURCE_DEFAULT = bytes.fromhex(
    "1247"            # ldc #71 "default.device.language"
    "53"
    "59"
    "04"
    "1285"            # ldc #133 "ru"
    "53"
)


def patch_exact(data: bytes, original: bytes, patched: bytes, label: str) -> bytes:
    count = data.count(original)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return data.replace(original, patched, 1)


def run(args, cwd):
    subprocess.run(args, cwd=cwd, check=True)


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: patch_translator_default_target.py ORIGINAL_JAR OUTPUT_JAR", file=sys.stderr)
        return 2

    source = Path(sys.argv[1]).resolve()
    output = Path(sys.argv[2]).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, output)

    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        run(["jar", "xf", str(output), str(TRANSLATION_VIEW), str(TRANSLATOR_RESOURCES)], work)

        view_path = work / TRANSLATION_VIEW
        resources_path = work / TRANSLATOR_RESOURCES
        view_path.write_bytes(
            patch_exact(view_path.read_bytes(), ORIGINAL_VIEW_BRANCH, PATCHED_VIEW_BRANCH, "TranslationView.auW")
        )
        resources_path.write_bytes(
            patch_exact(
                resources_path.read_bytes(),
                ORIGINAL_RESOURCE_DEFAULT,
                PATCHED_RESOURCE_DEFAULT,
                "TranslatorResources.default.device.language",
            )
        )

        run(["jar", "uf", str(output), str(TRANSLATION_VIEW), str(TRANSLATOR_RESOURCES)], work)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
