<#
.SYNOPSIS
    Exports environment variables from .env into the current PowerShell session for TravelEasy local development.
.DESCRIPTION
    Reads the root .env file and sets each KEY=VALUE pair as an environment variable in $env:.
    Run using dot-sourcing:
        . .\scripts\export-env.ps1
#>

$envFile = Join-Path $PSScriptRoot "..\.env"

if (-not (Test-Path $envFile)) {
    Write-Warning ".env file not found at: $envFile"
    Write-Warning "Please copy .env.example to .env and configure your local credentials before continuing."
    return
}

$loadedCount = 0
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $parts = $line -split "=", 2
        $key = $parts[0].Trim()
        $value = $parts[1].Trim()

        # Remove surrounding single or double quotes if present
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        [System.Environment]::SetEnvironmentVariable($key, $value, [System.EnvironmentVariableTarget]::Process)
        $loadedCount++
    }
}

Write-Host "TravelEasy: Successfully loaded $loadedCount environment variables into current session." -ForegroundColor Green
Write-Host "You can now run Maven microservices and tests with environment configuration active." -ForegroundColor Cyan
