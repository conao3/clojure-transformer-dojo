(ns generate
  (:require
   [dojo-io]
   [model]))

;; 日本語のプロンプトが引数で壊れないよう、環境変数があればそれを使う
(defn- parse-args [args]
  (let [env-prompt (System/getenv "DOJO_PROMPT")
        [prompt & more] (if env-prompt
                          (cons env-prompt (take-last 2 args))
                          args)]
    {:prompt prompt
     :max-new (if (first more) (parse-long (first more)) 64)
     :top-k (if (second more) (parse-long (second more)) 1)}))

(def ^:private out
  (java.io.PrintStream. System/out false "UTF-8"))

;; 選んだ瞬間に1片ずつ出力して、物語が単語ごとに現れるようにする
(defn- emit [text]
  (.print out text)
  (.flush out))

;; デコードはカーネルに任せる。文脈の全体を送り、最後の ID を最後に置く
(defn- piece [tokens]
  (get (dojo-io/request {"op" "decode" "tokens" tokens}) "text"))

(defn- tell [{:keys [prompt max-new top-k]}]
  (emit prompt)
  (loop [tokens (vec (get (model/tokenize prompt) "ids"))
         n 0]
    (when (and (< n max-new) (<= (count tokens) model/SEQ_LEN))
      (let [logits (model/forward tokens)
            id (model/sample logits top-k 1.0 (rand))]
        (when-not (contains? #{model/BOS model/EOS} id)
          (let [tokens (conj tokens id)]
            (emit (piece tokens))
            (recur tokens (inc n))))))))

(try
  (tell (parse-args *command-line-args*))
  (finally
    (dojo-io/stop)
    (shutdown-agents)))
