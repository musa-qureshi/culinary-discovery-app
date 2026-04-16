param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 3306,
    [string]$DbName = "culinary_discovery_app",
    [string]$DbUser = "root",
    [string]$DbPassword = "",
    [int]$ServerPort = 8080
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    Write-Host "Enter your MySQL password (leave blank if none):" -ForegroundColor Yellow
    $secure = Read-Host -AsSecureString
    $DbPassword = [System.Net.NetworkCredential]::new("", $secure).Password
}

$tcp = Test-NetConnection -ComputerName $DbHost -Port $DbPort -WarningAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
    throw "Cannot reach MySQL at ${DbHost}:${DbPort}. Start your MySQL service first."
}

$env:DB_URL = "jdbc:mysql://${DbHost}:${DbPort}/${DbName}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME = $DbUser
$env:DB_PASSWORD = $DbPassword
$env:SERVER_PORT = "$ServerPort"

Write-Host "Using DB_URL=$($env:DB_URL)" -ForegroundColor Cyan
Write-Host "Using DB_USERNAME=$($env:DB_USERNAME)" -ForegroundColor Cyan
Write-Host "Starting Spring Boot backend..." -ForegroundColor Green

Push-Location $PSScriptRoot
try {
    .\mvnw.cmd spring-boot:run
}
finally {
    Pop-Location
}