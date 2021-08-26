(defproject wikidata-clj "0.1.0-SNAPSHOT"
  :description "Clojure application and tools for extraction of biomedical concepts and relationships from wikidata rdf file."
  :url "https://github.com/datummd/wikidata-clj"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "1.3.610"]
                 [org.apache.commons/commons-compress "1.20"]
                 [com.cemerick/url "0.1.1"]]
  :repl-options {:init-ns wikidata-clj.core})
