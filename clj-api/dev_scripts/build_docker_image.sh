#!/bin/bash

docker build -t clj-api:lambda .

docker tag clj-api:lambda ${ECR_REPO_URI}:latest
