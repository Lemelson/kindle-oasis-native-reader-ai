import re
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
INSTALLER = ROOT / "kual-extension/nativepatch/bin/install-ai-note-v30.sh"
BUILD = ROOT / "patch-work/scripts/build_ai_note_patch.py"


class V30ReleaseTest(unittest.TestCase):
    def test_build_and_installer_are_versioned_v30(self):
        build = BUILD.read_text(encoding="utf-8")
        installer = INSTALLER.read_text(encoding="utf-8")

        self.assertIn("classes-ai-v30", build)
        self.assertIn("patched-ai-v30", build)
        self.assertIn("ReaderSDK-impl.ai-v30.jar", build)
        self.assertIn("Reader-plugin.v30.jar", build)
        self.assertIn("ReaderSDK-impl.ai-v30.jar", installer)
        self.assertIn("Reader-plugin.v30.jar", installer)
        self.assertIn("install-ai-note-v30 done", installer)

    def test_model_persistence_checks_are_single_shell_commands(self):
        installer = INSTALLER.read_text(encoding="utf-8")
        self.assertIn(
            '[ "$(awk -F= \'$1 == "model" { print $2; exit }\' "$CONFIG")" = "deepseek/deepseek-v4-flash-0731" ]',
            installer,
        )
        self.assertNotRegex(
            installer,
            re.compile(r'\[\s*"\$\(awk[^\n]+\)"\s*=\s*\n\s*"deepseek/'),
        )

    def test_installer_passes_shell_syntax_check(self):
        result = subprocess.run(
            ["sh", "-n", str(INSTALLER)],
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
