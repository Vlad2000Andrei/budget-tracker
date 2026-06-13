#!/bin/bash
# Bash Script to Build and Push Frontend Docker Image (Multi-Architecture)
# Supports running on Raspberry Pi (ARM64) and standard systems (AMD64).
set -e

# Automatically set working directory to the parent directory of this script (frontend root)
cd "$(dirname "$0")/.."

# Prompt for Docker Hub account name
read -p "Enter Docker Hub account name (e.g., vlad2000andrei): " DOCKER_HUB_ACCOUNT
if [ -z "$DOCKER_HUB_ACCOUNT" ]; then
    echo "Error: Docker Hub account name cannot be empty."
    exit 1
fi

# Prompt for version tag
read -p "Enter version tag (e.g., 1.0.0): " VERSION_TAG
if [ -z "$VERSION_TAG" ]; then
    echo "Error: Version tag cannot be empty."
    exit 1
fi

# 1. Verify Docker is installed and running
echo "Verifying Docker installation..."
if ! docker --version >/dev/null 2>&1; then
    echo "Error: Docker is not installed or not running. Please start Docker."
    exit 1
fi

# 2. Verify Buildx is available
echo "Verifying Docker Buildx availability..."
if ! docker buildx version >/dev/null 2>&1; then
    echo "Error: Docker Buildx is not available. Please install Docker Buildx or Docker Desktop."
    exit 1
fi

# 3. Create or use multi-architecture builder
echo "Setting up multi-architecture builder..."
BUILDER_NAME="multi-builder"
if ! docker buildx ls | grep -q "$BUILDER_NAME"; then
    echo "Creating new builder '$BUILDER_NAME'..."
    docker buildx create --name "$BUILDER_NAME" --use
else
    echo "Using existing builder '$BUILDER_NAME'..."
    docker buildx use "$BUILDER_NAME"
fi

# Bootstrap the builder
echo "Bootstrapping builder..."
docker buildx inspect --bootstrap

# 4. Build and Push the multi-architecture image
echo "Starting multi-architecture build and push for 'budget-tracker-frontend'..."
echo "Platforms: linux/amd64, linux/arm64"
echo "Tags: $DOCKER_HUB_ACCOUNT/budget-tracker-frontend:$VERSION_TAG, $DOCKER_HUB_ACCOUNT/budget-tracker-frontend:latest"

docker buildx build --platform linux/amd64,linux/arm64 \
  -t "$DOCKER_HUB_ACCOUNT/budget-tracker-frontend:$VERSION_TAG" \
  -t "$DOCKER_HUB_ACCOUNT/budget-tracker-frontend:latest" \
  --push .

echo -e "\nSuccessfully built and pushed multi-architecture images to Docker Hub!"
echo "Images: $DOCKER_HUB_ACCOUNT/budget-tracker-frontend:$VERSION_TAG and $DOCKER_HUB_ACCOUNT/budget-tracker-frontend:latest"
