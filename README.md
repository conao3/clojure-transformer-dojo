# clojure-transformer-dojo

「あなたの言語で書く Transformer」(Frog イベント、2026-10-24、登壇: LayerX Ryosuke さん) のハンズオン用リポジトリ。元ネタは YAPC::Tokyo 2026 の「Transformerをあなたの言語で―60分で書くLLM推論、Transformer道場」。

- イベントページ: http://frogagent.com/events/transformer-dojo-talk-2026-10/
- 題材: 1500 万パラメータ規模の小さなモデルの **推論** を好きな言語 (ここでは Clojure) で書く。学習は含まない。GPU 不要。
- 進め方: コーディングエージェントにコードを書かせ、読んで、動かして、質問する。

## 構成

- `src/transformer_dojo/`: 実装
- `test/transformer_dojo/`: テスト
- `models/`: 重み・トークナイザ置き場 (git 管理外。当日の配布物を置く)

## 開発

```bash
nix develop
make test
```
