"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { CATEGORIES, IJIN, type Category, type Ijin } from "./data/ijin";

const QUESTION_COUNT = 10;
const TIME_LIMIT = 20; // 秒
const BEST_KEY = "ijin-quiz-best";

type Question = { answer: Ijin; choices: string[] };
type Result = { question: Question; picked: string | null; correct: boolean };
type Phase = "start" | "playing" | "result";

function shuffle<T>(arr: T[]): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function buildQuestions(category: Category | "すべて"): Question[] {
  const pool = category === "すべて" ? IJIN : IJIN.filter((p) => p.category === category);
  return shuffle(pool)
    .slice(0, QUESTION_COUNT)
    .map((answer) => {
      // 同じジャンルからまぎらわしい選択肢を優先して選ぶ
      const same = shuffle(IJIN.filter((p) => p.category === answer.category && p.name !== answer.name));
      const others = shuffle(IJIN.filter((p) => p.category !== answer.category));
      const wrong = [...same, ...others].slice(0, 3).map((p) => p.name);
      return { answer, choices: shuffle([answer.name, ...wrong]) };
    });
}

function rankOf(score: number, total: number) {
  const r = score / total;
  if (r === 1) return { title: "真の強つよ勢", msg: "全問正解！もう「自称」は外していい。" };
  if (r >= 0.8) return { title: "強つよ勢", msg: "かなりの歴史通。あと一歩で頂点！" };
  if (r >= 0.5) return { title: "自称強つよ勢", msg: "名乗るだけの実力はある…かも？" };
  if (r >= 0.3) return { title: "見習い勢", msg: "伸びしろしかない。もう一回いこう！" };
  return { title: "これからの人", msg: "偉人たちもみんな最初は初心者だった。" };
}

function readBest(): number {
  try {
    return Number(localStorage.getItem(BEST_KEY) ?? 0) || 0;
  } catch {
    return 0;
  }
}

const noopSubscribe = () => () => {};

function saveBest(score: number) {
  try {
    localStorage.setItem(BEST_KEY, String(score));
  } catch {
    /* 保存できない環境では無視 */
  }
}

export default function Quiz() {
  const [phase, setPhase] = useState<Phase>("start");
  const [category, setCategory] = useState<Category | "すべて">("すべて");
  const [questions, setQuestions] = useState<Question[]>([]);
  const [index, setIndex] = useState(0);
  const [picked, setPicked] = useState<string | null>(null);
  const [answered, setAnswered] = useState(false);
  const [results, setResults] = useState<Result[]>([]);
  const [timeLeft, setTimeLeft] = useState(TIME_LIMIT);
  const [combo, setCombo] = useState(0);
  const storedBest = useSyncExternalStore(noopSubscribe, readBest, () => 0);
  const [sessionBest, setSessionBest] = useState(0);
  const best = Math.max(storedBest, sessionBest);

  const current = questions[index];
  const questionTotal = Math.min(
    QUESTION_COUNT,
    category === "すべて" ? IJIN.length : IJIN.filter((p) => p.category === category).length,
  );
  const score = results.filter((r) => r.correct).length;

  function start() {
    setQuestions(buildQuestions(category));
    setIndex(0);
    setPicked(null);
    setAnswered(false);
    setResults([]);
    setCombo(0);
    setTimeLeft(TIME_LIMIT);
    setPhase("playing");
  }

  function answer(choice: string | null) {
    if (answered || !current) return;
    const correct = choice === current.answer.name;
    setPicked(choice);
    setAnswered(true);
    setCombo((c) => (correct ? c + 1 : 0));
    setResults((rs) => [...rs, { question: current, picked: choice, correct }]);
  }

  function next() {
    if (index + 1 >= questions.length) {
      const finalScore = results.filter((r) => r.correct).length;
      if (finalScore > best) {
        setSessionBest(finalScore);
        saveBest(finalScore);
      }
      setPhase("result");
      return;
    }
    setIndex((i) => i + 1);
    setPicked(null);
    setAnswered(false);
    setTimeLeft(TIME_LIMIT);
  }

  // タイマー
  useEffect(() => {
    if (phase !== "playing" || answered) return;
    const id = setTimeout(() => {
      if (timeLeft <= 1) {
        setTimeLeft(0);
        answer(null);
      } else {
        setTimeLeft(timeLeft - 1);
      }
    }, 1000);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase, answered, timeLeft]);

  return (
    <div className="flex flex-1 w-full justify-center bg-amber-50 px-4 py-8 text-stone-800 dark:bg-stone-950 dark:text-stone-100 sm:py-14">
      <main className="w-full max-w-xl">
        <header className="mb-6 text-center">
          <p className="text-xs font-semibold tracking-[0.3em] text-amber-700 dark:text-amber-400">
            自称強つよ勢
          </p>
          <h1 className="mt-1 text-3xl font-black tracking-tight sm:text-4xl">偉人クイズ</h1>
        </header>

        {phase === "start" && (
          <section className="rounded-3xl bg-white p-6 shadow-sm ring-1 ring-stone-200 dark:bg-stone-900 dark:ring-stone-800 sm:p-8">
            <p className="leading-7 text-stone-600 dark:text-stone-300">
              ヒントを読んで、どの偉人のことか4択で当てよう。全{questionTotal}問、1問{TIME_LIMIT}秒。
            </p>

            <h2 className="mt-6 text-sm font-bold text-stone-500 dark:text-stone-400">ジャンル</h2>
            <div className="mt-2 grid grid-cols-3 gap-2 sm:grid-cols-5">
              {(["すべて", ...CATEGORIES] as const).map((c) => (
                <button
                  key={c}
                  onClick={() => setCategory(c)}
                  className={`rounded-xl px-3 py-2 text-sm font-bold transition ${
                    category === c
                      ? "bg-amber-600 text-white"
                      : "bg-stone-100 text-stone-700 hover:bg-stone-200 dark:bg-stone-800 dark:text-stone-200 dark:hover:bg-stone-700"
                  }`}
                >
                  {c}
                </button>
              ))}
            </div>

            <button
              onClick={start}
              className="mt-8 h-14 w-full rounded-2xl bg-stone-900 text-lg font-bold text-white transition hover:bg-stone-700 dark:bg-amber-500 dark:text-stone-950 dark:hover:bg-amber-400"
            >
              スタート
            </button>
            {best > 0 && (
              <p className="mt-4 text-center text-sm text-stone-500 dark:text-stone-400">
                自己ベスト：{best}問正解
              </p>
            )}
          </section>
        )}

        {phase === "playing" && current && (
          <section className="rounded-3xl bg-white p-6 shadow-sm ring-1 ring-stone-200 dark:bg-stone-900 dark:ring-stone-800 sm:p-8">
            <div className="flex items-center justify-between text-sm font-bold">
              <span>
                第{index + 1}問 <span className="text-stone-400">/ {questions.length}</span>
              </span>
              <span className="flex items-center gap-3">
                {combo >= 2 && <span className="text-amber-600 dark:text-amber-400">{combo}連続正解！</span>}
                <span className={timeLeft <= 5 && !answered ? "text-red-600" : "text-stone-500 dark:text-stone-400"}>
                  ⏱ {timeLeft}秒
                </span>
              </span>
            </div>
            <div className="mt-3 h-2 overflow-hidden rounded-full bg-stone-100 dark:bg-stone-800">
              <div
                className="h-full bg-amber-500 transition-all duration-1000 ease-linear"
                style={{ width: `${(timeLeft / TIME_LIMIT) * 100}%` }}
              />
            </div>

            <div className="mt-6 flex gap-2 text-xs font-bold">
              <span className="rounded-full bg-amber-100 px-3 py-1 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300">
                {current.answer.category}
              </span>
              <span className="rounded-full bg-stone-100 px-3 py-1 text-stone-600 dark:bg-stone-800 dark:text-stone-300">
                {current.answer.era}
              </span>
            </div>
            <p className="mt-3 text-lg font-bold leading-8">{current.answer.hint}</p>
            <p className="mt-1 text-sm text-stone-500 dark:text-stone-400">この人物はだれ？</p>

            <div className="mt-6 grid gap-3">
              {current.choices.map((c) => {
                const isAnswer = c === current.answer.name;
                const isPicked = c === picked;
                let style =
                  "bg-stone-50 ring-stone-200 hover:bg-amber-50 hover:ring-amber-400 dark:bg-stone-800 dark:ring-stone-700 dark:hover:bg-stone-700";
                if (answered && isAnswer)
                  style = "bg-emerald-50 ring-emerald-500 text-emerald-900 dark:bg-emerald-900/40 dark:text-emerald-100";
                else if (answered && isPicked)
                  style = "bg-red-50 ring-red-500 text-red-900 dark:bg-red-900/40 dark:text-red-100";
                else if (answered) style = "bg-stone-50 ring-stone-200 opacity-50 dark:bg-stone-800 dark:ring-stone-700";
                return (
                  <button
                    key={c}
                    onClick={() => answer(c)}
                    disabled={answered}
                    className={`flex min-h-14 items-center justify-between rounded-2xl px-5 py-3 text-left font-bold ring-2 transition ${style}`}
                  >
                    {c}
                    {answered && isAnswer && <span>○</span>}
                    {answered && isPicked && !isAnswer && <span>×</span>}
                  </button>
                );
              })}
            </div>

            {answered && (
              <div className="mt-6">
                <p className="text-lg font-black">
                  {picked === current.answer.name ? (
                    <span className="text-emerald-600 dark:text-emerald-400">正解！</span>
                  ) : picked === null ? (
                    <span className="text-red-600 dark:text-red-400">時間切れ…</span>
                  ) : (
                    <span className="text-red-600 dark:text-red-400">ざんねん…</span>
                  )}
                  <span className="ml-2 text-base font-bold">正解は「{current.answer.name}」</span>
                </p>
                <p className="mt-2 rounded-xl bg-amber-50 p-3 text-sm leading-6 text-stone-700 dark:bg-stone-800 dark:text-stone-300">
                  💡 {current.answer.fact}
                </p>
                <button
                  onClick={next}
                  className="mt-4 h-12 w-full rounded-2xl bg-stone-900 font-bold text-white transition hover:bg-stone-700 dark:bg-amber-500 dark:text-stone-950 dark:hover:bg-amber-400"
                >
                  {index + 1 >= questions.length ? "結果を見る" : "次の問題へ"}
                </button>
              </div>
            )}
          </section>
        )}

        {phase === "result" && (
          <section className="rounded-3xl bg-white p-6 shadow-sm ring-1 ring-stone-200 dark:bg-stone-900 dark:ring-stone-800 sm:p-8">
            <div className="text-center">
              <p className="text-sm font-bold text-stone-500 dark:text-stone-400">あなたの称号</p>
              <p className="mt-1 text-3xl font-black text-amber-600 dark:text-amber-400">
                {rankOf(score, questions.length).title}
              </p>
              <p className="mt-4 text-5xl font-black">
                {score}
                <span className="text-2xl text-stone-400"> / {questions.length}</span>
              </p>
              <p className="mt-3 text-stone-600 dark:text-stone-300">{rankOf(score, questions.length).msg}</p>
              <p className="mt-1 text-sm text-stone-500 dark:text-stone-400">自己ベスト：{best}問正解</p>
            </div>

            <h2 className="mt-8 text-sm font-bold text-stone-500 dark:text-stone-400">ふりかえり</h2>
            <ul className="mt-2 divide-y divide-stone-100 dark:divide-stone-800">
              {results.map((r, i) => (
                <li key={i} className="flex items-start gap-3 py-3 text-sm">
                  <span className={`font-black ${r.correct ? "text-emerald-600" : "text-red-600"}`}>
                    {r.correct ? "○" : "×"}
                  </span>
                  <span className="flex-1">
                    <span className="font-bold">{r.question.answer.name}</span>
                    {!r.correct && (
                      <span className="text-stone-500 dark:text-stone-400">
                        （あなたの答え：{r.picked ?? "時間切れ"}）
                      </span>
                    )}
                  </span>
                </li>
              ))}
            </ul>

            <div className="mt-6 grid gap-3 sm:grid-cols-2">
              <button
                onClick={start}
                className="h-12 rounded-2xl bg-stone-900 font-bold text-white transition hover:bg-stone-700 dark:bg-amber-500 dark:text-stone-950 dark:hover:bg-amber-400"
              >
                もう一度
              </button>
              <button
                onClick={() => setPhase("start")}
                className="h-12 rounded-2xl bg-stone-100 font-bold text-stone-700 transition hover:bg-stone-200 dark:bg-stone-800 dark:text-stone-200 dark:hover:bg-stone-700"
              >
                ジャンルを変える
              </button>
            </div>
          </section>
        )}
      </main>
    </div>
  );
}
