import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = (
    ROOT
    / "kual-extension/nativepatch/bin/switch-deepseek-v4-flash-0731-high.sh"
)


class SwitchDeepSeekV4Flash0731HighTest(unittest.TestCase):
    def test_switches_to_one_flash_route_and_removes_legacy_pro_settings(self):
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory) / "ai-assistant.properties"
            config.write_text(
                "api_key=secret-value\n"
                "model=deepseek/deepseek-v4-flash\n"
                "pro_model=deepseek/deepseek-v4-pro\n"
                "reasoning_effort=low\n"
                "gemini_models=gemini-3.6-flash\n",
                encoding="utf-8",
            )
            env = os.environ.copy()
            env["AI_CONFIG_PATH"] = str(config)
            env["AI_LOG_PATH"] = str(Path(directory) / "nativepatch.log")

            subprocess.run(["sh", str(SCRIPT)], env=env, check=True)

            self.assertEqual(
                config.read_text(encoding="utf-8"),
                "api_key=secret-value\n"
                "model=deepseek/deepseek-v4-flash-0731\n"
                "reasoning_effort=high\n"
                "gemini_models=gemini-3.6-flash\n",
            )

    def test_adds_missing_model_settings_once_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory) / "ai-assistant.properties"
            config.write_text("api_key=secret-value\n", encoding="utf-8")
            env = os.environ.copy()
            env["AI_CONFIG_PATH"] = str(config)
            env["AI_LOG_PATH"] = str(Path(directory) / "nativepatch.log")

            subprocess.run(["sh", str(SCRIPT)], env=env, check=True)
            subprocess.run(["sh", str(SCRIPT)], env=env, check=True)

            text = config.read_text(encoding="utf-8")
            self.assertEqual(text.count("model="), 1)
            self.assertEqual(text.count("reasoning_effort="), 1)
            self.assertIn("model=deepseek/deepseek-v4-flash-0731\n", text)
            self.assertNotIn("pro_model=", text)
            self.assertNotIn("pro_fallback_model=", text)
            self.assertIn("reasoning_effort=high\n", text)


if __name__ == "__main__":
    unittest.main()
