package org.uwrf;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.Cors;
import software.amazon.awscdk.services.apigateway.CorsOptions;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.EventType;
import software.amazon.awscdk.services.s3.NotificationKeyFilter;
import software.amazon.awscdk.services.s3.notifications.LambdaDestination;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class UwrfStack extends Stack {
    private final String studentName;

    public UwrfStack(final Construct scope, final String id, final String studentName) {
        this(scope, id, null, studentName);
    }

    public UwrfStack(final Construct scope, final String id, final StackProps props, final String studentName) {
        super(scope, id, props);
        this.studentName = studentName;

        Function videoHandler = Function.Builder.create(this, "VideoHandler")
                .functionName(studentName + "-video-handler")
                .runtime(Runtime.JAVA_21)
                .handler("org.uwrf.handlers.VideoHandler::handleRequest")
                .code(Code.fromAsset("target/lambda.jar"))
                .memorySize(512)
                .timeout(Duration.minutes(15))
                .description("Processes video uploads and generates quizzes")
                // Set MOCK_BEDROCK=false when you are ready to use real Bedrock (costs money).
                // Keep it true during development to use canned quiz responses at zero cost.
                .environment(Map.of("MOCK_BEDROCK", "true"))
                .build();

        String bucketName = studentName.toLowerCase().replaceAll("[^a-z0-9-]", "-") + "-video-bucket";

        Bucket videoBucket = Bucket.Builder.create(this, "VideoBucket")
                .bucketName(bucketName)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .build();

        Function transcriptApiHandler = Function.Builder.create(this, "TranscriptApiHandler")
                .functionName(studentName + "-transcript-api-handler")
                .runtime(Runtime.JAVA_21)
                .handler("org.uwrf.handlers.TranscriptApiHandler::handleRequest")
                .code(Code.fromAsset("target/lambda.jar"))
                .memorySize(512)
                .timeout(Duration.minutes(2))
                .description("Generates quizzes from transcript text uploaded by the browser app")
                .environment(Map.of(
                        "MOCK_BEDROCK", "false",
                        "QUIZ_BUCKET_NAME", videoBucket.getBucketName()
                ))
                .build();

        videoBucket.addEventNotification(
                EventType.OBJECT_CREATED,
                new LambdaDestination(videoHandler),
                NotificationKeyFilter.builder().suffix(".mp4").build()
        );
        videoBucket.addEventNotification(
                EventType.OBJECT_CREATED,
                new LambdaDestination(videoHandler),
                NotificationKeyFilter.builder().prefix("transcripts/").suffix(".txt").build()
        );
        videoBucket.addEventNotification(
                EventType.OBJECT_CREATED,
                new LambdaDestination(videoHandler),
                NotificationKeyFilter.builder().prefix("transcripts/").suffix(".json").build()
        );

        videoBucket.grantReadWrite(videoHandler);
        videoBucket.grantReadWrite(transcriptApiHandler);

        videoHandler.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "transcribe:StartTranscriptionJob",
                        "transcribe:GetTranscriptionJob",
                        "transcribe:DeleteTranscriptionJob"
                ))
                .resources(List.of("*"))
                .build());

        PolicyStatement bedrockPolicy = PolicyStatement.Builder.create()
                .actions(List.of(
                        "bedrock:InvokeModel",
                        "bedrock:InvokeModelWithResponseStream"
                ))
                .resources(List.of("*"))
                .build();
        videoHandler.addToRolePolicy(bedrockPolicy);
        transcriptApiHandler.addToRolePolicy(bedrockPolicy);

        videoHandler.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "aws-marketplace:ViewSubscriptions",
                        "aws-marketplace:Subscribe"
                ))
                .resources(List.of("*"))
                .build());

        RestApi quizApi = RestApi.Builder.create(this, "QuizApi")
                .restApiName(studentName + "-quiz-api")
                .description("Browser API for generating quizzes from transcript text")
                .defaultCorsPreflightOptions(CorsOptions.builder()
                        .allowOrigins(Cors.ALL_ORIGINS)
                        .allowMethods(List.of("OPTIONS", "POST", "GET"))
                        .allowHeaders(List.of("Content-Type"))
                        .build())
                .build();

        var quizResource = quizApi.getRoot().addResource("quiz");
        LambdaIntegration transcriptApiIntegration = new LambdaIntegration(transcriptApiHandler);
        quizResource.addMethod("POST", transcriptApiIntegration);
        quizResource.addMethod("GET", transcriptApiIntegration);

        CfnOutput.Builder.create(this, "VideoBucketName")
                .value(videoBucket.getBucketName())
                .description("Upload lecture videos to this S3 bucket")
                .build();

        CfnOutput.Builder.create(this, "QuizApiUrl")
                .value(quizApi.getUrl() + "quiz")
                .description("POST transcript text here, or paste this URL into web/index.html")
                .build();
    }
}
