param(
    [string]$PythonCommand = 'python',
    [string]$OutputDirectory = (Join-Path ([System.IO.Path]::GetTempPath()) 'fitness-history-benchmark')
)
$ErrorActionPreference = 'Stop'
$benchRepo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$benchOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $benchOutput -Force | Out-Null
$benchClasses = Join-Path $benchOutput 'classes'
New-Item -ItemType Directory -Path $benchClasses -Force | Out-Null

Push-Location $benchRepo
try {
    & (Join-Path $benchRepo 'gradlew.bat') compileDebugKotlin --offline --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Compile failed; verify JDK 17, SDK and cached dependencies.' }
} finally { Pop-Location }

& $PythonCommand (Join-Path $PSScriptRoot 'generate-history.py') $benchOutput
if ($LASTEXITCODE -ne 0) { throw 'Fixture generation failed.' }

$benchCacheRoot = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$benchCacheRoot = Join-Path $benchCacheRoot 'caches/modules-2/files-2.1'
$benchCoordinates = @(
    'org.jetbrains.kotlin/kotlin-stdlib/2.1.20',
    'org.jetbrains.kotlinx/kotlinx-serialization-json-jvm/1.8.1',
    'org.jetbrains.kotlinx/kotlinx-serialization-core-jvm/1.8.1'
)
$benchJars = foreach ($coordinate in $benchCoordinates) {
    $benchDependencyFiles = @(Get-ChildItem -Path (Join-Path $benchCacheRoot "$coordinate/*/*.jar") -File)
    if ($benchDependencyFiles.Count -ne 1) { throw "Expected one cached dependency for $coordinate" }
    $benchDependencyFiles[0].FullName
}
$benchClassPath = (@((Join-Path $benchRepo 'app/build/tmp/kotlin-classes/debug'), $benchClasses) + $benchJars) -join ';'
$benchJavac = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { (Get-Command javac).Source }
$benchJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java).Source }
& $benchJavac -encoding UTF-8 -cp $benchClassPath -d $benchClasses (Join-Path $PSScriptRoot 'FutureBench.java') (Join-Path $PSScriptRoot 'BoundaryProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed.' }
& $benchJava -Xmx1g -cp $benchClassPath FutureBench (Join-Path $benchOutput '1y.json') (Join-Path $benchOutput '5y.json') (Join-Path $benchOutput '10y.json') |
    Set-Content -LiteralPath (Join-Path $benchOutput 'desktop-jvm.json') -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw 'Benchmark failed.' }
& $benchJava -cp $benchClassPath BoundaryProbe (Join-Path $benchOutput '1y.json') |
    Set-Content -LiteralPath (Join-Path $benchOutput 'boundary.txt') -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw 'Boundary probe failed.' }
Write-Output "Results written to $benchOutput"
