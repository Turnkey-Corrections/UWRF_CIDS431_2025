package org.uwrf.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;

/**
 * Lambda function that handles S3 events when a video file is uploaded.
 *
 * YOUR TASKS:
 * 1. Call AWS Transcribe to convert the video's audio to text
 * 2. Send the transcript to AWS Bedrock to generate quiz questions
 * 3. Write the quiz JSON back to S3
 */
public class VideoHandler implements RequestHandler<S3Event, String> {

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        System.out.println("=== Lambda Function Triggered ===");
        System.out.println("Received S3 event with " + s3Event.getRecords().size() + " record(s)");

        for (S3EventNotification.S3EventNotificationRecord record : s3Event.getRecords()) {
            String bucketName = record.getS3().getBucket().getName();
            String objectKey = record.getS3().getObject().getKey();
            long objectSize = record.getS3().getObject().getSizeAsLong();
            String eventName = record.getEventName();

            System.out.println("--- S3 Event Details ---");
            System.out.println("Event Type: " + eventName);
            System.out.println("Bucket: " + bucketName);
            System.out.println("File: " + objectKey);
            System.out.println("Size: " + objectSize + " bytes");
            System.out.println("Event Time: " + record.getEventTime());
            System.out.println("------------------------");

            // TODO: Step 1 - Call AWS Transcribe
            // Use the bucketName and objectKey to start a transcription job
            // Hint: You'll need to add the AWS SDK for Transcribe to pom.xml
            // TranscribeClient transcribeClient = TranscribeClient.create();

            // TODO: Step 2 - Wait for transcription to complete and get the text
            // Transcription jobs are async, so you'll need to poll for completion
            // or use a Step Functions workflow for production

            // TODO: Step 3 - Call AWS Bedrock with the transcript
            // Send a prompt like "Generate 10 multiple choice questions from this lecture transcript: {transcript}"
            // Hint: You'll need to add the AWS SDK for Bedrock Runtime to pom.xml

            // TODO: Step 4 - Parse Bedrock's response and create Quiz JSON

            // TODO: Step 5 - Write the quiz JSON back to S3
            // Use S3Client to put the JSON file in the same bucket
        }

        return "Processed " + s3Event.getRecords().size() + " record(s)";
    }
}
