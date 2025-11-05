#!/bin/bash

# Script to download latest CI test artifacts from Gitea
# Usage: ./getLatestLogsFromCI.sh [run_number]
#
# Environment variables:
#   GIT_PAT - Required for authentication
#   DEV_ASSETS_LOCATION - Path to dev-assets directory (optional)
#
# Example:
#   GIT_PAT=your_token ./getLatestLogsFromCI.sh
#   GIT_PAT=your_token ./getLatestLogsFromCI.sh 50

set -e

# Configuration - Update this path to your dev-assets checkout location
DEV_ASSETS_LOCATION="${DEV_ASSETS_LOCATION:-/home/krickert/IdeaProjects/gitea/dev-assets}"

# Source shared utilities and CI functions from dev-assets
source "$DEV_ASSETS_LOCATION/scripts/shared-utils.sh"
source "$DEV_ASSETS_LOCATION/scripts/ci-utils.sh"

# Parse repository info
parse_git_repo_info

# Download CI artifacts
download_ci_artifacts "$1"
