(ns emem.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [markdown.core :as md]
            [hiccup.core :as hi])
  (:use emem.util emem.html)
  (:import [java.io File BufferedReader ByteArrayOutputStream]
           [java.lang String]
           [clojure.lang PersistentArrayMap PersistentVector])
  (:gen-class))

(def ^:private cli-opts
  "Specification for the command-line options."
  [["-o" "--output HTML"          "specify output file" :id :out] ;
   ["-i" "--install-resources"    "install the resources"]
   ["-n" "--no-resources"         "build full HTML; don't install resources"]
   ["-c" "--continuous"           "run in continuous build mode"]
   ["-f" "--refresh MILLISECONDS" "time between rebuilds (default: 200)"]
   ["-s" "--standalone"           "embed the CSS data with the output files"]

   ["-w" "--raw"   "emit 1:1 Markdown-HTML equivalence"]
   ["-p" "--plain" "build plain HTML; don't use CSS and JS"]
   ["-m" "--merge" "merge and process the files into a single output"]

   [nil "--title TEXT"      "document title"]
   [nil "--header TEXT"     "document header"]
   ["-t" "--titlehead TEXT" "the same as --title TEXT --header TEXT"]

   ["-C" "--css CSS"     "specify alternative main CSS resource"]
   ["-S" "--style STYLE" "specify alternative style for the syntax highlighter"]
   ["-L" "--list-styles" "list available styles for the syntax highlighter"]

   ["-v" nil         "increase verbosity"
    :id :verbosity :default 0
    :assoc-fn (fn [m k _] (update-in m [k] inc))]
   ["-V" "--version" "display program version"]
   ["-h" "--help"    "display this help"]])

(defn- display-usage
  "Displays program usage."
  [text]
  (println
   (s/join \newline
           ["Usage: emem [OPTION]... [MARKDOWN_FILE]..."
            ""
            "Options:"
            text
            ""
            "See https://github.com/ebzzry/emem for more information"])))

(defn- display-errors
  "Displays the errors encountered during command parsing."
  [errors]
  (println
   (str "The following errors occurred:\n\n"
        (s/join \newline errors))))

(defn- version
  "Displays program version."
  []
  (with-open [in (find-resource "etc/VERSION")]
    (let [ver (slurp in)]
      (spit *out* ver))))

(defn- get-styles
  "Returns the available styles for the syntax highlighter."
  []
  (sort compare (remove #{"main" "ewan"}
                 (map root (get-resources "static/css")))))

(defn- list-styles
  "Displays the available styles for the syntax highlighter."
  []
  (doseq [style (get-styles)]
    (println style)))

(defn- inputs-ok?
  "Verifies that all inputs exist."
  [inputs opts]
  (msg "[*] Verifying inputs..." 1 (verb opts))
  (or (when-let [[in & _] inputs]
        (= in *in*))
      (files-exist? inputs)))

(defn- write-html
  "Writes the HTML to file."
  [opts args]
  (msg "[*] Writing output..." 1 (verb opts))
  (let [output (out opts)
        f (fn [out]
            (.write out (html opts args))
            (flush))]
    (if (= output *out*)
      (f *out*)
      (with-open [out (io/writer output)]
        (f out)))))

(defn- write-pdf [] nil)

(defn- stage
  "Verifies options, and conditionalizes installation of resources."
  [opts args f exit]
  (msg "[*] Setting up stage..." 1 (verb opts))
  (cond
    (:install-resources opts) (copy-resources opts)

    (inputs-ok? args opts)
    (do (or (:raw opts)
            (:plain opts)
            (:no-resources opts)
            (in? args)
            (= (:out opts) "-")
            (= (:out opts) *out*)
            (copy-resources opts args))
        (f))

    :else (exit)))

(declare launch)

(defn- rebuild
  "Rebuilds the target if any of the input files are modified."
  [opts args]
  (msg "[*] Rebuilding..." 1 (verb opts))
  (let [times (mod-times args)
        refresh (if-let [t (:refresh opts)] (read-string t) 200)
        options (merge-true opts :no-resources)]
    (with-out-str (print times))
    (Thread/sleep refresh)
    (if (not= times (mod-times args))
      (launch options args)
      (recur opts args))))

(defn- launch
  "Converts a Markdown file to HTML."
  [opts args]
  (let [options (merge-options
                 opts
                 (or (html-name (first args))
                     *out*))
        f (fn [inputs]
            (stage options inputs
                   #(write-html options inputs)
                   #(exit)))]
    (if (empty? args)
      (let [out (:out opts)]
        (if (and out (not (out? opts)) (not (= "-" out)))
          (do (or (:no-resources opts)
                  (when-not (:standalone opts)
                    (install-resources (:dir opts))))
              (f [*in*]))
          (f [*in*])))
      (do (f args)
          (and (:continuous opts)
               (rebuild opts args))))))

(defn- multi-launch
  "Invoke LAUNCH on ARGS. If either :continuous and :no-resources, or
  if force is true, parallelize LAUNCH on arguments."
  [opts args]
  (if (empty? args)
    (launch opts args)
    (doseq [arg args]
      (if (and (:continuous opts)
               (:no-resources opts))
        (future (launch opts [arg]))
        (launch opts [arg])))))

(defn- expand-md
  "Returns a vector of absolute paths of Markdown files, found in
  traversing PATHS, including directories."
  [paths]
  (expand paths "md"))

(defn- dump
  "Converts Markdown inputs to HTML."
  [opts args]
  (let [argsn (count args)
        args? (> argsn 1)
        xargs (expand-md args)]
    (cond
      ;; install resources
      (:install-resources opts)
      (exit #(install-resources (:dir opts)))

      ;; merge
      (and args? (:merge opts))
      (launch opts xargs)

      ;; multi parallel
      (or (and args? (common-directory? args))
          (and (= argsn 1)) (dir? (first args)))
      (let [dir (if (:out opts)
                  (abs-parent (:out opts))
                  (abs-parent (first args)))]
        (or (:no-resources opts)
            (when-not (:standalone opts)
              (install-resources (:dir opts))))
        (multi-launch (merge-true opts :no-resources)
                      xargs))

      ;; multi serial
      args?
      (multi-launch opts xargs)

      :else
      (multi-launch opts xargs))))

(defn convert
  "Converts Markdown inputs to HTML.

  Options:
  :out String                specify output file
  :install-resources Boolean install the resource files only
  :no-resources Boolean      build full HTML; don't install resources
  :standalone Boolean        embed the CSS data with the output files
  :raw Boolean               emit 1:1 Markdown-HTML equivalence
  :plain Boolean             build plain HTML; don't use CSS and JS
  :merge Boolean             merge and process the files into one file
  :title String              document title
  :header String             document header
  :titlehead String          the same as :title String :header String
  :css String                specify alternative main CSS resource
  :style String              specify alternative style for the syntax highlighter"
  [in & args]
  (cond
    ;; (convert "README.md")
    (and (zero? (count args)) (string? in))
    (convert [in] :out (html-name in))

    ;; (convert "README.md" "foo.html")
    (and (= (count args) 1) (every? string? [in (first args)]))
    (convert [in] :out (first args))

    ;; (convert ["foo.md" "bar.md" "baz.md"]
    ;;          ["mu.html" "ka.html" "mo.html"])
    (and (vector? in) (vector? (first args)))
    (let [v1 in
          v2 (first args)]
      (and (= (count v1) (count v2))
           (doseq [[md html] (zipmap v1 v2)]
             (convert md html))))

    ;; (convert ["README.md" "TODO.md"])
    ;; (convert [] ...)
    (vector? in)
    (let [options (apply sorted-map args)]
      (dump options in))))

(defn -main
  [& args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args cli-opts)]
    (cond
      errors (exit #(display-errors errors) 1)
      (:help options) (exit #(display-usage summary))
      (:version options) (exit version)
      (:list-styles options) (exit list-styles)

      :else (dump options arguments))))
