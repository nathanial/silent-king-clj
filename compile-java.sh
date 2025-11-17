#!/bin/bash
# Compile Java sources to classes directory

set -e

echo "Compiling Java sources..."
mkdir -p classes

# Use Clojure to compile Java (it has access to javac via the JDK)
clojure -M -e "
(import '[javax.tools ToolProvider DiagnosticCollector])
(let [compiler (ToolProvider/getSystemJavaCompiler)
      diagnostics (DiagnosticCollector.)]
  (if (nil? compiler)
    (throw (Exception. \"No Java compiler available. Please ensure JDK is installed.\"))
    (let [file-manager (.getStandardFileManager compiler diagnostics nil nil)
          source-file (java.io.File. \"src/java/silentking/noise/FastNoiseLite.java\")
          sources (.getJavaFileObjectsFromFiles file-manager [source-file])
          options [  \"-d\" \"classes\"]
          task (.getTask compiler nil file-manager diagnostics options nil sources)]
      (if (.call task)
        (println \"Java compilation successful!\")
        (do
          (doseq [diag (.getDiagnostics diagnostics)]
            (println (.toString diag)))
          (throw (Exception. \"Java compilation failed\")))))))
"

echo "Java compilation complete!"
