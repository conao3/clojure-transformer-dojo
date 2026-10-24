(ns dojo-io
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as jio]))

;; カーネルは1プロセスにつき1回だけ起動して使い回す
;; （起動のたびに重みを読み直すため）。
(defonce ^:private kernel
  (delay
    (let [bin (System/getenv "DOJO_BIN")
          _ (when (empty? bin)
              (throw (ex-info "DOJO_BIN is required" {})))
          builder (doto (ProcessBuilder. ^java.util.List [bin])
                    (.redirectError
                     java.lang.ProcessBuilder$Redirect/INHERIT))
          proc (.start builder)]
      {:proc proc
       :in (jio/writer (.getOutputStream proc) :encoding "UTF-8")
       :out (jio/reader (.getInputStream proc) :encoding "UTF-8")})))

;; dojo:fn request
;; モデルの外：カーネルへ1行送り、1行の答えを受け取る
(defn request [message]
  (let [{:keys [in out]} @kernel
        line (str (json/write-str message) "\n")]
    ;; 1行でカーネルに届けないと、カーネルは行の終わりを待つ
    (.write in line) ; [00.2]
    ;; flush を忘れると、双方が相手を待ってタイムアウトする
    (.flush in) ; [00.3]
    ;; 答えはちょうど1行。EOF まで読むとカーネルを待ち続ける
    (let [resp (json/read-str (.readLine out)) ; [00.4]
          ok? (get resp "ok")]
      (when-not ok?
        (binding [*out* *err*]
          (println (get resp "error")))
        (System/exit 1))
      resp)))
;; dojo:end request

(defn stop
  "カーネルの stdin を閉じて終了を待つ。"
  []
  (when (realized? kernel)
    (let [{:keys [proc in]} @kernel]
      (.close in)
      (.waitFor ^Process proc))))
