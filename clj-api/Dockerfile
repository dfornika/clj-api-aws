# --- Build stage ---
FROM clojure:temurin-21-tools-deps AS builder

WORKDIR /build

# Resolve deps before copying source to exploit layer caching.
# This layer is only invalidated when deps.edn changes.
COPY deps.edn ./
RUN clojure -P -A:build

COPY . .
RUN clojure -T:build uber

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine

ENV AWS_LAMBDA_RUNTIME_API="localhost:9001" \
    PORT="8080" \
    AWS_LAMBDA_WEB_ADAPTER_READINESS_CHECK_PATH="/health"

WORKDIR /app

COPY --from=public.ecr.aws/awsguru/aws-lambda-adapter:0.9.1 /lambda-adapter /opt/extensions/lambda-adapter

COPY --from=builder /build/target/app.jar /app/app.jar

CMD ["java", "-jar", "/app/app.jar"]
