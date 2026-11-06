param([Parameter(Mandatory)][string]$SourcePath, [Parameter(Mandatory)][string]$OutputPath, [string]$SmokeSourcePath)
$ErrorActionPreference = 'Stop'
$vswhere = 'C:/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe'
$installation = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (-not $installation) { throw 'Windows media bridge requires Visual Studio C++ Build Tools and Windows SDK.' }
$vcvars = Join-Path $installation 'VC/Auxiliary/Build/vcvars64.bat'
$source = (Resolve-Path -LiteralPath $SourcePath).Path
$output = [IO.Path]::GetFullPath($OutputPath)
$directory = Split-Path -Parent $output
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$workDirectory = [IO.Path]::GetFullPath((Join-Path $directory '../../windows-media-obj'))
New-Item -ItemType Directory -Force -Path $workDirectory | Out-Null
Push-Location -LiteralPath $workDirectory
try {
    & $env:ComSpec /d /s /c "call `"$vcvars`" >nul && cl /nologo /std:c++17 /EHsc /MD /LD /DWIN32_LEAN_AND_MEAN /DNOMINMAX `"$source`" /link /OUT:`"$output`" /IMPLIB:`"$workDirectory/rxplayer-media.lib`" windowsapp.lib"
    if ($LASTEXITCODE -ne 0) { throw "Windows media bridge build failed: $LASTEXITCODE" }
    if ($SmokeSourcePath) {
        $smoke = (Resolve-Path -LiteralPath $SmokeSourcePath).Path
        $probe = Join-Path $workDirectory 'windows-media-smoke.exe'
        & $env:ComSpec /d /s /c "call `"$vcvars`" >nul && cl /nologo /std:c++17 /EHsc /MD /DWIN32_LEAN_AND_MEAN /DNOMINMAX `"$smoke`" /Fe:`"$probe`" /link windowsapp.lib user32.lib"
        if ($LASTEXITCODE -ne 0) { throw 'SMTC probe build failed' }
        & $probe $output
        if ($LASTEXITCODE -ne 0) { throw 'SMTC probe failed' }
    }
} finally { Pop-Location }
