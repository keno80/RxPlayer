param([Parameter(Mandatory=$true)][string]$OutputPath)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$m1Plain = [Text.Encoding]::UTF8.GetBytes('synthetic-session-only')
$m1Scope = [Security.Cryptography.DataProtectionScope]::CurrentUser
$m1Protected = [Security.Cryptography.ProtectedData]::Protect($m1Plain, $null, $m1Scope)
$m1File = [IO.Path]::GetTempFileName()
try {
    [IO.File]::WriteAllBytes($m1File, $m1Protected)
    $m1Read = [IO.File]::ReadAllBytes($m1File)
    $m1Recovered = [Security.Cryptography.ProtectedData]::Unprotect($m1Read, $null, $m1Scope)
    $m1RoundTrip = [Convert]::ToBase64String($m1Recovered) -eq [Convert]::ToBase64String($m1Plain)
    $m1Read[$m1Read.Length - 1] = $m1Read[$m1Read.Length - 1] -bxor 1
    $m1TamperRejected = $false
    try { [void][Security.Cryptography.ProtectedData]::Unprotect($m1Read, $null, $m1Scope) }
    catch [Security.Cryptography.CryptographicException] { $m1TamperRejected = $true }
    $m1Report = @{
        checked_at = [DateTime]::UtcNow.ToString('o')
        scope = 'CurrentUser'
        synthetic_data_only = $true
        persisted_round_trip = $m1RoundTrip
        ciphertext_differs = [Convert]::ToBase64String($m1Protected) -ne [Convert]::ToBase64String($m1Plain)
        tamper_rejected = $m1TamperRejected
    }
} finally {
    Remove-Item -LiteralPath $m1File
}
$m1Report.temporary_file_removed = -not (Test-Path -LiteralPath $m1File)
$m1Absolute = [IO.Path]::GetFullPath($OutputPath)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($m1Absolute)) | Out-Null
[IO.File]::WriteAllText($m1Absolute, ($m1Report | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))
Write-Output "Report saved: $m1Absolute"
