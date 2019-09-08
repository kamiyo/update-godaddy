(defproject update-godaddy "1.0.1"
  :description "Update DNS entries on GoDaddy based on detected IP"
  :url "https://github.com/kamiyo/update-godaddy"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-http "3.10.0"]
                 [org.clojure/data.json "0.2.6"]
                 [overtone/at-at "1.2.0"]
                 [environ "1.1.0"]
                 [org.clojure/tools.cli "0.4.2"]]
  :main update-godaddy.core
  :target-path "target/%s/"
  :profiles {:uberjar {:aot :all
                       :omit-source true}}
  :repl-options {:init-ns update-godaddy.core})
