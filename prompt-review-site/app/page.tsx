"use client";

import { useState } from "react";
import {
  envelopeTemplate,
  exampleInput,
  modes,
  systemPrompt,
  type Mode,
} from "./prompt-data";

type PromptView = "mode" | "envelope" | "system";

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1200);
  }

  return (
    <button className="copy-button" onClick={copy} type="button">
      {copied ? "Скопировано" : "Копировать"}
    </button>
  );
}

function PromptBlock({
  eyebrow,
  title,
  value,
}: {
  eyebrow: string;
  title: string;
  value: string;
}) {
  return (
    <section className="prompt-panel">
      <header className="panel-header">
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h3>{title}</h3>
        </div>
        <CopyButton value={value} />
      </header>
      <pre>{value}</pre>
    </section>
  );
}

function ModeButton({
  mode,
  active,
  onClick,
}: {
  mode: Mode;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      aria-pressed={active}
      className={`mode-button ${active ? "active" : ""}`}
      onClick={onClick}
      type="button"
    >
      <span>{mode.id}</span>
      <small>{mode.short}</small>
    </button>
  );
}

export default function Home() {
  const [activeMode, setActiveMode] = useState<Mode>(modes[0]);
  const [view, setView] = useState<PromptView>("mode");

  return (
    <main>
      <header className="hero">
        <nav className="topline">
          <span className="wordmark">KINDLE / AI</span>
          <span className="local-pill">LOCAL REVIEW · НЕ ПУБЛИКУЕТСЯ</span>
        </nav>

        <div className="hero-grid">
          <div>
            <p className="eyebrow">
              Prompt atlas · v30 · актуальные промпты · 22 августа 2026
            </p>
            <h1>Что именно Kindle отправляет AI</h1>
            <p className="lede">
              Все пять режимов, общий системный промпт, данные книги и реальный
              порядок моделей — в одном месте, без пересказа и скрытых частей.
            </p>
          </div>
          <div className="hero-stats" aria-label="Текущая конфигурация">
            <div>
              <span>PRIMARY</span>
              <strong>Gemini 3.7 Flash</strong>
              <small>high thinking</small>
            </div>
            <div>
              <span>FALLBACK / HOLD</span>
              <strong>DeepSeek V4 Flash 0731</strong>
              <small>high reasoning</small>
            </div>
            <div>
              <span>OUTPUT BUDGET</span>
              <strong>6 750 tokens</strong>
              <small>общий для всех режимов</small>
            </div>
          </div>
        </div>
      </header>

      <section className="route-section" aria-labelledby="route-title">
        <div className="section-title">
          <p className="eyebrow">Маршрутизация</p>
          <h2 id="route-title">Один жест меняет только порядок провайдеров</h2>
        </div>
        <div className="routes">
          <article>
            <span className="route-number">01</span>
            <div>
              <h3>Короткое нажатие</h3>
              <p>Gemini · до 4 попыток</p>
            </div>
            <span className="arrow">→</span>
            <div>
              <h3>Если не ответил</h3>
              <p>DeepSeek Flash · до 4 попыток</p>
            </div>
          </article>
          <article>
            <span className="route-number">02</span>
            <div>
              <h3>Удержание ≈ 800 мс</h3>
              <p>DeepSeek Flash · до 5 попыток</p>
            </div>
            <span className="arrow">→</span>
            <div>
              <h3>Если не ответил</h3>
              <p>Gemini · до 4 попыток</p>
            </div>
          </article>
        </div>
        <div className="payload-stack">
          <div>
            <span className="payload-role">SYSTEM ROLE</span>
            <h3>Общие правила ассистента</h3>
            <p>
              Отдельное сообщение высокого приоритета. Оно не смешивается с
              книжным текстом и не нуждается в повторе внизу.
            </p>
          </div>
          <div>
            <span className="payload-role">USER ROLE</span>
            <h3>Книга → режим → финальная цель</h3>
            <p>
              Метаданные и контекст, затем промпт режима и правила ответа. Самым
              последним повторяется точный выделенный фрагмент.
            </p>
          </div>
        </div>
      </section>

      <section className="workbench" aria-labelledby="modes-title">
        <aside>
          <p className="eyebrow">Режимы</p>
          <h2 id="modes-title">Зачем читатель выделил текст</h2>
          <div className="mode-list">
            {modes.map((mode) => (
              <ModeButton
                active={activeMode.id === mode.id}
                key={mode.id}
                mode={mode}
                onClick={() => {
                  setActiveMode(mode);
                  setView("mode");
                }}
              />
            ))}
          </div>
        </aside>

        <div className="mode-detail">
          <div className="mode-summary">
            <div>
              <p className="eyebrow">Активный режим</p>
              <h2>{activeMode.id}</h2>
              <p>{activeMode.purpose}</p>
            </div>
            <div className="length-card">
              <span>Форма ответа</span>
              <strong>{activeMode.shape}</strong>
            </div>
          </div>

          <div className="view-tabs" role="tablist" aria-label="Части промпта">
            {[
              ["mode", "Промпт режима"],
              ["envelope", "Общий конверт"],
              ["system", "Системный промпт"],
            ].map(([id, label]) => (
              <button
                aria-selected={view === id}
                className={view === id ? "active" : ""}
                key={id}
                onClick={() => setView(id as PromptView)}
                role="tab"
                type="button"
              >
                {label}
              </button>
            ))}
          </div>

          {view === "mode" && (
            <PromptBlock
              eyebrow="Дословно из AiAssistantController.java"
              title={`Инструкция режима ${activeMode.id}`}
              value={activeMode.prompt}
            />
          )}
          {view === "envelope" && (
            <PromptBlock
              eyebrow="Структура каждого запроса"
              title="Общий пользовательский промпт"
              value={envelopeTemplate}
            />
          )}
          {view === "system" && (
            <PromptBlock
              eyebrow="Дословно из OpenRouterClient.java"
              title="Общие правила ассистента"
              value={systemPrompt}
            />
          )}
        </div>
      </section>

      <section className="input-section" aria-labelledby="input-title">
        <div className="section-title">
          <p className="eyebrow">Входные данные</p>
          <h2 id="input-title">Что Kindle берёт из книги</h2>
          <p>
            Пример ниже вымышленный, но показывает полный порядок
            пользовательского запроса: книжные данные, инструкции режима и
            повтор выделенного фрагмента в самом конце.
          </p>
        </div>
        <div className="input-grid">
          <div className="capture-list">
            <article>
              <span>01</span>
              <h3>Метаданные</h3>
              <p>Название, авторы, язык, текущая глава и позиции выделения.</p>
            </article>
            <article>
              <span>02</span>
              <h3>До выделения</h3>
              <p>Около 5 000 слов, максимум 60 000 символов. Считается прочитанным.</p>
            </article>
            <article>
              <span>03</span>
              <h3>Сам фрагмент</h3>
              <p>Главный источник истины и центральный предмет ответа.</p>
            </article>
            <article>
              <span>04</span>
              <h3>После выделения</h3>
              <p>Около 3 000 слов, максимум 60 000 символов. Только для тихого уточнения.</p>
            </article>
            <article>
              <span>05</span>
              <h3>Инструкции</h3>
              <p>Адаптация, полный промпт выбранного режима и общие правила ответа.</p>
            </article>
            <article>
              <span>06</span>
              <h3>Финальная цель ответа</h3>
              <p>Выделенный фрагмент повторяется дословно последним и объявляется непосредственной целью.</p>
            </article>
          </div>
          <PromptBlock
            eyebrow="Иллюстративный пример"
            title="Сокращённый пример полного запроса"
            value={exampleInput}
          />
        </div>
      </section>

      <section className="length-note">
        <p className="eyebrow">Новая политика ответа</p>
        <h2>Главное сначала. Форма — по ситуации.</h2>
        <p>
          Общий потолок возвращён к 6 750 токенам. Ни один режим больше не
          задаёт число предложений, слов или обязательные рубрики. Ответ сразу
          начинается с самой релевантной информации, а затем модель сама
          выбирает полезную глубину, контекст и структуру — без вступительной
          воды и искусственного растягивания.
        </p>
      </section>

      <footer>
        <span>
          Источник: финальные промпты из текущего кода · 22 августа 2026
        </span>
        <span>Kindle не изменён · сайт локальный</span>
      </footer>
    </main>
  );
}
