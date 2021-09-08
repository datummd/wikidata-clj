(ns wikidata-clj.data
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.core.async :as async])
  (:import [org.apache.commons.compress.compressors.bzip2 BZip2CompressorInputStream]))

(def default-ns (or (System/getenv "DEFAULT-NS") "http://datum.md/"))

(def prefixes
  {:rdfs "http://www.w3.org/2000/01/rdf-schema#"
   :rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
   :skos "http://www.w3.org/2004/02/skos/core#"
   :schema-org "https://schema.org/"
   :bioschemas "https://bioschemas.org/"
   :dbpedia "http://dbpedia.org/ontology/"
   :identifiers "http://identifiers.org/"
   :umls-mth "http://umls.nlm.nih.gov/MTH/"
   :umls-mesh "http://umls.nlm.nih.gov/MSH/"
   :umls-omim "http://umls.nlm.nih.gov/OMIM/"
   :umls-drugbank "http://umls.nlm.nih.gov/DRUGBANK/"
   :clinicaltrials-gov "http://data.linkedct.org/resource/trials/"
   :umls-icd10 "http://umls.nlm.nih.gov/ICD10/"
   :umls-icd10-cm "http://umls.nlm.nih.gov/ICD10CM/"
   :umls-icd9-cm "http://umls.nlm.nih.gov/ICD9CM/"
   :umls-nci "http://umls.nlm.nih.gov/NCI/"
   :umls-rxnorm "http://umls.nlm.nih.gov/RXNORM/"
   :umls-icpc2 "http://umls.nlm.nih.gov/ICPC2EENG/"
   :w3-vcard "http://www.w3.org/2006/vcard/ns#"
   :owl "http://www.w3.org/2002/07/owl#"})

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
   "<http://www.wikidata.org/prop/direct/P1692>" :skos-exactmatch   ;ICD-9-CM
   "<http://www.wikidata.org/prop/direct/P493>" :skos-exactmatch    ;ICD-9-CM
   "<http://www.wikidata.org/prop/direct/P1748>" :skos-exactmatch   ;NCI Thesaurus ID
   "<http://www.wikidata.org/prop/direct/P3345>" :skos-exactmatch   ;RxNORM ID
   "<http://www.wikidata.org/prop/direct/P667>" :skos-exactmatch    ;ICPC2 ID
   "<http://www.wikidata.org/prop/direct/P780>" :symptoms
   "<http://www.wikidata.org/prop/direct/P2175>" :medical-condition-treated
   "<http://www.wikidata.org/prop/direct/P2176>" :drug-used-for-treatment
   "<http://www.wikidata.org/prop/direct/P924>" :possible-treatment
   "<http://www.wikidata.org/prop/direct/P2293>" :genetic-association
   "<http://www.wikidata.org/prop/direct/P460>" :same-as
   "<http://www.wikidata.org/prop/direct/P361>" :part-of
   "<http://www.wikidata.org/prop/direct/P527>" :has-part
   "<http://www.w3.org/2000/01/rdf-schema#label>" :label
   "<http://www.w3.org/2004/02/skos/core#prefLabel>" :label
   "<http://www.w3.org/2004/02/skos/core#altLabel>" :label
   "<http://schema.org/name>" :title
   "<http://schema.org/description>" :description
   "<http://www.wikidata.org/prop/direct/P18>" :schema-image
   "<http://www.wikidata.org/prop/direct/P1813>" :short-name
   "<http://www.wikidata.org/prop/direct/P2067>" :schema-weight
   "<http://www.wikidata.org/prop/direct/P231>" :cas-number
   "<http://dbpedia.org/ontology/casNumber>" :cas-number
   "<http://www.wikidata.org/prop/direct/P638>" :pdb-structure-id
   "<http://www.wikidata.org/prop/direct/P3636>" :pdb-ligand-id
   "<http://www.wikidata.org/prop/direct/P661>" :chem-spider-id
   "<http://www.wikidata.org/prop/direct/P672>" :mesh-tree-code
   "<http://www.wikidata.org/prop/direct/P373>" :schema-category
   "<http://www.wikidata.org/prop/direct/P910>" :schema-category
   "<http://www.wikidata.org/prop/direct/P235>" :inchi-key
   "<http://www.wikidata.org/prop/direct/P234>" :inchi
   "<http://www.wikidata.org/prop/direct/P274>" :molecular-formula
   "<http://www.wikidata.org/prop/direct/P662>" :pubchem-id
   "<http://www.wikidata.org/prop/direct/P117>" :chemical-structure
   "<http://www.wikidata.org/prop/direct/P8224>" :chemical-structure
   "<http://www.wikidata.org/prop/direct/P227>" :gnd-id
   "<http://www.wikidata.org/prop/direct/P683>" :chebi
   "<http://www.wikidata.org/prop/direct/P2868>" :role
   "<http://www.wikidata.org/prop/direct/P769>" :schema-interactingdrug
   "<http://www.wikidata.org/prop/direct/P592>" :ChEMBL-ID
   "<http://www.wikidata.org/prop/direct/P665>" :KEGG-ID
   "<http://www.wikidata.org/prop/direct/P699>" :DOID
   "<http://www.wikidata.org/prop/direct/P1995>" :schema-speciality
   "<http://www.wikidata.org/prop/direct/P1889>" :differentFrom
   "<http://www.wikidata.org/prop/direct/P349>" :ndlid
   "<http://www.wikidata.org/prop/direct/P828>" :has-cause
   "<http://www.wikidata.org/prop/direct/P1542>" :has-effect
   "<http://www.wikidata.org/prop/direct/P923>" :diagnoses
   "<http://www.wikidata.org/prop/direct/P680>" :molecular-function
   "<http://www.wikidata.org/prop/direct/P682>" :biological-process})

(def KG-biomedical-property-by-type
  {:instance-of (str "<" (:rdf prefixes) "type>")
   :subclass-of (str "<" (:rdfs prefixes) "subClassOf>")
   :skos-exactmatch (str "<" (:skos prefixes) "exactMatch>")
   :symptoms (str "<" (:schema-org prefixes) "signOrSymptom>")
   :drug-used-for-treatment (str "<" (:schema-org prefixes) "drug>")
   :possible-treatment (str "<" (:schema-org prefixes) "possibleTreatment>")
   :medical-condition-treated (str "<" default-ns "medical_condition_treated>" )
   :genetic-association (str "<" (:bioschemas prefixes) "associatedDisease>")
   :same-as (str "<" (:schema-org prefixes) "sameAs>")
   :part-of (str "<" (:schema-org prefixes) "isPartOf>")
   :has-part (str "<" (:schema-org prefixes) "isPartOf>")
   :schema-image (str "<" (:schema-org prefixes) "image>")
   :schema-category (str "<" (:schema-org prefixes) "category>")
   :schema-weight (str "<" (:schema-org prefixes) "weight>")
   :schema-speciality (str "<" (:schema-org prefixes) "relevantSpecialty>")
   :schema-interactingdrug (str "<" (:schema-org prefixes) "interactingDrug>")
   :short-name "<http://www.lexinfo.net/ontology/2.0/lexinfo#abbreviation>"
   :pdb-structure-id (str "<" default-ns "has_PDB_structure_id>")
   :pdb-ligand-id (str "<" default-ns "has_PDB_ligand_id>")
   :mesh-tree-code (str "<" default-ns "has_mesh_tree_code>")
   :cas-number (str "<" (:dbpedia prefixes) "casNumber>")
   :inchi-key (str "<" (:bioschemas prefixes) "inChIKey>")
   :inchi (str "<" (:bioschemas prefixes) "inChI>")
   :chem-spider-id (str "<" default-ns "chemSpiderId>")
   :molecular-formula (str "<" (:bioschemas prefixes) "molecularFormula>")
   :molecular-function (str "<" (:bioschemas prefixes) "hasMolecularFunction>")
   :biological-process (str "<" (:bioschemas prefixes) "isInvolvedInBiologicalProcess>")
   :pubchem-id (str "<" default-ns "pubchemID>")
   :chemical-structure (str "<" default-ns "chemical_structure>")
   :gnd-id (str "<" (:dbpedia prefixes) "individualisedGnd>")
   :chebi (str "<" (:identifiers prefixes) "chebi>")
   :role (str "<" (:w3-vcard prefixes) "role>")
   :ChEMBL-ID (str "<" default-ns "ChEMBL_ID>")
   :KEGG-ID (str "<" default-ns "KEGG_ID>")
   :differentFrom (str "<" (:owl prefixes) "differentFrom>")
   :ndlid (str "<" (:dbpedia prefixes) "ndlId>")
   :DOID (str "<" (:identifiers prefixes) "doid>")
   :has-cause (str "<" default-ns "has_cause>")
   :has-effect (str "<" default-ns "has_effect>")
   :diagnoses (str "<" default-ns "diagnoses>")
   })

(def external-id-prefix-map
  {"<http://www.wikidata.org/prop/direct/P2892>" :umls-mth   ;UMLS CUI
   "<http://www.wikidata.org/prop/direct/P486>" :umls-mesh    ;MeSH ID
   "<http://www.wikidata.org/prop/direct/P492>" :umls-omim    ;OMIM ID
   "<http://www.wikidata.org/prop/direct/P715>" :umls-drugbank    ;Drugbank ID
   "<http://www.wikidata.org/prop/direct/P3098>" :clinicaltrials-gov   ;ClinicalTrials.gov ID
   "<http://www.wikidata.org/prop/direct/P494>" :umls-icd10    ; ICD10
   "<http://www.wikidata.org/prop/direct/P4229>" :umls-icd10-cm   ; ICD10-CM
   "<http://www.wikidata.org/prop/direct/P1692>" :umls-icd9-cm    ; ICD9-CM
   "<http://www.wikidata.org/prop/direct/P493>" :umls-icd9-cm     ; ICD9-CM
   "<http://www.wikidata.org/prop/direct/P3345>" :umls-rxnorm     ; RXNORM
   "<http://www.wikidata.org/prop/direct/P667>" :umls-icpc2       ; ICPC2EENG
   })

(defn bzip2-reader
  ""
  [filename]
  (-> filename
      io/file
      io/input-stream
      BZip2CompressorInputStream.
      io/reader))
