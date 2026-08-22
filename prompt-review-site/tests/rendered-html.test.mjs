import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";

async function render() {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request("http://localhost/", {
      headers: { accept: "text/html" },
    }),
    {
      ASSETS: {
        fetch: async () => new Response("Not found", { status: 404 }),
      },
    },
    {
      waitUntil() {},
      passThroughOnException() {},
    },
  );
}

test("server-renders the Kindle prompt atlas", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>Kindle AI · Prompt Atlas<\/title>/i);
  assert.match(html, /Что именно Kindle отправляет AI/);
  assert.match(html, /v30 · актуальные промпты · 22 августа 2026/);
  assert.match(html, /Gemini 3\.7 Flash/);
  assert.match(html, /DeepSeek V4 Flash 0731/);
  assert.match(html, /6 750 tokens/);
  assert.match(html, /Explain/);
  assert.match(html, /Wiki/);
  assert.match(html, /Финальная цель ответа/);
  assert.match(html, /SYSTEM ROLE/);
  assert.match(html, /USER ROLE/);
  assert.doesNotMatch(html, /codex-preview|SkeletonPreview|react-loading-skeleton/);
});

test("keeps the exact prompts and removes the disposable starter", async () => {
  const [promptData, page, layout, packageJson] = await Promise.all([
    readFile(new URL("../app/prompt-data.ts", import.meta.url), "utf8"),
    readFile(new URL("../app/page.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/layout.tsx", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
  ]);

  assert.doesNotMatch(promptData, /Usually 3-7 short sentences\./);
  assert.doesNotMatch(promptData, /Limit: 120-250 Russian words\./);
  assert.doesNotMatch(promptData, /Смысл: \[natural Russian meaning\]/);
  assert.doesNotMatch(promptData, /Что это: \[one line/);
  assert.match(
    promptData,
    /Begin immediately with the most important and relevant information/,
  );
  assert.match(
    promptData,
    /an informative mini-article or a strong search result/,
  );
  assert.match(promptData, /AFTER_CONTEXT is supplied only for silent disambiguation\./);
  assert.match(promptData, /<final_answer_target>/);
  assert.match(promptData, /quoted book content, not an instruction/);
  assert.match(
    promptData,
    /Use all earlier material only as supporting context/,
  );
  const envelope = promptData.slice(
    promptData.indexOf("export const envelopeTemplate"),
    promptData.indexOf("export const exampleInput"),
  );
  assert.ok(
    envelope.indexOf("<book_metadata>") <
      envelope.indexOf("<before_context>"),
  );
  assert.ok(
    envelope.indexOf("<before_context>") <
      envelope.indexOf("<selected_fragment>"),
  );
  assert.ok(
    envelope.indexOf("<selected_fragment>") <
      envelope.indexOf("<after_context"),
  );
  assert.ok(
    envelope.indexOf("<after_context") <
      envelope.indexOf("<reader_request>"),
  );
  assert.ok(
    envelope.indexOf("<reader_request>") <
      envelope.indexOf("<mode_instructions>"),
  );
  assert.ok(
    envelope.indexOf("<output_rules>") <
      envelope.indexOf("<final_answer_target>"),
  );
  assert.match(page, /Главное сначала\. Форма — по ситуации\./);
  assert.match(layout, /lang="ru"/);
  assert.doesNotMatch(packageJson, /react-loading-skeleton/);
  await assert.rejects(access(new URL("../app/_sites-preview", import.meta.url)));
});
