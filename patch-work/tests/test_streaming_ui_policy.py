import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
JAVA = (
    ROOT
    / "patch-work/src/com/amazon/ebook/booklet/reader/plugin/notesandmarks"
)


class StreamingUiPolicyTest(unittest.TestCase):
    def test_gemini_and_deepseek_expose_live_request_thinking_and_answer_phases(self):
        card = (JAVA / "AiAssistantCard.java").read_text(encoding="utf-8")
        chain = (JAVA / "AiProviderChainClient.java").read_text(encoding="utf-8")

        self.assertIn("STREAM_UPDATE_INTERVAL_MS = 600L", card)
        self.assertIn("thinking.setLength(0);", card)
        self.assertIn("currentProviderLabel", card)
        self.assertIn('": запрос отправлен…"', chain)
        self.assertIn('"Gemini: запрос принят. Ожидаю thinking…"', chain)
        self.assertIn('"DeepSeek Flash: запрос принят. Ожидаю thinking…"', chain)
        self.assertIn('\\"includeThoughts\\":true', chain)


if __name__ == "__main__":
    unittest.main()
