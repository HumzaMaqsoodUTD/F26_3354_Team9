# Compiles the app and starts the server at http://127.0.0.1:8000 (Windows PowerShell)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
if (Test-Path build) { Remove-Item build -Recurse -Force }
New-Item -ItemType Directory -Path build | Out-Null
$files = Get-ChildItem -Path "src", "..\junit-tests\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 17 -encoding UTF-8 -d build $files
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp build occupan.App
