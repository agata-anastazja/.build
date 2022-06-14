(ns scripts.ci-configs
  (:require
   [clj-yaml.core :as yaml]
   [flatland.ordered.map :refer [ordered-map]]))

(defn run
  ([cmd-name cmd]
   (run cmd-name cmd nil))
  ([cmd-name cmd no-output-timeout]
   (let [base {:run {:name    cmd-name
                     :command cmd}}]
     (if no-output-timeout
       (assoc-in base [:run :no_output_timeout] no-output-timeout)
       base))))

(defn jvm
  []
  (ordered-map
   :docker            [{:image "circleci/clojure:openjdk-11-lein-2.9.8-bullseye"}]
   :working_directory "~/repo"
   :environment       {:LEIN_ROOT         "true"
                       :BABASHKA_PLATFORM "linux"}
   :resources_class   "large"
   :steps             [:checkout
                       (run "Pull Submodules" "git submodule init /n git submodule update")
                       {:restore_cache {:keys ["v1-dependencies-{{ checksum \"project.clj\" }}-{{ checksum \"deps.edn\" }}"
                                               "v1-dependencies-"]}}
                       (run "Install Clojure" "sudo script/install-clojure")
                       (run "Run JVM tests" "export BABASHKA_FEATURE_JDBC=true /n export BABASHKA_FEATURE_POSTGRESQL=true /n script/test /n script/run_lib_tests")
                       (run "Run as lein command" ".circleci/script/lein")
                       (run "Create uberjar" "mkdir -p /tmp/release\nscript/uberjar\nVERSION=$(cat resources/BABASHKA_VERSION)\njar=target/babashka-$VERSION-standalone.jar\ncp $jar /tmp/release
java -jar $jar script/reflection.clj\nreflection=\"babashka-$VERSION-reflection.json\"\njava -jar \"$jar\" --config .build/bb.edn --deps-root . release-artifact \"$jar\"
java -jar \"$jar\" --config .build/bb.edn --deps-root . release-artifact \"$reflection\"")
                       {:store_artifacts {:path "/tmp/release"
                                          :destination "release"}}
                       {:save_cache {:paths ["~/.m2"]
                                     :key "v1-dependencies-{{ checksum \"project.clj\" }}-{{ checksum \"deps.edn\" }}"}}]))

(defn linux
  []
  (ordered-map
   :docker            [{:image "circleci/clojure:openjdk-11-lein-2.9.8-bullseye"}]
   :working_directory "~/repo"
   :environment       {:LEIN_ROOT         "true"
                       :GRAALVM_VERSION   "22.1.0"
                       :GRAALVM_HOME      "/home/circleci/graalvm-ce-java11-22.1.0"
                       :BABASHKA_PLATFORM "linux"
                       :BABASHKA_TEST_ENV "native"
                       :BABASHKA_XMX      "-J-Xmx6500m"}
   :resource_class    "large"
   :steps             [:checkout
                       (run "Pull Submodules" "git submodule init\ngit submodule update")
                       {:restore_cache
                        {:keys ["linux-{{ checksum \"project.clj\" }}-{{ checksum \".circleci/config.yml\" }}"]}}
                       (run "Install native dev tools"
                            "sudo apt-get update\nsudo apt-get -y install build-essential zlib1g-dev")
                       (run "Download GraalVM" "script/install-graalvm")
                       (run "Build binary" "script/uberjar\nscript/compile" "30m")
                       (run "Run tests" "script/test\nscript/run_lib_tests")
                       (run "Release" ".circleci/script/release")
                       {:persist_to_workspace {:root  "/tmp"
                                               :paths ["release"]}}
                       {:save_cache {:paths ["~/.m2" "~/graalvm-ce-java11-22.1.0"]
                                     :key
                                     "linux-{{ checksum \"project.clj\" }}-{{ checksum \".circleci/config.yml\" }}"}}
                       {:store_artifacts {:paths       "/tmp/release"
                                          :destination "release"}}
                       (run "Publish artifact link to Slack" "./bb .circleci/script/publish_artifact.clj || true")]))

(def config
  (ordered-map
   :version   2.1
   :commands
   {:setup-docker-buildx
    {:steps
     [{:run
       {:name    "Create multi-platform capabale buildx builder"
        :command
        "docker run --privileged --rm tonistiigi/binfmt --install all\ndocker buildx create --name ci-builder --use"}}]}}
   :jobs      (ordered-map
               :jvm   (jvm)
               :linux (linux))
   :workflows (ordered-map
               :version 2
               :ci      {:jobs ["jvm"
                                "linux"
                                "linux-static"
                                "mac"
                                "linux-aarch64"
                                "linux-aarch64-static"
                                {:deploy {:filters  {:branches {:only "master"}}
                                          :requires ["jvm" "linux"]}}
                                {:docker {:filters  {:branches {:only "master"}}
                                          :requires ["linux" "linux-static" "linux-aarch64"]}}]})))

(comment
  (spit "foo.yml"
        (yaml/generate-string config
                              :dumper-options
                              {:flow-style :block})))
