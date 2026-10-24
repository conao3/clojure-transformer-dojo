# AGENTS.md

Clojure で Transformer の推論をスクラッチで書くハンズオン用リポジトリ。背景は README.md。

- ワークショップの作業ディレクトリは `transformer-dojo/`。エージェントはそこで起動し、`transformer-dojo/AGENTS.md` と `.agents/skills/dojo/SKILL.md` に従う。コードは `transformer-dojo/work/` にだけ書く。
- `transformer-dojo/` の配布物 (`dojo`、`model/`、`tests/golden/` など) は編集しない。`make setup` で取り直せる。
- Clojure は babashka で動かす。`deps.edn` を作らず、`clojure.data.json` を使う。
- 慣習は agents-share `notes/lang/clojure.md` に従う。`make lint` / `make fmt` は `work/` が対象。
