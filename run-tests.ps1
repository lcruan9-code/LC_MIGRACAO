# Compila, executa os testes JUnit e gera o relatorio de cobertura JaCoCo.
# Uso:  powershell -ExecutionPolicy Bypass -File run-tests.ps1
# Requer apenas o JDK 8 no PATH (nao depende do Ant).

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
Set-Location $root

$out = Join-Path $root "build\test-run"
$classes = Join-Path $out "classes"
$testClasses = Join-Path $out "testclasses"
New-Item -ItemType Directory -Force $classes, $testClasses | Out-Null

$prod = "lib\flatlaf-3.4.jar;lib\slf4j-api-1.7.36.jar;lib\logback-classic-1.2.13.jar;lib\logback-core-1.2.13.jar;mysql-connector-java-5.1.36-bin.jar"
$testLibs = "lib\junit-4.13.2.jar;lib\hamcrest-core-1.3.jar"

# --- argfiles sem BOM (javac nao aceita BOM) ---
$srcList = Join-Path $out "src.txt"
$testList = Join-Path $out "test.txt"
[IO.File]::WriteAllLines($srcList, (Get-ChildItem -Recurse src -Filter *.java | ForEach-Object FullName))
[IO.File]::WriteAllLines($testList, (Get-ChildItem -Recurse test -Filter *.java | ForEach-Object FullName))

Write-Host "==> Compilando producao..." -ForegroundColor Cyan
javac -encoding UTF-8 -source 1.8 -target 1.8 -cp $prod -d $classes "@$srcList"
if ($LASTEXITCODE) { throw "Falha ao compilar src" }

Write-Host "==> Compilando testes..." -ForegroundColor Cyan
javac -encoding UTF-8 -source 1.8 -target 1.8 -cp "$prod;$testLibs;$classes" -d $testClasses "@$testList"
if ($LASTEXITCODE) { throw "Falha ao compilar testes" }

$tests = @(
  "config.AppConfigTest",
  "model.migration.MigrationReportTest",
  "model.migration.MigrationReportFormatterTest",
  "model.migration.StagingLoaderTest",
  "model.migration.MergeScriptBuilderTest",
  "model.migration.MigrationEngineTest"
)

$exec = Join-Path $out "jacoco.exec"
Write-Host "==> Executando JUnit (com agente JaCoCo)..." -ForegroundColor Cyan
java "-javaagent:lib\jacocoagent-0.8.11-runtime.jar=destfile=$exec" `
  -cp "$prod;$testLibs;$classes;$testClasses" org.junit.runner.JUnitCore $tests
$junitExit = $LASTEXITCODE

Write-Host "==> Gerando relatorio de cobertura..." -ForegroundColor Cyan
java -jar "lib\jacococli-0.8.11-nodeps.jar" report $exec `
  --classfiles $classes --sourcefiles src `
  --csv (Join-Path $out "coverage.csv") --html (Join-Path $out "jacoco-html") | Out-Null

Write-Host "Relatorio HTML: $out\jacoco-html\index.html"
exit $junitExit
