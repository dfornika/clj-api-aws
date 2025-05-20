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

    // Lambda Function
    const apiLambda = new lambda.DockerImageFunction(this, 'apiLambda', {
      // DockerImageFunction specifically requires DockerImageCode
      code: lambda.DockerImageCode.fromImageAsset(path.join(__dirname, '../../clj-api')),
      architecture: lambda.Architecture.X86_64,
      memorySize: 512, // Or more for JVM
      timeout: Duration.seconds(30),
      environment: {
        // PORT: "8080", // Lambda Web Adapter typically listens on 8080
      },
      logRetention: logs.RetentionDays.ONE_WEEK,
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

    // Integrate Lambda with API Gateway
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
