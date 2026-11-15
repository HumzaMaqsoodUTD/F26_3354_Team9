# Compiles the app and all tests (including the original AssignmentServiceTest) and runs them with JUnit.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$jar = "..\junit-tests\lib\junit-platform-console-standalone-1.11.4.jar"
if (Test-Path build-test) { Remove-Item build-test -Recurse -Force }
New-Item -ItemType Directory -Path build-test -Force | Out-Null
New-Item -ItemType Directory -Path evidence -Force | Out-Null
$files = Get-ChildItem -Path "src", "..\junit-tests\src", "test", "..\junit-tests\test" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 17 -encoding UTF-8 -cp $jar -d build-test $files
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -jar $jar execute --class-path build-test --scan-class-path --disable-ansi-colors --details tree --reports-dir evidence\junit | Tee-Object -FilePath evidence\junit-output.txt
