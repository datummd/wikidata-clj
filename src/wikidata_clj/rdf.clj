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

(defn get-entity-id
  "extract wikidata entity-id from URI"
  [entity-uri]
  (let [entity-url (try (url (str/replace entity-uri #"[<>]" "")) (catch Exception e nil))]
    (if entity-url (second (re-find #"/(Q.*)$" (:path entity-url))))))

(defn extract-wikidata-bio-concepts-rdf
  "Filters out biomedical concept definitions from wikidata file.
   `filename` - filename of the wikidata file to extract from. You can also use a bzip2 file
                which will be slower but doesn't need large space."
  [filename]
  (if (.exists (io/file (str "resources/" filename)))
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
          out (async/chan 10000)

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
        (with-open [rdr (if (ends-with? filename ".bz2")
                          (bzip2-reader filename)
                          (-> (io/resource filename)
                              io/reader))]
          (doseq [line (line-seq rdr)]
            (async/>!! in line)))))))

(defn extract-wikidata-bio-properties-rdf
  "Filters out properties associated with wikidata biomedical concepts from the wikidata rdf file.
   `filename` - filename of the wikidata file. You can also use a bzip2 file which will be slower
                but doesn't need large amount of disk space."
  [filename]
  (if (.exists (io/file (str "resources/" filename)))
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
          out (async/chan 10000)

          graph "<http://www.wikidata.org>"

          concept-trie (reduce (fn [trie concept]
                                 (let [entity-id (get-entity-id (key concept))
                                       type-id (val concept)]
                                   (if entity-id
                                     (assoc-in trie (str/trim entity-id) {:$ true :type type-id})
                                     trie)))
                               {} biomedical-concept-type)
        
          concept-trie (->> "wikidata-concepts.nq"
                            io/resource
                            io/reader
                            line-seq
                            (reduce (fn [trie line]
                                      (let [[s p o & rest] (str/split line #"\s+")
                                            entity-id (get-entity-id (str/trim s))
                                            type-id (get biomedical-concept-type (str/trim o))]
                                        (if (and entity-id (= p "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>"))
                                          (assoc-in trie (str/trim entity-id) {:$ true :type type-id})
                                          trie)))
                                    (or concept-trie {})))

          wikidata-handler (fn [line out*]
                             (async/thread
                               (let [[s p o & rest] (str/split line #"\s+")
                                     o (->> (concat [(str/trim o)] (butlast rest))
                                            (str/join " "))
                                     entity-id (get-entity-id (str/trim s))]
                                 (if (or (get-in concept-trie (conj (into [] entity-id) :$))
                                         (get biomedical-concept-type (str/trim s)))
                                   (let [prop-type (get biomedical-property-type (str/trim p))]
                                     (cond
                                       (and (contains? #{:label :title :description} prop-type)
                                            (= "en" (second (str/split o #"@"))))
                                       (async/>!! out (str (str/trim s) " " (str/trim p) " " (str/trim o) " " graph " .\r\n"))

                                       (= prop-type :skos-exactmatch)
                                       (async/>!! out (str (str/trim s) " " (:skos-exactmatch KG-biomedical-property-by-type) " <" (get prefixes (get external-id-prefix-map (str/trim p))) (str/replace (str/trim o) #"\"" "") "> " graph " .\r\n"))

                                       (and prop-type (not (contains? #{:label :title :description} prop-type)))
                                       (async/>!! out (str (str/trim s) " " (get KG-biomedical-property-by-type prop-type) " " (str/trim o) " " graph " .\r\n")))))
                               
                                 (async/close! out*))))]
      (async/thread
        (with-open [wtr (BufferedWriter. (io/writer "resources/wikidata-properties.nq" :append true))]
          (loop []
            (when-let [v (async/<!! out)]
              (.write wtr v))
            (recur))))

      (async/pipeline-async (* n-cpu 2) out wikidata-handler in)

      (async/thread
        (with-open [rdr (if (ends-with? filename ".bz2")
                          (bzip2-reader bz2-filename)
                          (-> (io/resource filename)
                              io/reader))]
          (doseq [line (line-seq rdr)]
            (async/>!! in line)))))))
