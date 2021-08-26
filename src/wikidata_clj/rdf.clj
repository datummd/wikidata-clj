(ns wikidata-clj.rdf
  (:require [wikidata-clj.data :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.core.async :as async])
  (:import [java.io BufferedWriter]))

;; Map mapping files containing list of wikidata concepts to corresponding biomedical type 
;; like disease vs drug 
(def concept-list-file-map {"disease-concepts.csv" :Disease
                            "symptom-concepts.csv" :Symptom
                            "treatment-concepts.csv" :Treatment
                            "chemical-concepts.csv" :Chemical-Compound
                            "medicaation-concepts.csv" :Medication
                            "protein-concepts.csv" :Protein
                            "trials-concepts.csv" :Clinical-Trial
                            "bioprocess-concepts.csv" :Biological-Process
                            "protein-fragment-concepts.csv" :Protein-Fragment})

(def n-cpu (.availableProcessors (Runtime/getRuntime)))

(defn extract-wikidata-bio-concepts-rdf
  "Filters out biomedical concept definitions from wikidata bzip2 file.
   `bz2-filename` - filename of the wikidata bzip2 file"
  [bz2-filename]
  (let [biomedical-concept-type
        (reduce
         (fn [concept-map file]
           (->> (io/resource (key file))
                (io/reader)
                (line-seq)
                (drop 1)
                (reduce (fn [acc line]
                          (let [txt (str "<" (str/trim line) ">")]
                            (assoc acc txt (val file))))
                        concept-map)))
         biomedical-concept-type
         concept-list-file-map)

        concept-types (into #{} (keys biomedical-concept-type))

        in (async/chan)
        out (async/chan)

        graph "<http://www.wikidata.org>"

        wikidata-handler (fn [line out*]
                           (async/thread
                             (let [[s p o & rest] (str/split line #"\s+")
                                   o (->> (concat [(str/trim o)] (butlast rest))
                                          (str/join " "))]
                               (if (and (= (get biomedical-property-type (str/trim p) "") :instance-of) (contains? concept-types (str/trim o)))
                                 (async/>!! out (str (str/trim s) " " (:instance-of KG-biomedical-property-by-type) " " (str/trim o) " " graph " .\r\n")))
                               (async/close! out*))))]
    (async/thread
      (with-open [wtr (BufferedWriter. (io/writer "resources/wikidata-concepts.nq" :append true))]
        (loop []
          (when-let [v (async/<!! out)]
            (.write wtr v))
          (recur))))

    (async/pipeline-async (* n-cpu 2) out wikidata-handler in)

    (async/thread
      (with-open [rdr (bzip2-reader bz2-filename)]
        (doseq [line (line-seq rdr)]
          (async/>!! in line))))))

;; (defn extract-wikidata-bio-properties-rdf
;;   ""
;;   [bz2-filename]
;;   ())
