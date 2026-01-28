package org.uwrf;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;

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
                .build();

        // TODO: Create an S3 bucket for video uploads
        // Bucket videoBucket = Bucket.Builder.create(this, "VideoBucket")
        //         .build();

        // TODO: Add S3 event notification to trigger Lambda when a video is uploaded
        // videoBucket.addEventNotification(
        //         EventType.OBJECT_CREATED,
        //         new LambdaDestination(videoHandler),
        //         NotificationKeyFilter.builder().suffix(".mp4").build()
        // );

        // TODO: Grant Lambda permissions to:
        // - Read from the S3 bucket
        // - Call AWS Transcribe
        // - Call AWS Bedrock
        // - Write quiz results back to S3
    }
}
