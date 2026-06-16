import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigwv2 from 'aws-cdk-lib/aws-apigatewayv2';
import { HttpLambdaIntegration } from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { Duration } from 'aws-cdk-lib';
import * as path from 'path';

export class CljApiStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // Pass `-c dev=true` to allow stack destruction to delete the table.
    // Defaults to RETAIN so data survives accidental `cdk destroy`.
    const isDev = this.node.tryGetContext('dev') === 'true';

    // DynamoDB Table (PAY_PER_REQUEST = no idle charges, ideal for hobby projects)
    const itemsTable = new dynamodb.TableV2(this, 'itemsTable', {
      partitionKey: { name: 'id', type: dynamodb.AttributeType.STRING },
      billing: dynamodb.Billing.onDemand(),
      removalPolicy: isDev ? cdk.RemovalPolicy.DESTROY : cdk.RemovalPolicy.RETAIN,
    });

    // Lambda Function
    const apiLambda = new lambda.DockerImageFunction(this, 'apiLambda', {
      code: lambda.DockerImageCode.fromImageAsset(path.join(__dirname, '../../clj-api')),
      architecture: lambda.Architecture.X86_64,
      memorySize: 512,
      timeout: Duration.seconds(30),
      environment: {
        DYNAMODB_TABLE_NAME: itemsTable.tableName,
      },
      logRetention: logs.RetentionDays.ONE_WEEK,
    });

    // Grant Lambda read/write access to the table
    itemsTable.grantReadWriteData(apiLambda);

    // HTTP API Gateway
    const httpApi = new apigwv2.HttpApi(this, 'httpApi', {
      apiName: 'ClojureApiTest',
      description: 'HTTP API for Clojure Lambda',
      corsPreflight: {
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

    const lambdaIntegration = new HttpLambdaIntegration('lambdaIntegration', apiLambda);

    httpApi.addRoutes({
      path: '/{proxy+}',
      methods: [apigwv2.HttpMethod.ANY],
      integration: lambdaIntegration,
    });

    new cdk.CfnOutput(this, 'ApiGatewayUrl', {
      value: httpApi.url!,
      description: 'The URL of the API Gateway',
    });
  }
}
