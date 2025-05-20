import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as ecr_assets from 'aws-cdk-lib/aws-ecr-assets';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigwv2 from 'aws-cdk-lib/aws-apigatewayv2';
import { HttpLambdaIntegration } from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import * as logs from 'aws-cdk-lib/aws-logs';
import { Duration } from 'aws-cdk-lib'; // For timeout
import * as path from 'path';

export class CljApiStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    /* const helloLambda = new lambda.Function(this, 'HelloLambda', {
      runtime: lambda.Runtime.NODEJS_20_X, // Or any simple runtime
      handler: 'index.handler',
      code: lambda.Code.fromInline(`
        exports.handler = async function(event) {
          console.log("request:", JSON.stringify(event, undefined, 2));
          return {
            statusCode: 200,
            headers: { "Content-Type": "text/plain" },
            body: "Hello from temporary inline Lambda!"
          };
        };
      `),
      timeout: cdk.Duration.seconds(10),
      logRetention: logs.RetentionDays.ONE_DAY, // Keep it short for test
    }); */

    // Lambda Function
    const apiLambda = new lambda.Function(this, 'apiLambda', {
      code: lambda.Code.fromAsset(path.join(__dirname, '../../minimal-docker-test')),
      handler: lambda.Handler.FROM_IMAGE,
      runtime: lambda.Runtime.FROM_IMAGE,
      architecture: lambda.Architecture.X86_64, // Or ARM_64, match your Dockerfile
      memorySize: 512, // Adjust as needed (MB). JVM needs a bit more.
      timeout: Duration.seconds(30), // API Gateway has a 30s timeout.
      environment: {
        // PORT: "8080", // Already set in Dockerfile, but can override
        // Add any other environment variables your app needs
        // DATABASE_URL: "your_db_connection_string_from_secrets_manager_or_ssm"
      },
      logRetention: logs.RetentionDays.ONE_WEEK, // Adjust as needed
    });

    // 3. HTTP API Gateway
    const httpApi = new apigwv2.HttpApi(this, 'httpApi', {
      apiName: 'ClojureApiTest',
      description: 'HTTP API for Clojure Lambda',
      corsPreflight: { // Optional: configure CORS
        allowHeaders: ['Content-Type', 'Authorization'],
        allowMethods: [
          apigwv2.CorsHttpMethod.GET,
          apigwv2.CorsHttpMethod.POST,
          apigwv2.CorsHttpMethod.PUT,
          apigwv2.CorsHttpMethod.DELETE,
          apigwv2.CorsHttpMethod.OPTIONS,
        ],
        allowOrigins: ['*'], // Be more specific for production
      },
    });

    // 4. Integrate Lambda with API Gateway
    const lambdaIntegration = new HttpLambdaIntegration('lambdaIntegration', apiLambda);

    httpApi.addRoutes({
      path: '/{proxy+}', // Catch-all path, forwards everything to your Ring app
      methods: [apigwv2.HttpMethod.ANY],
      integration: lambdaIntegration,
    });

    // Output the API Gateway endpoint URL
    new cdk.CfnOutput(this, 'ApiGatewayUrl', {
      value: httpApi.url!,
      description: 'The URL of the API Gateway',
    });

  };
}
