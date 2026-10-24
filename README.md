# clojure-transformer-dojo

「あなたの言語で書く Transformer」(Frog イベント、2026-10-24、登壇: LayerX Ryosuke さん) のハンズオン用リポジトリ。元ネタは YAPC::Tokyo 2026 の「Transformerをあなたの言語で―60分で書くLLM推論、Transformer道場」。

- イベントページ: http://frogagent.com/events/transformer-dojo-talk-2026-10/
- 特設サイト (配布物のダウンロード): https://transformer-dojo.opsbr.dev/
- 題材: 約 1500 万パラメータの Llama 系モデルの **推論** を Clojure で書く。学習は含まない。GPU 不要。コーディングエージェントが 11 個のパーツ (`tokenize` から `sample` まで) を書き、人間はそれを読む。

## 構成

- `transformer-dojo/`: 特設サイトの配布物 (`make setup` で展開。git 管理外)。`./dojo` コマンド、モデル、テスト、エージェント用スキルが入る
- `transformer-dojo/work/`: エージェントが書くコード。**ここだけ git 管理**
- `flake.nix`: JDK 21、clojure、babashka、clj-kondo、cljstyle。dojo の Clojure ランタイムは babashka (`bb`)

## 手順

```bash
nix develop
make setup   # 配布物を transformer-dojo/ に展開 (./dojo も未展開なら自動で行う)
make init    # dojo init (session: Japanese, language: Clojure)
./dojo       # セットアップページが開く。OpenCode を使う場合はここ
```

リポジトリ直下の `./dojo` は `transformer-dojo/dojo` のラッパーで、`./dojo test` `./dojo viz` `./dojo generate ...` もそのまま使える。

Claude Code など手元のエージェントを使う場合は `transformer-dojo/` で起動し、次を送る。

> Run the dojo. Session: Japanese. Language: Clojure.

進捗は `./dojo status`、確認は `./dojo test`、書いたコードを読むのは `./dojo viz`、物語の生成は `./dojo generate "There was a big tree with"`。

## メモ

- 出力する JSON は `clojure.data.json` (babashka 同梱)。`deps.edn` は使わない。
- 特設サイトのパスコード (OpenRouter の支援クレジット用) はこのリポジトリに書かない。
