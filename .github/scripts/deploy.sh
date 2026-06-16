#!/usr/bin/env bash
# Deploy clj-api to AWS.
#
# Builds the uberjar then hands off to CDK, which builds the Docker image,
# pushes it to ECR, and updates the Lambda function.
#
# Prerequisites (one-time, per AWS account/region):
#   cd infrastructure && npx cdk bootstrap
#
# Local usage (AWS credentials must be configured):
#   .github/scripts/deploy.sh
#
# In CI: called by .github/workflows/deploy.yml with credentials from secrets.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

echo "==> Building uberjar..."
(cd "$REPO_ROOT/clj-api" && clojure -T:build uber)

echo "==> Deploying with CDK..."
(cd "$REPO_ROOT/infrastructure" && npm ci --quiet && npx cdk deploy --require-approval never)
