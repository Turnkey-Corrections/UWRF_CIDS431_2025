package org.uwrf;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
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
                .timeout(Duration.minutes(5))
                .description("Processes video uploads and generates quizzes")
                // Set MOCK_BEDROCK=false when you are ready to use real Bedrock (costs money).
                // Keep it true during development to use canned quiz responses at zero cost.
                .environment(Map.of("MOCK_BEDROCK", "true",
                                    "MOCK_TRANSCRIBE", "true"))
                .build();

        // create the S3 bucket where videos are uploaded and quiz results are stored
        Bucket videoBucket = Bucket.Builder.create(this, "VideoBucket")
                .bucketName(studentName + "-video-bucket") // prefix keeps the name unique across AWS
                .build();

        // trigger Lambda automatically whenever a .mp4 is uploaded to the bucket
        videoBucket.addEventNotification(
                EventType.OBJECT_CREATED,              // fires on any new upload
                new LambdaDestination(videoHandler),   // send the event to our Lambda function
                NotificationKeyFilter.builder().suffix(".mp4").build() // only for .mp4 files
        );

        // grant Lambda permission to read videos from S3
        videoBucket.grantRead(videoHandler);

        // grant Lambda permission to write quiz results back to S3
        videoBucket.grantWrite(videoHandler);

        // grant Lambda permission to start and check transcription jobs
        videoHandler.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "transcribe:StartTranscriptionJob",
                        "transcribe:GetTranscriptionJob"
                ))
                .resources(List.of("*"))
                .build());

        // grant Lambda permission to call Bedrock AI models
        videoHandler.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "bedrock:InvokeModel",
                        "bedrock:InvokeModelWithResponseStream"
                ))
                .resources(List.of("*"))
                .build());

        // required to subscribe to Bedrock models via AWS Marketplace
        videoHandler.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "aws-marketplace:ViewSubscriptions",
                        "aws-marketplace:Subscribe"
                ))
                .resources(List.of("*"))
                .build());
    }
}
