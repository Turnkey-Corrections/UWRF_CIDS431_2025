package org.uwrf.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for VideoHandler.
 *
 * Run these tests locally to verify your Lambda logic before deploying to AWS.
 * This saves you from having to deploy CDK every time you make a change.
 */
class VideoHandlerTest {

    private VideoHandler handler;

    @BeforeEach
    void setUp() {
        handler = new VideoHandler();
    }

    @Test
    void handleRequest_withValidS3Event_printsEventDetails() {
        S3Event s3Event = createTestS3Event("my-video-bucket", "lectures/SampleVideo.mp4", 844365824L);
        Context mockContext = new MockLambdaContext();

        String result = handler.handleRequest(s3Event, mockContext);

        assertNotNull(result);
        assertTrue(result.contains("1 record(s)"));
    }

    @Test
    void handleRequest_extractsCorrectBucketAndKey() {
        S3Event s3Event = createTestS3Event("test-bucket", "videos/lecture1.mp4", 12345678L);
        Context mockContext = new MockLambdaContext();

        String result = handler.handleRequest(s3Event, mockContext);

        assertNotNull(result);
    }

    @Test
    void handleRequest_handlesMultipleRecords() {
        S3EventNotification.S3EventNotificationRecord record1 = createS3Record("bucket1", "video1.mp4", 100L);
        S3EventNotification.S3EventNotificationRecord record2 = createS3Record("bucket2", "video2.mp4", 200L);
        S3Event s3Event = new S3Event(List.of(record1, record2));

        Context mockContext = new MockLambdaContext();

        String result = handler.handleRequest(s3Event, mockContext);

        assertTrue(result.contains("2 record(s)"));
    }

    /**
     * Helper method to create a test S3Event.
     * Use this pattern in your own tests to simulate different scenarios.
     */
    private S3Event createTestS3Event(String bucketName, String objectKey, long objectSize) {
        S3EventNotification.S3EventNotificationRecord record = createS3Record(bucketName, objectKey, objectSize);
        return new S3Event(List.of(record));
    }

    private S3EventNotification.S3EventNotificationRecord createS3Record(String bucketName, String objectKey, long objectSize) {
        S3EventNotification.S3BucketEntity bucket = new S3EventNotification.S3BucketEntity(
                bucketName,
                null,
                "arn:aws:s3:::" + bucketName
        );

        S3EventNotification.S3ObjectEntity object = new S3EventNotification.S3ObjectEntity(
                objectKey,
                objectSize,
                "abc123etag",
                "1",
                null
        );

        S3EventNotification.S3Entity s3 = new S3EventNotification.S3Entity(
                "configId",
                bucket,
                object,
                "1.0"
        );

        return new S3EventNotification.S3EventNotificationRecord(
                "us-east-1",
                "ObjectCreated:Put",
                "aws:s3",
                Instant.now().toString(),
                "2.1",
                null,
                null,
                s3,
                null
        );
    }

    /**
     * Minimal mock implementation of Lambda Context for local testing.
     */
    static class MockLambdaContext implements Context {
        @Override public String getAwsRequestId() { return "test-request-id"; }
        @Override public String getLogGroupName() { return "/aws/lambda/test"; }
        @Override public String getLogStreamName() { return "test-stream"; }
        @Override public String getFunctionName() { return "VideoHandler"; }
        @Override public String getFunctionVersion() { return "$LATEST"; }
        @Override public String getInvokedFunctionArn() { return "arn:aws:lambda:us-east-1:123456789:function:VideoHandler"; }
        @Override public com.amazonaws.services.lambda.runtime.CognitoIdentity getIdentity() { return null; }
        @Override public com.amazonaws.services.lambda.runtime.ClientContext getClientContext() { return null; }
        @Override public int getRemainingTimeInMillis() { return 300000; }
        @Override public int getMemoryLimitInMB() { return 512; }
        @Override public com.amazonaws.services.lambda.runtime.LambdaLogger getLogger() {
            return new com.amazonaws.services.lambda.runtime.LambdaLogger() {
                @Override public void log(String message) { System.out.println("[Lambda] " + message); }
                @Override public void log(byte[] message) { System.out.println("[Lambda] " + new String(message)); }
            };
        }
    }
}
