#!/usr/bin/env bb
(ns ld-stats.core
  (:require [org.httpkit.client :as client]
            [cheshire.core :as json]
            [clojure.data.csv :as csv]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as s]))

(def supported-formats #{:json :csv})

(defn println-err [& s]
  (binding [*out* *err*]
    (apply println s)))

(defn epoch->timestamp_str [e]
  (str (java.time.Instant/ofEpochMilli e)))

(defn datetime-months-before-now [months]
  (-> (java.time.ZonedDateTime/now (java.time.ZoneId/of "Z"))
      (.minus months java.time.temporal.ChronoUnit/MONTHS)))

(defn parse-datetime [s]
  (java.time.ZonedDateTime/parse s))

(defn datetime-is-before? [zdt1 zdt2]
  (.isBefore zdt1 zdt2))

(defn fail-on-non-200-status [{:keys [status body opts] :as res}]
  (let [{:keys [url method]} opts]
   (if-not (= status 200)
     (throw (ex-info
             (str "Response has non-200 status: " status " ! (" method ", " url")")
             {:status status
              :body body
              :response res}))
     res)))

(defn ld-client-opts [ld-api-key]
  {:headers {"Authorization" ld-api-key
                               "LD-API-Version" "beta"}})

(defn parse-ok-json-response [res]
  (-> res
      fail-on-non-200-status
      :body
      (json/parse-string true)))

(defn api-get-feature-flags [ld-api-key]
  (client/get "https://app.launchdarkly.com/api/v2/flags/default" (ld-client-opts ld-api-key)))

(defn api-get-all-stats [ld-api-key]
  (client/get "https://app.launchdarkly.com/api/v2/code-refs/statistics/default" (ld-client-opts ld-api-key)))

(defn build-usage-stats-by-ff-keys [all-stats-response]
  (->> all-stats-response
       :flags
       (map (fn [[k v]]
              [k (->> v
                      (map :name)
                      frequencies)]))
       (into {})))

(defn extract-ff-data [{:keys [key name environments]} ld-env]
  {:key key
   :name name
   :last-modified (-> environments
                              (get-in [ld-env :lastModified])
                              epoch->timestamp_str)})

(defmulti ffs->export-str (fn [format & _] format))

(defmethod ffs->export-str :csv
  [_ ffs]
  (with-open [sw (java.io.StringWriter.)]
    (doall (for [ff ffs]
             (let [{:keys [key name last-modified usages]} ff]
               (csv/write-csv sw [[key name last-modified usages]]))))
    (s/join "\n" ["Key,Name,Last modified,Usages"
                  (.toString sw)])))

(defmethod ffs->export-str :json
  [_ ffs]
  (-> (map #(select-keys % [:key :name :last-modified :usages]) ffs)
      (json/encode {:pretty true})))

(defn export-to-csv [ld-api-key environment format filename modified-before-months without-usages-only?]
  (println-err "Fetching FFs. [modified-before-months=" modified-before-months ", without-usages-only?=" without-usages-only? "]")
  (let [millis-before (System/currentTimeMillis)
        [feature-flags-response all-stats-response] (->> [(api-get-feature-flags ld-api-key) (api-get-all-stats ld-api-key)]
                                                         (map #(parse-ok-json-response (deref %))))
        usage-stats-by-ff-keys (build-usage-stats-by-ff-keys all-stats-response)
        mapped-ffs (->> feature-flags-response
                        :items
                        (filter #(false? (get-in % [:environments environment :archived])))
                        (sort-by #(get-in % [:environments environment :lastModified]) <)
                        (map #(extract-ff-data % environment))
                        (map #(let [{:keys [key]} %
                                    ff-kw (keyword key)
                                    usages (or (get usage-stats-by-ff-keys ff-kw) {})]
                                (assoc % :usages usages))))
        max-modif-time (datetime-months-before-now modified-before-months)
        filtered-ffs (->> mapped-ffs
                          (filter #(if without-usages-only?
                                     (empty? (:usages %))
                                     true))
                          (filter #(datetime-is-before? (parse-datetime (:last-modified %))
                                                        max-modif-time)))
        export-str (ffs->export-str format filtered-ffs)]
    (println-err (str "Finished. Elapsed time: " (- (System/currentTimeMillis) millis-before) " ms"))
    (if filename
      (spit filename export-str)
      (println export-str))))

(def cli-options
  [["-k" "--ld-api-key APIKEY" "LaunchDarkly API key. May also be provided via environment property LD_API_KEY."
    :validate [#(not-empty %) "Must be a non-empty string"]]
   ["-e" "--environment ENVIRONMENT" "LaunchDarkly environment, such as \"production\""
    :validate [#(not-empty %) "Must be non-empty string"]]
   ["-m" "--modified-before-months MONTHS" "Only include FFs which has not been modified in previous MONTHS months."
    :default 1
    :parse-fn #(Long/parseLong %)
    :validate [#(>= % 0) "Must be number greater to or equal to 0"]]
   ["-w" "--without-usages-only" "Only include FFs with no code usages."
    :default false]
   ["-f" "--format FORMAT" "Output format. Either \"csv\" or \"json\"."
    :default :csv
    :parse-fn keyword
    :validate [#(contains? supported-formats %) (str "Must be one of supported formats : " supported-formats)]]
   ["-o" "--output-file FILENAME" "Output to file with provided FILENAME. If not specified, output is sent to STDOUT."
    :validate [#(not-empty %) "Must be a non-empty string"]]
   ["-h" "--help" "Shows this usage information."]
   ["-d" "--debug" "Debug logging. Print more detailed errors, including API responses."]])

(defn println-exception-info [e debug]
  (println-err "ERROR:" (ex-message e))
  (if debug
    (println-err e)
    (println-err "Use --debug to see more detailed error information")))

(defn -main [& args]
  (let [{:keys [options summary errors]} (parse-opts args cli-options)
        {:keys [without-usages-only modified-before-months help output-file ld-api-key debug format environment]} options
        effective-ld-api-key (or ld-api-key (System/getenv "LD_API_KEY"))]  
    (try
      (cond
        help
        (println summary)

        errors
        (do (println-err "Errors in command line arguments: " errors)
            (System/exit 1))

        (nil? effective-ld-api-key)
        (throw (ex-info "LaunchDarkly key not present. Specify it either via --ld-api-key cli parameter or LD_API_KEY env property." {}))

        (nil? environment)
        (throw (ex-info "Environment not provided" {}))

        :else
        (export-to-csv effective-ld-api-key (keyword environment) format output-file modified-before-months without-usages-only))
      (catch Exception e
        (println-exception-info e debug)
        (System/exit 1)))))