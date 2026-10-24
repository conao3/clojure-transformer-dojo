(ns parts
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as jio]
   [dojo-io]
   [model]))

(defn- read-request []
  (json/read-str (slurp (jio/reader System/in :encoding "UTF-8"))))

(defn- write-response [resp]
  (doto (java.io.PrintStream. System/out false "UTF-8")
    (.print (json/write-str resp))
    (.flush)))

(defn- run [part req]
  (case part
    "tokenize" (model/tokenize (get req "text"))
    "softmax" {"y" (model/softmax (get req "x"))}
    "rmsnorm" {"y" (model/rmsnorm (get req "x") (get req "g"))}
    "rope" {"y" (model/rope (get req "x") (get req "pos"))}
    "attention_head" {"y" (model/attention-head (get req "q")
                                                (get req "ks")
                                                (get req "vs"))}
    "mha" {"ys" (model/mha (get req "layer") (get req "xs"))}
    "ffn" {"y" (model/ffn (get req "layer") (get req "x"))}
    "block" {"ys" (model/block (get req "layer") (get req "xs"))}
    "forward" {"logits" (model/forward (get req "tokens"))}
    "autoregress" {"tokens" (model/autoregress (get req "tokens")
                                               (get req "max_new"))}
    "sample" {"id" (model/sample (get req "logits")
                                 (get req "top_k")
                                 (get req "temperature" 1.0)
                                 (get req "u"))}))

(let [[part] *command-line-args*
      resp (run part (read-request))]
  (write-response resp)
  (dojo-io/stop)
  (shutdown-agents))
