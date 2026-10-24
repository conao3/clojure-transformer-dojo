# AGENTS.md

Clojure で Transformer の推論 (forward pass とトークン生成) をスクラッチで書くハンズオン用リポジトリ。README.md に背景がある。

- 学習はしない。約 1500 万パラメータの小さなモデルの推論だけを書く。GPU 不要。
- 重みとトークナイザは `models/` に置く (git 管理外)。形式は当日の配布物に従う。
- 外部の数値計算ライブラリは使わず、行列積・softmax・RMSNorm/LayerNorm・attention・サンプリングを自分で書く。読む人が追えることを優先し、最適化は動いてから。
- ビルドと実行は `nix develop` の中で `make test` / `clojure -M -m transformer-dojo.core`。
- 慣習は agents-share `notes/lang/clojure.md` に従う。
