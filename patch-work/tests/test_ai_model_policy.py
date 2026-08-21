import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
JAVA = (
    ROOT
    / "patch-work/src/com/amazon/ebook/booklet/reader/plugin/notesandmarks"
)


class AiModelPolicyTest(unittest.TestCase):
    def test_runtime_defaults_use_current_flash_models_and_shared_budget(self):
        config = (JAVA / "OpenRouterConfig.java").read_text(encoding="utf-8")

        self.assertIn(
            'String model = "deepseek/deepseek-v4-flash-0731";',
            config,
        )
        self.assertIn('String geminiModels = "gemini-3.7-flash";', config)
        self.assertIn("int maxTokens = 6750;", config)
        self.assertIn('String reasoningEffort = "high";', config)

    def test_provider_chain_has_only_gemini_and_deepseek_flash_routes(self):
        runtime_sources = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (
                JAVA / "AiAssistantCard.java",
                JAVA / "AiProviderChainClient.java",
                JAVA / "OpenRouterClient.java",
                JAVA / "OpenRouterConfig.java",
            )
        )

        self.assertNotIn("DeepSeek Pro", runtime_sources)
        self.assertNotIn("deepseek-v4-pro", runtime_sources)
        self.assertNotIn("proModel", runtime_sources)
        self.assertNotIn("proRequest", runtime_sources)
        self.assertIn("DeepSeek Flash", runtime_sources)

    def test_mode_prompts_choose_their_own_shape_and_lead_with_relevance(self):
        controller = (JAVA / "AiAssistantController.java").read_text(
            encoding="utf-8"
        )
        preset_prompts = controller[
            controller.index("static String presetPrompt(") :
            controller.index("static String buildUserPrompt(")
        ]
        prompt_sources = [preset_prompts]
        site_prompt_path = ROOT / "prompt-review-site/app/prompt-data.ts"
        if site_prompt_path.is_file():
            prompt_sources.append(site_prompt_path.read_text(encoding="utf-8"))

        for prompt_source in prompt_sources:
            self.assertNotIn("Usually 3-7 short sentences.", prompt_source)
            self.assertNotIn("Limit: 120-250 Russian words.", prompt_source)
            self.assertNotIn("Смысл: [natural Russian meaning]", prompt_source)
            self.assertNotIn("Что это: [one line", prompt_source)
            self.assertNotIn("Зачем это здесь: [main function]", prompt_source)
            self.assertNotIn("Главная идея: [core meaning]", prompt_source)

        self.assertIn(
            "Begin immediately with the most important and relevant information",
            controller,
        )
        self.assertIn(
            "Then add secondary context, nuance, examples, or interesting facts",
            controller,
        )
        self.assertIn("Choose the structure and level of detail yourself.", controller)
        self.assertIn("Do not use a fixed response template.", controller)
        self.assertIn(
            "an informative mini-article or a strong search result",
            preset_prompts,
        )
        self.assertIn(
            "Connect it to the book context when that genuinely helps",
            preset_prompts,
        )

    def test_short_press_is_gemini_first_and_hold_is_deepseek_first(self):
        source = (JAVA / "AiProviderChainClient.java").read_text(encoding="utf-8")
        run_chain = source[
            source.index("private static void runChain(") :
            source.index("private static boolean tryGemini(")
        ]

        openrouter_calls = [
            index
            for index in range(len(run_chain))
            if run_chain.startswith("tryOpenRouter", index)
        ]
        gemini_calls = [
            index
            for index in range(len(run_chain))
            if run_chain.startswith("tryGemini", index)
        ]

        self.assertLess(openrouter_calls[0], gemini_calls[0])
        self.assertLess(gemini_calls[1], openrouter_calls[1])
        self.assertEqual(run_chain.count("tryOpenRouter"), 2)
        self.assertEqual(run_chain.count("tryGemini"), 2)

    def test_example_config_matches_runtime_model_policy(self):
        example = (ROOT / "ai-assistant.example.properties").read_text(
            encoding="utf-8"
        )

        self.assertIn("model=deepseek/deepseek-v4-flash-0731\n", example)
        self.assertNotIn("pro_model=", example)
        self.assertIn("max_tokens=6750\n", example)
        self.assertIn("reasoning_effort=high\n", example)
        self.assertIn("gemini_models=gemini-3.7-flash\n", example)


if __name__ == "__main__":
    unittest.main()
