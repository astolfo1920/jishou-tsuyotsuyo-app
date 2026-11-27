// Web 版のアプリ本体だけを www/ にコピーする（.env.local や .vercel をアプリに含めないため）
import { cpSync, rmSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = join(root, '睡眠管理アプリ_モックアップ');
const out = join(root, 'www');

rmSync(out, { recursive: true, force: true });
mkdirSync(out);
for (const p of ['index.html', 'css', 'js']) cpSync(join(src, p), join(out, p), { recursive: true });
console.log('copied web assets to www/');
