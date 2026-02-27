# Atlasia AI - Dev Environment Secrets Generator
# This script generates secure random keys for local development

function New-SecureSecret([int]$byteLength) {
    $bytes = New-Object Byte[] $byteLength
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [Convert]::ToBase64String($bytes)
}

function New-HexSecret([int]$byteLength) {
    $bytes = New-Object Byte[] $byteLength
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return ($bytes | ForEach-Object { $_.ToString("x2") }) -join ""
}

Write-Host "--- Generating Secure Secrets for Atlasia ---" -ForegroundColor Cyan

$jwtSecret = New-SecureSecret 64
$encryptionKey = New-SecureSecret 32
$orchToken = New-HexSecret 32

$envPath = "infra/deployments/dev/.env.dev"

if (Test-Path $envPath) {
    $content = Get-Content $envPath
    $content = $content -replace "JWT_SECRET_KEY=.*", "JWT_SECRET_KEY=$jwtSecret"
    $content = $content -replace "ORCHESTRATOR_TOKEN=.*", "ORCHESTRATOR_TOKEN=$orchToken"
    
    if ($content -match "VAULT_ENCRYPTION_KEY=") {
        $content = $content -replace "VAULT_ENCRYPTION_KEY=.*", "VAULT_ENCRYPTION_KEY=$encryptionKey"
    } else {
        $content += "`nVAULT_ENCRYPTION_KEY=$encryptionKey"
    }

    $content | Set-Content $envPath
    Write-Host "Updated $envPath with new secrets." -ForegroundColor Green
} else {
    Write-Host "Error: $envPath not found!" -ForegroundColor Red
}

Write-Host "--- Generated Secrets ---"
Write-Host "JWT Secret (HS512): $jwtSecret"
Write-Host "Encryption Key (AES-256): $encryptionKey"
Write-Host "Orchestrator Token: $orchToken"
Write-Host "------------------------"
Write-Host "Please restart your containers: docker compose -f infra/deployments/dev/docker-compose.yml up -d"
