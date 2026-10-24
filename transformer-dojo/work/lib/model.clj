(ns model
  (:require
   [dojo-io])
  (:import
   (java.nio ByteBuffer ByteOrder)
   (java.util Base64)))

;; dojo:fn constants
;; モデルの形
(def DIM 288)
(def N_LAYERS 6)
(def N_HEADS 6)
(def HEAD_SIZE 48)
(def HIDDEN_DIM 768)
(def VOCAB_SIZE 32000)
(def SEQ_LEN 256)
(def ROPE_THETA 10000.0)
(def EPS 1e-5)
(def BOS 1)
(def EOS 2)
;; dojo:end constants

;; 重みとの行列積はカーネルが計算する。x は1行でも複数行でもよい
(defn- matmul [name x]
  (get (dojo-io/request {"op" "matmul" "name" name "x" x}) "y"))

;; カーネルの f32 はリトルエンディアン。順序を誤ると NaN になる
(defn- decode-f32 [b64]
  (let [raw (.decode (Base64/getDecoder) ^String b64)
        buf (ByteBuffer/wrap raw)]
    (.order buf ByteOrder/LITTLE_ENDIAN)
    (vec (repeatedly VOCAB_SIZE #(double (.getFloat buf))))))

;; 1次元の重み（RMSNorm のゲイン）はカーネルから読み出す。
;; 変わらない値なので、名前ごとに最初の1回だけ尋ねる
(def ^:private weight
  (memoize
   (fn [name]
     (get (dojo-io/request {"op" "weight" "name" name}) "y"))))

;; dojo:fn tokenize
;; モデルの前：テキスト → トークン列
(defn tokenize [text]
  (let [msg {"op" "tokenize" "text" text}
        ;; 単語でも文字でもなくピースに分けるので、語彙も文も短く済む
        resp (dojo-io/request msg)] ; [00.1]
    {"ids" (get resp "ids")
     "pieces" (get resp "pieces")}))
;; dojo:end tokenize

;; dojo:fn embed
;; ストリームの始まり：トークンを埋め込み表の行に置き換える
(defn embed [tokens]
  (let [msg {"op" "embed" "tokens" tokens}]
    (get (dojo-io/request msg) "y")))
;; dojo:end embed

;; dojo:fn rmsnorm
;; 位置ごとに独立して：ベクトルの大きさをそろえる
(defn rmsnorm [x g]
  (let [n (count x)
        ms (/ (reduce + (map * x x)) n) ; [02.1]
        ;; 続く行列は rms 1 を想定。x がどれだけ育っても戻す
        inv (/ 1.0 (Math/sqrt (+ ms EPS)))] ; [02.2]
    ;; 次元ごとの学習ゲインで、効かせたい次元を変えられる
    (mapv #(* %1 %2 inv) g x))) ; [02.3]
;; dojo:end rmsnorm

;; dojo:fn rope
;; アテンションの中：q と k を位置の分だけ回す
(defn rope [x pos]
  (vec
   (mapcat
    (fn [i]
      (let [head-dim (mod i HEAD_SIZE)
            ratio (/ head-dim HEAD_SIZE)
            ;; 速い対と遅い対が時計の針のように近さと遠さを示す
            freq (Math/pow ROPE_THETA (- ratio)) ; [03.1]
            angle (* pos freq) ; [03.2]
            c (Math/cos angle)
            s (Math/sin angle)
            xi (nth x i)
            xj (nth x (inc i))]
        ;; 位置で q と k を回すので、スコアは距離だけで決まる
        [(- (* xi c) (* xj s)) ; [03.3]
         (+ (* xi s) (* xj c))]))
    (range 0 (count x) 2))))
;; dojo:end rope

;; dojo:fn softmax
;; スコア → 確率：すべて正で、合計が1になる
(defn softmax [x]
  ;; 最大値を引く：exp(1003) はあふれるが、引けば最大でも exp(0)
  (let [m (apply max x) ; [01.1]
        ;; exp ですべて正になり、大きいスコアは大きいまま残る
        e (mapv #(Math/exp (- % m)) x) ; [01.2]
        s (reduce + e)] ; [01.3]
    ;; 合計で割ると、足して1になる確率になる
    (mapv #(/ % s) e))) ; [01.4]
;; dojo:end softmax

;; dojo:fn attention_head
;; 位置のあいだ：1つのヘッドが見える位置から値を集める
(defn attention-head [q ks vs]
  ;; √48 で割ると48個の積の和が縮み、割合が散らばったままになる
  (let [scale (/ 1.0 (Math/sqrt HEAD_SIZE)) ; [04.1]
        ;; クエリがキーと合う位置ほど、スコアは大きくなる
        dots (mapv #(reduce + (map * q %)) ks) ; [04.2]
        score (mapv #(* scale %) dots)
        a (softmax score)] ; [04.3]
    ;; 1つの位置だけでなく、見える全位置のバリューを割合で混ぜる
    (mapv (fn [i] ; [04.4]
            (reduce + (map #(* %1 (nth %2 i)) a vs)))
          (range HEAD_SIZE))))
;; dojo:end attention_head

;; dojo:fn mha
;; 位置のあいだ：6つのヘッドを並べて、過去の行だけを読む
(defn mha [layer xs]
  (let [w #(str "layers." layer "." %)
        ;; 各層は自分の入力から、新しくクエリ・キー・バリューを作る
        qs (matmul (w "wq") xs) ; [05.1]
        ks (matmul (w "wk") xs) ; [05.1]
        vs (matmul (w "wv") xs) ; [05.1]
        ;; 位置を与えるのはこの呼び出し。q と k を回し、v は回さない
        qs (mapv rope qs (range)) ; [05.2]
        ks (mapv rope ks (range)) ; [05.2]
        ;; 各ヘッドは別々のソフトマックスで、別の読み方をする
        head (fn [row h] ; [05.4]
               (let [lo (* h HEAD_SIZE)]
                 (subvec row lo (+ lo HEAD_SIZE))))
        ;; 各行は自分と前の行だけを読むので、後ろの文は結果を変えない
        seen (fn [rows p h] ; [05.3]
               (mapv #(head % h) (take (inc p) rows)))
        attend (fn [p h]
                 (attention-head (head (qs p) h)
                                 (seen ks p h)
                                 (seen vs p h)))
        heads (range N_HEADS)
        xbs (mapv (fn [p] ; [05.5]
                    (vec (mapcat #(attend p %) heads)))
                  (range (count xs)))]
    ;; wo が6つのヘッドの結果を、288個の数の1行に混ぜる
    (matmul (w "wo") xbs))) ; [05.6]
;; dojo:end mha

;; dojo:fn silu
;; 位置ごとに独立して：なめらかな活性化。負の値は小さな負で残る
(defn silu [z]
  (/ z (+ 1.0 (Math/exp (- z)))))
;; dojo:end silu

;; dojo:fn ffn
;; 位置ごとに独立して：288 → 768 に広げ、ゲートして 288 へ戻す
(defn ffn [layer x]
  (let [w #(str "layers." layer "." %)
        ;; ゲートは、各隠れユニットの up をどれだけ通すかを決める
        gate (matmul (w "w1") x) ; [06.1]
        ;; up はゲートに掛けられる側で、ゲート付きのほうが成績が良い
        up (matmul (w "w3") x) ; [06.2]
        ;; silu の曲がりと積がなければ、3つの行列は1つと同じ動きになる
        act (mapv #(* (silu %1) %2) gate up)] ; [06.3]
    (matmul (w "w2") act))) ; [06.4]
;; dojo:end ffn

;; dojo:fn block
;; ストリームへの2回の足し算：注意の枝と FFN の枝を足し込む
(defn block [layer xs]
  (let [w #(str "layers." layer "." %)
        g-att (weight (w "rms_att"))
        g-ffn (weight (w "rms_ffn"))
        ;; 枝は拡大縮小したコピーを読み、x 自体は変えないでおく
        normed (mapv #(rmsnorm % g-att) xs) ; [07.1]
        att (mha layer normed) ; [07.2]
        ;; 足すのであって置き換えない。前の層が書いたものが残る
        hs (mapv #(mapv + %1 %2) xs att) ; [07.3]
        normed-h (mapv #(rmsnorm % g-ffn) hs) ; [07.4]
        fs (mapv #(ffn layer %) normed-h)] ; [07.5]
    (mapv #(mapv + %1 %2) hs fs))) ; [07.6]
;; dojo:end block

;; dojo:fn forward
;; モデル全体：トークン列 → 最後の位置のロジット
(defn forward [tokens]
  (let [layers (range N_LAYERS)
        ;; 各トークンは位置を持たない、埋め込み表の1行から始まる
        xs (embed tokens) ; [08.1]
        xs (reduce #(block %2 %1) xs layers) ; [08.2]
        g (weight "rms_final")
        ;; ブロックはコピーだけを正規化するので、ここで x を正規化する
        x (rmsnorm (peek xs) g) ; [08.3]
        msg {"op" "matmul" "name" "output" "x" x
             "encoding" "b64"}
        ;; 分類器は埋め込み表を使い回す：似た使われ方の語は似た行になる
        resp (dojo-io/request msg)] ; [08.4]
    (decode-f32 (get resp "y_b64"))))
;; dojo:end forward

;; dojo:fn autoregress
;; ループ：自分の出力を入力に戻して、1トークンずつ書き足す
(defn autoregress [tokens max-new]
  (loop [tokens (vec tokens)
         out []]
    (if (or (>= (count out) max-new) ; [09.3]
            (> (count tokens) SEQ_LEN)) ; [09.3]
      out
      ;; 毎回、古い行もやり直す。実際の LLM は k と v を取っておく
      (let [logits (forward tokens) ; [09.1]
            top #(if (> (logits %2) (logits %1)) %2 %1)
            ids (range VOCAB_SIZE)
            next-id (reduce top ids)] ; [09.2]
        (if (contains? #{BOS EOS} next-id) ; [09.3]
          out
          ;; 次の通過は、1つ長い入力への同じ forward 呼び出しになる
          (recur (conj tokens next-id) ; [09.4]
                 (conj out next-id)))))))
;; dojo:end autoregress

;; dojo:fn sample
;; モデルの後：ロジット → トークン1つ。上位 k から u で引く
(defn sample [logits top-k temperature u]
  (let [z (mapv #(/ % temperature) logits) ; [10.1]
        ids (range (count z))
        k (min top-k (count z))
        order (fn [i] [(- (z i)) i])
        ;; 最上位だけだと同じ話の繰り返し。k 個から引くと話が変わる
        kept (take k (sort-by order ids)) ; [10.2]
        p (softmax (mapv z kept))] ; [10.3]
    ;; 確率の高いトークンは 0〜1 の広い区間を持ち、u がそこに落ちやすい
    (loop [ids kept ; [10.4]
           ps p
           c 0.0]
      (let [c (+ c (first ps))]
        (if (or (> c u) (nil? (next ids)))
          (first ids)
          (recur (next ids) (next ps) c))))))
;; dojo:end sample
