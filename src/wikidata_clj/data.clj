(ns wikidata-clj.data
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.core.async :as async])
  (:import [org.apache.commons.compress.compressors.bzip2 BZip2CompressorInputStream]))

(def prefixes
  {:rdfs "http://www.w3.org/2000/01/rdf-schema#"
   :rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
   :skos "http://www.w3.org/2004/02/skos/core#"
   :schema-org "https://schema.org/"
   :bioschemas "https://bioschemas.org/"
   :umls-mth "http://umls.nlm.nih.gov/MTH/"
   :umls-mesh "http://umls.nlm.nih.gov/MSH/"
   :umls-omim "http://umls.nlm.nih.gov/OMIM/"
   :umls-drugbank "http://umls.nlm.nih.gov/DRUGBANK/"
   :clinicaltrials-gov "http://data.linkedct.org/resource/trials/"
   :umls-icd10 "http://umls.nlm.nih.gov/ICD10/"
   :umls-icd10-cm "http://umls.nlm.nih.gov/ICD10CM/"})

(def biomedical-concept-type
  {"<http://www.wikidata.org/entity/Q12136>" :Disease
   "<http://www.wikidata.org/entity/Q11173>" :Chemical-Compound
   "<http://www.wikidata.org/entity/Q12140>" :Medication
   "<http://www.wikidata.org/entity/Q179661>" :Treatment
   "<http://www.wikidata.org/entity/Q169872>" :Symptom
   "<http://www.wikidata.org/entity/Q7187>" :Gene
   "<http://www.wikidata.org/entity/Q8054>" :Protein
   "<http://www.wikidata.org/entity/Q30612>" :Clinical-Trial
   "<http://www.wikidata.org/entity/Q2996394>" :Biological-Process
   "<http://www.wikidata.org/entity/Q930752>" :Medical-Speciality
   "<http://www.wikidata.org/entity/Q78782478>" :Protein-Fragment})

(def biomedical-property-type
  {"<http://www.wikidata.org/prop/direct/P31>" :instance-of
   "<http://www.wikidata.org/prop/direct/P279>" :subclass-of
   "<http://www.wikidata.org/prop/direct/P2888>" :skos-exactmatch   
   "<http://www.wikidata.org/prop/direct/P2892>" :skos-exactmatch   ;UMLS CUI
   "<http://www.wikidata.org/prop/direct/P486>" :skos-exactmatch    ;MeSH ID
   "<http://www.wikidata.org/prop/direct/P492>" :skos-exactmatch    ;OMIM ID
   "<http://www.wikidata.org/prop/direct/P715>" :skos-exactmatch    ;Drugbank ID
   "<http://www.wikidata.org/prop/direct/P3098>" :skos-exactmatch   ;ClinicalTrials.gov ID
   "<http://www.wikidata.org/prop/direct/P494>" :skos-exactmatch    ;ICD-10
   "<http://www.wikidata.org/prop/direct/P4229>" :skos-exactmatch   ;ICD-10-CM
   "<http://www.wikidata.org/prop/direct/P780>" :symptoms
   "<http://www.wikidata.org/prop/direct/P2176>" :drug-used-for-treatment
   "<http://www.wikidata.org/prop/direct/P2293>" :genetic-association
   "<http://www.wikidata.org/prop/direct/P460>" :same-as
   "<http://www.wikidata.org/prop/direct/P361>" :part-of
   "<http://www.wikidata.org/prop/direct/P527>" :has-part
   "<http://www.w3.org/2000/01/rdf-schema#label>" :label
   "<http://www.w3.org/2004/02/skos/core#prefLabel>" :label
   "<http://www.w3.org/2004/02/skos/core#altLabel>" :label
   "<http://schema.org/name>" :title
   "<http://schema.org/description>" :description
   "<http://www.wikidata.org/prop/direct/P1813>" :short-name
   "<http://www.wikidata.org/prop/direct/P2067>" :schema-weight
   "<http://dbpedia.org/ontology/casNumber>" :cas-number})

(def KG-biomedical-property-by-type
  {:instance-of (str "<" (:rdf prefixes) "type>")
   :subclass-of (str "<" (:rdfs prefixes) "subClassOf>")
   :skos-exactmatch (str "<" (:skos prefixes) "exactMatch>")
   :symptoms (str "<" (:schema-org prefixes) "signOrSymptom>")
   :drug-used-for-treatment (str "<" (:schema-org prefixes) "drug>")
   :genetic-association (str "<" (:bioschemas prefixes) "associatedDisease>")
   :same-as (str "<" (:schema-org prefixes) "sameAs>")
   :part-of (str "<" (:schema-org prefixes) "isPartOf>")
   :has-part (str "<" (:schema-org prefixes) "isPartOf>")
   :schema-weight (str "<" (:schema-org prefixes) "weight>")
   :short-name "<http://www.lexinfo.net/ontology/2.0/lexinfo#abbreviation>"})

(defn bzip2-reader
  ""
  [filename]
  (-> filename
      io/file
      io/input-stream
      BZip2CompressorInputStream.
      io/reader))
