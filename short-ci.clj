(require '[clojure.string :as str]
         '[babashka.tasks :as tasks])

(def config
  {:skip-if-only [#".*.md$"]})

(defn get-changes
  []
  (-> (tasks/shell {:out :string} "git diff --name-only HEAD~1")
      (str/split-lines)))

(defn irrelevant-change?
  [change regexes]
  (some? (some #(re-matches % change) regexes)))

(defn relevant?
  [change-set regexes]
  (some? (some #(not (irrelevant-change? % regexes)) change-set)))

(defn main
  [halting-cmd]
  (let [{:keys [skip-if-only]} config
        changed-files          (get-changes)]
    (if (relevant? changed-files skip-if-only)
      (println "Proceeding with CI run.")
      (do
        (println "Irrelevant changes - skipping CI run.")
        (tasks/shell halting-cmd)))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [halting-cmd (first *command-line-args*)]
    (when-not halting-cmd
      (println "Please specify a command to short the CI run.")
      (System/exit 1))
    (main halting-cmd)))

(comment
  (def regexes
    [#".*.md$"
     #".*.clj$"]) ; ignore clojure files

  (irrelevant-change? "src/file.png" regexes)

  (re-matches #".*.clj$" "src/file.clj.dfff")

  (relevant? ["src/file.clj"] regexes))
