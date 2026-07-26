import importlib.util
import io
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT_PATH = ROOT / "patch-work/scripts/build_ai_note_patch.py"
SPEC = importlib.util.spec_from_file_location("build_ai_note_patch", SCRIPT_PATH)
BUILD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILD)


class ContentPanelBookTapPatchTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with zipfile.ZipFile(BUILD.ORIGINAL_JAR, "r") as source:
            cls.original = source.read(BUILD.CONTENT_PANEL_MOUSE_CLASS + ".class")

    def test_replaces_only_the_native_book_tap_close_call(self):
        patched = BUILD.patch_content_panel_book_tap_class(self.original)

        self.assertNotEqual(self.original, patched)
        self.assertEqual(
            1,
            BUILD.count_content_panel_book_tap_hooks(patched),
        )

    def test_fails_closed_when_expected_mouse_handler_shape_changes(self):
        cp = BUILD.ConstantPool(self.original)
        code_start, code_end = BUILD.method_code_range(
            self.original,
            cp,
            BUILD.BOOK_TAP_METHOD,
            BUILD.BOOK_TAP_METHOD_DESC,
        )
        pattern = BUILD.content_panel_book_tap_pattern(self.original, cp)
        offset = self.original[code_start:code_end].find(pattern)
        self.assertGreaterEqual(offset, 0)
        changed = bytearray(self.original)
        changed[code_start + offset] = 0

        with self.assertRaisesRegex(ValueError, "expected exactly 1"):
            BUILD.patch_content_panel_book_tap_class(bytes(changed))


class ReproducibleJarTest(unittest.TestCase):
    def test_generated_entries_are_byte_for_byte_reproducible(self):
        first = io.BytesIO()
        second = io.BytesIO()

        with zipfile.ZipFile(first, "w", zipfile.ZIP_DEFLATED) as target:
            BUILD.write_generated_entry(target, "example/Generated.class", b"class bytes")
        with zipfile.ZipFile(second, "w", zipfile.ZIP_DEFLATED) as target:
            BUILD.write_generated_entry(target, "example/Generated.class", b"class bytes")

        self.assertEqual(first.getvalue(), second.getvalue())


if __name__ == "__main__":
    unittest.main()
