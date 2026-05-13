package org.uwrf.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import org.uwrf.services.BedrockQuizGenerator;
import org.uwrf.services.MockQuizGenerator;
import org.uwrf.services.QuizGenerator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.time.Instant;


/**
 * Lambda function that handles S3 events when a video file is uploaded.
 *
 * YOUR TASKS:
 * 1. Call AWS Transcribe to convert the video's audio to text
 * 2. Send the transcript to AWS Bedrock to generate quiz questions
 * 3. Write the quiz JSON back to S3
 *
 * COST TIP: This handler uses a QuizGenerator interface so you can develop locally
 * without paying for Bedrock tokens. Set the Lambda environment variable:
 *   MOCK_BEDROCK=true   → uses MockQuizGenerator (free, returns canned questions)
 *   MOCK_BEDROCK=false  → uses BedrockQuizGenerator (real AI, costs money)
 */
public class VideoHandler implements RequestHandler<S3Event, String> {

    private final QuizGenerator quizGenerator;

    private static final String TRANSCRIPT_KEY = "transcripts/sample-transcript.json";    		
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Default constructor used by AWS Lambda.
     * Checks the MOCK_BEDROCK environment variable to select the quiz generator.
     */
    public VideoHandler() {
        this("true".equalsIgnoreCase(System.getenv("MOCK_BEDROCK"))
                ? new MockQuizGenerator()
                : new BedrockQuizGenerator());
    }

    /**
     * Constructor for unit tests -- inject any QuizGenerator implementation directly.
     */
    VideoHandler(QuizGenerator quizGenerator) {
        this.quizGenerator = quizGenerator;
    }

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

            if (objectKey.equals(TRANSCRIPT_KEY)) {
                System.out.println("Ignoring transcript file upload event.");
                continue;
            }

            try (S3Client s3Client = S3Client.create()) {
                // Step 1: Read the pre-supplied transcript JSON from S3.
                System.out.println("Reading pre-supplied transcript: " + TRANSCRIPT_KEY);
                ResponseBytes<GetObjectResponse> transcriptBytes = s3Client.getObjectAsBytes(
                        GetObjectRequest.builder()
                                .bucket(bucketName)
                                .key(TRANSCRIPT_KEY)
                                .build());

                JsonNode transcriptNode = mapper.readTree(transcriptBytes.asByteArray());
                String transcript = transcriptNode
                        .get("results")
                        .get("transcripts")
                        .get(0)
                        .get("transcript")
                        .asText();
                System.out.println("Transcript length: " + transcript.length() + " characters");

                // Step 2: Send the transcript to the QuizGenerator.
                String quizJson = this.quizGenerator.generateQuiz(transcript);

                // Step 3: Wrap the quiz with metadata.
                String wrappedQuiz = "{"
                        + "\"sourceVideo\":\"" + objectKey + "\","
                        + "\"transcriptSource\":\"" + TRANSCRIPT_KEY + "\","
                        + "\"generatedAt\":\"" + Instant.now() + "\","
                        + "\"questions\":" + quizJson
                        + "}";

                // Step 4: Write the quiz JSON back to S3.
                String videoBaseName = objectKey.contains(".")
                        ? objectKey.substring(0, objectKey.lastIndexOf('.'))
                        : objectKey;
                String quizKey = "quizzes/" + videoBaseName + "-quiz.json";

                s3Client.putObject(PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(quizKey)
                                .contentType("application/json")
                                .build(),
                        RequestBody.fromString(wrappedQuiz));

                System.out.println("Quiz written to s3://" + bucketName + "/" + quizKey);

            } catch (Exception e) {
                System.err.println("Error processing " + objectKey + ": " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            }

        }

        return "Processed " + s3Event.getRecords().size() + " record(s)";
    }
}
