$ScriptDir = Split-Path $MyInvocation.MyCommand.Path
Set-Location $ScriptDir\..

Write-Host "Compiling Java sources..."
if (!(Test-Path -Path "classes")) {
    New-Item -ItemType Directory -Force -Path "classes" | Out-Null
}

# Use Clojure to compile Java (it has access to javac via the JDK)
# We write to a temp file to avoid shell escaping issues with quotes
$clojureCode = @'
(import '[javax.tools ToolProvider DiagnosticCollector])
(let [compiler (ToolProvider/getSystemJavaCompiler)
      diagnostics (DiagnosticCollector.)]
  (if (nil? compiler)
    (throw (Exception. "No Java compiler available. Please ensure JDK is installed."))
    (let [file-manager (.getStandardFileManager compiler diagnostics nil nil)
          source-file (java.io.File. "src/java/silentking/noise/FastNoiseLite.java")
          sources (.getJavaFileObjectsFromFiles file-manager [source-file])
          options ["-d" "classes"]
          task (.getTask compiler nil file-manager diagnostics options nil sources)]
      (if (.call task)
        (println "Java compilation successful!")
        (do
          (doseq [diag (.getDiagnostics diagnostics)]
            (println (.toString diag)))
          (throw (Exception. "Java compilation failed")))))))
'@

$tempFile = [System.IO.Path]::GetTempFileName() + ".clj"
Set-Content -Path $tempFile -Value $clojureCode

try {
    clojure -M $tempFile
}
finally {
    if (Test-Path $tempFile) {
        Remove-Item -Path $tempFile -ErrorAction SilentlyContinue
    }
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "Java compilation complete!"
} else {
    Write-Error "Java compilation failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

