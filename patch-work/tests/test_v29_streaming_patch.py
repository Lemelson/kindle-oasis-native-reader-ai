import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class V29StreamingPatchTest(unittest.TestCase):
    def test_build_installer_and_kual_action_are_versioned_v29(self):
        build = (
            ROOT / "patch-work/scripts/build_ai_note_patch.py"
        ).read_text(encoding="utf-8")
        installer_path = (
            ROOT / "kual-extension/nativepatch/bin/install-ai-note-v29.sh"
        )
        menu = json.loads(
            (ROOT / "kual-extension/nativepatch/menu.json").read_text(
                encoding="utf-8"
            )
        )

        self.assertIn("classes-ai-v29", build)
        self.assertIn("patched-ai-v29", build)
        self.assertIn("ReaderSDK-impl.ai-v29.jar", build)
        self.assertIn("Reader-plugin.v29.jar", build)
        self.assertTrue(installer_path.is_file())

        installer = installer_path.read_text(encoding="utf-8")
        self.assertIn("ReaderSDK-impl.ai-v29.jar", installer)
        self.assertIn("Reader-plugin.v29.jar", installer)
        self.assertIn(
            "set_prop model deepseek/deepseek-v4-flash-0731", installer
        )
        self.assertIn("set_prop gemini_models gemini-3.7-flash", installer)
        self.assertIn("set_prop gemini_thinking_level high", installer)
        self.assertIn("set_prop reasoning_effort high", installer)
        self.assertIn("set_prop reasoning_exclude false", installer)
        self.assertIn("remove_prop pro_model", installer)
        self.assertIn("remove_prop pro_fallback_model", installer)
        self.assertIn("install-ai-note-v29 done", installer)
        self.assertIn("reboot", installer)

        items = menu["items"][0]["items"]
        self.assertTrue(
            any(
                item.get("name") == "Install AI Note v29 live thinking"
                and item.get("action") == "./bin/install-ai-note-v29.sh"
                for item in items
            )
        )


if __name__ == "__main__":
    unittest.main()
