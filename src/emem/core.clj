(ns emem.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [markdown.core :as md]
            [hiccup.core :as hi]
            [emem.util :as u])
  (:import [java.io File BufferedReader ByteArrayOutputStream]
           [java.lang String]
           [clojure.lang PersistentArrayMap PersistentVector])
  (:gen-class))

(def ^:private cli-opts
  "Specification for the command-line options."
  [["-o" "--output HTML"          "output file" :id :out] ;
   ["-i" "--install-resources"    "install the resources"]
   ["-n" "--no-resources"         "build full HTML; don't install resources"]
   ["-c" "--continuous"           "run in continuous build mode"]
   ["-f" "--refresh MILLISECONDS" "time between rebuilds (default: 200)"]

   ["-w" "--raw"   "emit 1:1 Markdown-HTML equivalence"]
   ["-p" "--plain" "build plain HTML; don't use CSS and JS"]
   ["-m" "--merge" "merge and process the files into a single output"]

   [nil "--title TEXT"      "document title"]
   [nil "--header TEXT"     "document header"]
   ["-t" "--titlehead TEXT" "the same as --title TEXT --header TEXT"]

   ["-I" "--icon ICO"    "favicon resource"]
   ["-C" "--css CSS"     "CSS resource"]
   ["-S" "--style STYLE" "style id for the syntax highlighter"]
   ["-L" "--list-styles" "list available styles for the syntax highlighter"]

   ["-v" nil         "increase verbosity"
    :id :verbosity :default 0
    :assoc-fn (fn [m k _] (update-in m [k] inc))]
   ["-V" "--version" "display program version"]
   ["-h" "--help"    "display this help"]])

(def ^:private default-style
  "Default style for the syntax highlighter."
  "ewan")

(defn- verb
  "Provides default value for :verbosity option."
  [opts]
  (or (:verbosity opts) 0))

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
  (with-open [in (u/find-resource "etc/VERSION")]
    (let [ver (slurp in)]
      (spit *out* ver))))

(defn- get-styles
  "Returns the available styles for the syntax highlighter."
  []
  (sort compare (remove #{"main" "ewan"}
                 (map u/root (u/get-resources "static/css")))))

(defn- list-styles
  "Displays the available styles for the syntax highlighter."
  []
  (doseq [style (get-styles)]
    (println style)))

(defn- with-resources
  "Locates the resource files in the classpath."
  [res f dir]
  (doseq [[path _] (u/resources res)]
    (let [relative-path (subs path 1)
          file (str res "/" relative-path)]
      (f file (or dir (u/pwd))))))

(defn- copy-resources
  "Installs the files required by the HTML file."
  [opts & [args]]
  (u/msg "[*] Copying resources..." 1 (verb opts))
  (let [dir (-> (:out opts) u/parent* io/file)
        f (fn [file dir]
            (with-open [in (u/re-stream file)]
              (let [path (io/file dir file)]
                (io/make-parents (io/file dir file))
                (io/copy in (io/file dir file)))))]
    (with-resources "static" f dir)))

(defn- install-resources
  "Installs the HTML resources relative to PATH."
  ([]
   (install-resources (u/pwd)))
  ([path]
   (copy-resources {:out (or path (u/pwd))})))

(defn- inputs-ok?
  "Verifies that all inputs exist."
  [inputs opts]
  (u/msg "[*] Verifying inputs..." 1 (verb opts))
  (or (when-let [[in & _] inputs]
        (= in *in*))
      (u/files-exist? inputs)))

(defn- html-page
  "Wraps TEXT with HTML necessary for correct page display."
  [opts args text]
  (let [title (or (:title opts)
                  (:titlehead opts)
                  (when (u/in? args) "")
                  (when-let [line (u/first-line (first args))]
                    line))
        header (or (:header opts) (:titlehead opts))
        icon (or (:icon opts) "static/ico/glider.ico")
        css (or (:css opts) "static/css/main.css")
        style (str "static/css/"
                   (or (:style opts) default-style)
                   ".css")]
    (hi/html
     [:html
      [:head
       (u/quo title [:title title])
       [:meta {:charset "utf-8"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0, user-scalable=yes"}]
       ;; u/quo
       (when (not (:plain opts))
         (hi/html
          [:link {:rel "apple-touch-icon" :sizes "57x57" :href "static/ico/apple-touch-icon-57x57.png"}]
          [:link {:rel "apple-touch-icon" :sizes "60x60" :href "static/ico/apple-touch-icon-60x60.png"}]
          [:link {:rel "apple-touch-icon" :sizes "72x72" :href "static/ico/apple-touch-icon-72x72.png"}]
          [:link {:rel "apple-touch-icon" :sizes "76x76" :href "static/ico/apple-touch-icon-76x76.png"}]
          [:link {:rel "apple-touch-icon" :sizes "114x114" :href "static/ico/apple-touch-icon-114x114.png"}]
          [:link {:rel "apple-touch-icon" :sizes "120x120" :href "static/ico/apple-touch-icon-120x120.png"}]
          [:link {:rel "apple-touch-icon" :sizes "144x144" :href "static/ico/apple-touch-icon-144x144.png"}]
          [:link {:rel "apple-touch-icon" :sizes "152x152" :href "static/ico/apple-touch-icon-152x152.png"}]
          [:link {:rel "apple-touch-icon" :sizes "180x180" :href "static/ico/apple-touch-icon-180x180.png"}]
          [:link {:rel "icon" :type "image/png" :href "static/ico/favicon-32x32.png" :sizes "32x32"}]
          [:link {:rel "icon" :type "image/png" :href "static/ico/favicon-194x194.png" :sizes "194x194"}]
          [:link {:rel "icon" :type "image/png" :href "static/ico/favicon-96x96.png" :sizes "96x96"}]
          [:link {:rel "icon" :type "image/png" :href "static/ico/android-chrome-192x192.png" :sizes "192x192"}]
          [:link {:rel "icon" :type "image/png" :href "static/ico/favicon-16x16.png" :sizes "16x16"}]
          [:link {:rel "manifest" :href "static/ico/manifest.json"}]
          [:link {:rel "shortcut icon" :href "static/ico/favicon.ico"}]
          [:meta {:name "msapplication-TileColor" :content "#da532c"}]
          [:meta {:name "msapplication-TileImage" :content "static/ico/mstile-144x144.png"}]
          [:meta {:name "msapplication-config" :content "static/ico/browserconfig.xml"}]
          [:meta {:name "theme-color" :content "#ffffff"}]
          [:link {:rel "icon" :href icon :type "image/x-icon"}]
          [:link {:rel "stylesheet" :href css :media "all"}]
          (when-not (= (:style opts) "-")
            (hi/html
             [:link {:rel "stylesheet" :href style :media "all"}]
             [:script {:src "static/js/highlight.pack.js"}]
             [:script "hljs.initHighlightingOnLoad();"]))))]
      [:body
       (u/quo header [:h1 header])
       [:div {:id "content"}        
        text]]])))

(defn markdown
  "Returns a Markdown string converted to HTML."
  [str]
  (md/md-to-html-string str))

(defn- html
  "Converts Markdown inputs to HTML strings."
  [opts args]
  (let [text (if (:merge opts)
               (s/join (map #(markdown (slurp %)) args))
               (markdown (slurp (first args))))]
    (if (:raw opts)
      text
      (html-page opts args text))))

(defn- html-name
  "Returns the HTML name of PATH"
  [path]
  (if (empty? path)
    nil
    (str (u/abs-file-name path) ".html")))

(defn- write-html
  "Writes the HTML to file."
  [opts args]
  (u/msg "[*] Writing output..." 1 (verb opts))
  (let [output (u/out opts)
        f (fn [out]
            (.write out (html opts args))
            (flush))]
    (if (= output *out*)
      (f *out*)
      (with-open [out (io/writer output)]
        (f out)))))

(defn- stage
  "Verifies options, and conditionalizes installation of resources."
  [opts args f exit]
  (u/msg "[*] Setting up stage..." 1 (verb opts))
  (cond
    (:install-resources opts) (copy-resources opts)

    (inputs-ok? args opts)
    (do (or (:raw opts)
            (:plain opts)
            (:no-resources opts)
            (u/in? args)
            (= (:out opts) "-")
            (= (:out opts) *out*)
            (copy-resources opts args))
        (f))
    
    :else (exit)))

(declare launch)

(defn- rebuild
  "Rebuilds the target if any of the input files are modified."
  [opts args]
  (u/msg "[*] Rebuilding..." 1 (verb opts))
  (let [times (u/mod-times args)
        refresh (if-let [t (:refresh opts)] (read-string t) 200)
        options (u/merge-true opts :no-resources)]
    (with-out-str (print times))
    (Thread/sleep refresh)
    (if (not= times (u/mod-times args))
      (launch options args)
      (recur opts args))))

(defn- launch
  "Converts a Markdown file to HTML."
  [opts args]
  (let [options (u/merge-options
                 opts
                 (or (html-name (first args))
                     *out*))
        f (fn [inputs]
            (stage options inputs
                   #(write-html options inputs)
                   #(u/exit)))]
    (if (empty? args)
      (let [out (:out opts)]
        (if (and out (not (u/out? opts)) (not (= "-" out)))
          (do (or (:no-resources opts)
                  (install-resources out))
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
  (u/expand paths "md"))

(defn- dump
  "Converts Markdown inputs to HTML."
  [opts args]
  (let [argsn (count args)
        args? (> argsn 1)
        xargs (expand-md args)]
    (cond
      ;; install resources
      (:install-resources opts)
      (u/exit #(install-resources (:dir opts)))

      ;; merge
      (and args? (:merge opts))
      (launch opts xargs)

      ;; multi parallel
      (and args? (u/common-directory? args))
      (do  (install-resources (u/abs-parent (first args)))
           (multi-launch (u/merge-true opts :no-resources)
                         xargs))

      ;; multi serial
      args?
      (multi-launch opts xargs)

      :else (multi-launch opts xargs))))

(defn convert
  "Converts Markdown inputs to HTML.

  Options:
  :out String                output file
  :install-resources Boolean install the resource files only
  :no-resources Boolean      build full HTML; don't install resources
  :raw Boolean               emit 1:1 Markdown-HTML equivalence
  :plain Boolean             build plain HTML; don't use CSS and JS
  :merge Boolean             merge and process the files into one file
  :title String              document title
  :header String             document header
  :titlehead String          the same as :title String :header String
  :icon String               icon resource
  :css String                CSS resource
  :style String              style id for the syntax highlighter"
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
      errors (u/exit #(display-errors errors) 1)
      
      (:help options) (u/exit #(display-usage summary))
      (:version options) (u/exit version)
      (:list-styles options) (u/exit list-styles)

      :else (dump options arguments))))
