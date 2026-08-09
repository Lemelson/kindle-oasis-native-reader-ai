import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
JAVA = (
    ROOT
    / "patch-work/src/com/amazon/ebook/booklet/reader/plugin/notesandmarks"
)


class DirectSerbiaReleaseTest(unittest.TestCase):
    def test_current_runtime_has_no_vpn_lifecycle_or_proxy_path(self):
        self.assertFalse((JAVA / "AiVpnSession.java").exists())

        for name in (
            "AiAssistantController.java",
            "AiAssistantCard.java",
            "AiConnectivityDiagnostics.java",
            "AiProviderChainClient.java",
            "OpenRouterClient.java",
            "OpenRouterConfig.java",
        ):
            source = (JAVA / name).read_text(encoding="utf-8")
            self.assertNotIn("AiVpnSession", source, name)
            self.assertNotIn("vpnEnabled", source, name)
            self.assertNotIn("ensureVpnProxy", source, name)
            self.assertNotIn("stopVpnProxy", source, name)
            self.assertNotIn("java.net.Proxy", source, name)

    def test_current_config_and_kual_menu_expose_direct_mode_only(self):
        example = (ROOT / "ai-assistant.example.properties").read_text(
            encoding="utf-8"
        )
        self.assertNotIn("vpn_", example)

        menu_path = ROOT / "kual-extension/nativepatch/menu.json"
        menu = json.loads(menu_path.read_text(encoding="utf-8"))
        items = menu["items"][0]["items"]
        names = [item["name"] for item in items]
        actions = [item["action"] for item in items]

        self.assertIn("Install AI Note v28 direct Serbia", names)
        self.assertIn("./bin/install-ai-note-v28.sh", actions)
        self.assertNotIn("Start AI VPN Proxy", names)
        self.assertNotIn("Stop AI VPN Proxy", names)

    def test_v28_installer_disables_legacy_vpn_setting_and_can_restore_it(self):
        installer_path = (
            ROOT / "kual-extension/nativepatch/bin/install-ai-note-v28.sh"
        )
        installer = installer_path.read_text(encoding="utf-8")

        self.assertIn('CONFIG="/mnt/us/native-reader-research/ai-assistant.properties"', installer)
        self.assertIn('set_prop vpn_enabled false', installer)
        self.assertIn("CONFIG_BACKUP=", installer)
        self.assertIn("restore_config_after_failure", installer)
        self.assertIn("Reader-plugin.v28.jar", installer)
        self.assertIn("ReaderSDK-impl.ai-v28.jar", installer)

    def test_build_outputs_are_versioned_v28(self):
        build = (
            ROOT / "patch-work/scripts/build_ai_note_patch.py"
        ).read_text(encoding="utf-8")

        self.assertIn("classes-ai-v28", build)
        self.assertIn("patched-ai-v28", build)
        self.assertIn("ReaderSDK-impl.ai-v28.jar", build)
        self.assertIn("Reader-plugin.v28.jar", build)


if __name__ == "__main__":
    unittest.main()
