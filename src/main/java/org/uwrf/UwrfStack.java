package org.uwrf;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.EventType;
import software.amazon.awscdk.services.s3.NotificationKeyFilter;
import software.amazon.awscdk.services.s3.notifications.LambdaDestination;


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
                .timeout(Duration.minutes(5))
                .description("Processes video uploads and generates quizzes")
                // Set MOCK_BEDROCK=false when you are ready to use real Bedrock (costs money).
                // Keep it true during development to use canned quiz responses at zero cost.
                .environment(Map.of("MOCK_BEDROCK", "true"))
                .build();

        // Create an S3 bucket for video uploads
Bucket videoBucket = Bucket.Builder.create(this, "VideoBucket")
        .bucketName(studentName.toLowerCase() + "-video-bucket")
        .removalPolicy(RemovalPolicy.DESTROY)
        .autoDeleteObjects(true)
        .build();

// Add S3 event notification to trigger Lambda when a video is uploaded
videoBucket.addEventNotification(
        EventType.OBJECT_CREATED,
        new LambdaDestination(videoHandler),
        NotificationKeyFilter.builder().suffix(".mp4").build()
);

// Grant Lambda read/write access to the S3 bucket
videoBucket.grantReadWrite(videoHandler);

// Grant Lambda permission to call AWS Transcribe
videoHandler.addToRolePolicy(PolicyStatement.Builder.create()
        .effect(Effect.ALLOW)
        .actions(List.of(
                "transcribe:StartTranscriptionJob",
                "transcribe:GetTranscriptionJob",
                "transcribe:ListTranscriptionJobs"
        ))
        .resources(List.of("*"))
        .build());


        videoHandler.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "bedrock:InvokeModel",
                        "bedrock:InvokeModelWithResponseStream"
                ))
                .resources(List.of("*"))
                .build());

        videoHandler.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "aws-marketplace:ViewSubscriptions",
                        "aws-marketplace:Subscribe"
                ))
                .resources(List.of("*"))
                .build());
    }
}
