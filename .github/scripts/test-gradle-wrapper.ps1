$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repositoryRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$wrapper = Join-Path $repositoryRoot 'gradle-plugin/gradlew.bat'
$originalJavaHome = $env:JAVA_HOME

function Assert-WrapperResult {
    param([string[]]$Arguments, [bool]$Success, [string]$Scenario)

    $output = & $wrapper @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if (($exitCode -eq 0) -ne $Success) {
        $output | Write-Output
        throw "$Scenario returned exit code $exitCode"
    }
    Write-Output "Passed: $Scenario"
}

try {
    Assert-WrapperResult -Arguments @('--version') -Success $true -Scenario 'valid invocation'
    Assert-WrapperResult -Arguments @('--invalid-cognitive-java-wrapper-option') -Success $false `
        -Scenario 'Java process failure is propagated'
    $missingJavaHome = Join-Path ([System.IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString())
    $env:JAVA_HOME = $missingJavaHome
    Assert-WrapperResult -Arguments @('--version') -Success $false -Scenario 'missing configured Java executable'
} finally {
    $env:JAVA_HOME = $originalJavaHome
}
exit 0
