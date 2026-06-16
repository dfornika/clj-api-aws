# infrastructure

AWS CDK stack (TypeScript) for `clj-api`. Provisions a DynamoDB table, a
Lambda function running the Clojure API as a Docker image, and an HTTP API
Gateway in front of it.

## What gets deployed

| Resource | Details |
|---|---|
| **DynamoDB table** | On-demand billing, partition key `id` (string). Removal policy is `RETAIN` by default; pass `-c dev=true` to allow `cdk destroy` to delete it. |
| **Lambda function** | Docker image built from `../clj-api/Dockerfile`. 512 MB memory, 30 s timeout, x86_64. Reserved concurrency: 10 (excess requests → 429, no Lambda charge). |
| **HTTP API Gateway** | Routes `GET /health` (unauthenticated) and `/{proxy+}` (JWT required) → Lambda. Throttled to 10 req/s / 50 burst. CORS enabled for all origins/methods. |
| **Cognito User Pool Client** | Per-app client attached to the shared User Pool from the personal foundation stack. Pool ID and provider URL are read from SSM (`/personal/cognito/user-pool-id`, `/personal/cognito/user-pool-provider-url`). |

The Lambda image uses the [AWS Lambda Web Adapter](https://github.com/awslabs/aws-lambda-web-adapter)
to proxy requests to the Jetty server running inside the container on port 8080.

## Prerequisites

- [AWS CDK CLI](https://docs.aws.amazon.com/cdk/v2/guide/getting_started.html): `npm install -g aws-cdk`
- AWS credentials configured (`aws configure` or environment variables)
- Docker running (CDK builds and pushes the image during deploy)
- CDK bootstrapped once per account/region (see below)

## First-time bootstrap

```bash
npx cdk bootstrap
```

This provisions the S3 bucket and ECR repository that CDK uses internally.
Only needed once per AWS account/region.

## Deploying

The recommended way is via the repo-level deploy script, which builds the
uberjar first:

```bash
# from the repo root
.github/scripts/deploy.sh
```

Or manually, step by step:

```bash
# 1. Build the uberjar
cd ../clj-api && clojure -T:build uber && cd -

# 2. Deploy (builds Docker image, pushes to ECR, updates Lambda)
npm ci
npx cdk deploy
```

## Prerequisites

Before deploying, the personal foundation stack must be deployed and the following
SSM parameters must exist in the same account/region:

| Parameter | Description |
|---|---|
| `/foundation/cognito/personal-user-pool-id` | User Pool ID from the foundation stack |
| `/foundation/cognito/personal-user-pool-provider-url` | Provider URL (e.g. `https://cognito-idp.<region>.amazonaws.com/<pool-id>`) |

## Authentication

The API uses Cognito JWT authorization. `GET /health` is open; all other routes
require a valid `Authorization: Bearer <token>` header.

The User Pool lives in the personal foundation stack (invite-only, no self-sign-up).
User management is done there. This stack only creates a per-app `UserPoolClient`;
its ID is available in the `UserPoolClientId` stack output.

### Getting a token

```bash
# UserPoolId comes from the foundation stack; UserPoolClientId from this stack's outputs
aws cognito-idp initiate-auth \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id <UserPoolClientId> \
  --auth-parameters USERNAME=you@example.com,PASSWORD='YourSecurePassword!'
# → copy AuthenticationResult.AccessToken (valid for 1 hour)
```

### Calling the API

```bash
curl -H "Authorization: Bearer <AccessToken>" \
  https://<api-id>.execute-api.<region>.amazonaws.com/items
```

### Refreshing a token

```bash
aws cognito-idp initiate-auth \
  --auth-flow REFRESH_TOKEN_AUTH \
  --client-id <UserPoolClientId> \
  --auth-parameters REFRESH_TOKEN=<RefreshToken>
```

## Other useful commands

```bash
npx cdk diff    # compare deployed stack with current state
npx cdk synth   # emit the synthesised CloudFormation template
npx cdk destroy # tear down (add -c dev=true to also delete the DynamoDB table)
npm test        # run CDK stack unit tests
```
