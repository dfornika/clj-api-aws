# infrastructure

AWS CDK stack (TypeScript) for `clj-api`. Provisions a DynamoDB table, a
Lambda function running the Clojure API as a Docker image, and an HTTP API
Gateway in front of it.

## What gets deployed

| Resource | Details |
|---|---|
| **DynamoDB table** | On-demand billing, partition key `id` (string). Removal policy is `RETAIN` by default; pass `-c dev=true` to allow `cdk destroy` to delete it. |
| **Lambda function** | Docker image built from `../clj-api/Dockerfile`. 512 MB memory, 30 s timeout, x86_64. |
| **HTTP API Gateway** | Routes `/{proxy+}` → Lambda. CORS enabled for all origins/methods. |

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

## Other useful commands

```bash
npx cdk diff    # compare deployed stack with current state
npx cdk synth   # emit the synthesised CloudFormation template
npx cdk destroy # tear down (add -c dev=true to also delete the DynamoDB table)
npm test        # run CDK stack unit tests
```
