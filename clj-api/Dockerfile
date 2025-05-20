# Use a slim JRE base image
FROM eclipse-temurin:21-jre-alpine

# Set the AWS_LAMBDA_RUNTIME_API if not already set by AWS Lambda.
# This is often set by the Lambda environment, but good to have a default.
ENV AWS_LAMBDA_RUNTIME_API="localhost:9001" \
    # Configure the port your Clojure app (Jetty) will listen on
    # The Lambda Web Adapter will proxy requests to this port.
    PORT="8080" \
    # Optional: Configure the adapter's readyness check path.
    # Should match a path in your app that returns 200 OK quickly.
    AWS_LAMBDA_WEB_ADAPTER_READINESS_CHECK_PATH="/health"

WORKDIR /app

# Copy the Lambda Web Adapter binary.
COPY --from=public.ecr.aws/awsguru/aws-lambda-adapter:0.9.1 /lambda-adapter /opt/extensions/lambda-adapter

# Copy your uberjar
COPY target/app.jar /app/app.jar

# The Lambda environment will run this command.
# It starts your Clojure application (which starts Jetty).
# The Lambda Web Adapter runs as an extension and proxies requests.
CMD ["java", "-jar", "/app/app.jar"]
