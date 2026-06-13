# PowerShell Script to Build and Push Frontend Docker Image (Multi-Architecture)
# Supports running on Raspberry Pi (ARM64) and standard systems (AMD64).

# Automatically set working directory to the parent directory of this script (frontend root)
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location (Split-Path -Parent $ScriptDir)

$DockerHubAccount = Read-Host "Enter Docker Hub account name (e.g., vlad2000andrei)"
if ([string]::IsNullOrWhiteSpace($DockerHubAccount)) {
    Write-Error "Docker Hub account name cannot be empty."
    exit 1
}

$VersionTag = Read-Host "Enter version tag (e.g., 1.0.0)"
if ([string]::IsNullOrWhiteSpace($VersionTag)) {
    Write-Error "Version tag cannot be empty."
    exit 1
}

# 1. Verify Docker is installed and running
Write-Host "Verifying Docker installation..." -ForegroundColor Cyan
& docker --version
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker is not installed or not running in this shell. Please start Docker."
    exit 1
}

# 2. Verify Buildx is available
Write-Host "Verifying Docker Buildx availability..." -ForegroundColor Cyan
& docker buildx version
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker Buildx is not available. Please install Docker Buildx or Docker Desktop."
    exit 1
}

# 3. Create or use multi-architecture builder
Write-Host "Setting up multi-architecture builder..." -ForegroundColor Cyan
$builderName = "multi-builder"
$builders = & docker buildx ls
$hasBuilder = $builders -match $builderName

if (-not $hasBuilder) {
    Write-Host "Creating new builder '$builderName'..." -ForegroundColor Yellow
    & docker buildx create --name $builderName --use
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to create Buildx builder."
        exit 1
    }
} else {
    Write-Host "Using existing builder '$builderName'..." -ForegroundColor Green
    & docker buildx use $builderName
}

# Bootstrap the builder
Write-Host "Bootstrapping builder..." -ForegroundColor Cyan
& docker buildx inspect --bootstrap
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to bootstrap Buildx builder."
    exit 1
}

# 4. Build and Push the multi-architecture image
Write-Host "Starting multi-architecture build and push for 'budget-tracker-frontend'..." -ForegroundColor Cyan
Write-Host "Platforms: linux/amd64, linux/arm64" -ForegroundColor Yellow
Write-Host "Tags: $DockerHubAccount/budget-tracker-frontend:$VersionTag, $DockerHubAccount/budget-tracker-frontend:latest" -ForegroundColor Yellow

& docker buildx build --platform linux/amd64,linux/arm64 `
  -t "$DockerHubAccount/budget-tracker-frontend:$VersionTag" `
  -t "$DockerHubAccount/budget-tracker-frontend:latest" `
  --push .

if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker build and push failed. Make sure you are logged in using 'docker login'."
    exit 1
}

Write-Host "`nSuccessfully built and pushed multi-architecture images to Docker Hub!" -ForegroundColor Green
Write-Host "Images: $DockerHubAccount/budget-tracker-frontend:$VersionTag and $DockerHubAccount/budget-tracker-frontend:latest" -ForegroundColor Green
