(ns scripts.short-ci
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [babashka.tasks :as tasks]))

(def config
  {:skip-if-only [#".*.md$"]})

(defn get-changes
  []
  (-> (tasks/shell {:out :string} "git diff --name-only HEAD~1")
      (:out)
      (str/split-lines)))

(defn irrelevant-change?
  [change regexes]
  (some? (some #(re-matches % change) regexes)))

(defn relevant?
  [change-set regexes]
  (some? (some #(not (irrelevant-change? % regexes)) change-set)))

;; TODO: generate config from clojure 
(defn write-config [path]
  (println (slurp (io/resource path))))

(defn main
  []
  (let [{:keys [skip-if-only]} config
        changed-files          (get-changes)]
    (if (relevant? changed-files skip-if-only)
      (do
        (println "Proceeding with CI run.")
        (write-config "circleci/actual.yml"))
      (do
        (println "Irrelevant changes - skipping CI run.")
        (write-config "circleci/shorted.yml")))))

(when (= *file* (System/getProperty "babashka.file"))
  (main))

(comment
  (def regexes
    [#".*.md$"
     #".*.clj$"]) ; ignore clojure files

  (:out (tasks/shell {:out :string} "ls"))

  (irrelevant-change? "src/file.png" regexes)

  (re-matches #".*.clj$" "src/file.clj.dfff")

  (relevant? ["src/file.clj"] regexes))
