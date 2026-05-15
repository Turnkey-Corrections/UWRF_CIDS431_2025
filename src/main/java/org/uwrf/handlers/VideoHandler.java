package org.uwrf.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.uwrf.services.BedrockQuizGenerator;
import org.uwrf.services.MockQuizGenerator;
import org.uwrf.services.QuizGenerator;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.transcribe.model.Media.*;
import software.amazon.awssdk.utils.IoUtils;
import java.io.ByteArrayOutputStream;
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

            //TODO: Placeholder: On Event Trigger A Read an Existing Transcript From S3
            //My AWS account is not allowing me to use the Transcribe service
            //Customer service has been less than helpful so for now I will do this
            //Pulls an example transcript from the origin s3 bucket then passes the text along

            S3Client s3Client = S3Client.builder().build();
            //System.out.println("S3 Client Region: " + s3Client.serviceClientConfiguration().region());
            String transcriptText = "";
            try {
                //Creates object request
                GetObjectRequest req = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key("example-transcript.json")
                        .build();

                ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(req);
                String rawJson = "";
                try {
                    rawJson = IoUtils.toUtf8String(s3Stream);
                    System.out.println("Json: " + rawJson);
                } catch (Exception e) {
                    System.out.println("Stream error: " + e.getMessage());
                }
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root;
                // Parse the JSON to get the transcript element
                try {
                    root = mapper.readTree(rawJson);
                    System.out.println("Root: " + root);
                    transcriptText = root.path("results")
                            .path("transcripts")
                            .get(0)
                            .path("transcript")
                            .asText();
                } catch (Exception e) {
                    System.out.println("JSON error: " + e.getMessage());
                }

                System.out.println("Transcript loaded: " + transcriptText);
                // TODO: Step 1 - Call AWS Transcribe
                // Use the bucketName and objectKey to start a transcription job
                // Hint: TranscribeClient transcribeClient = TranscribeClient.create();
                // Start a job with transcribeClient.startTranscriptionJob(...)
                // Output the transcript JSON to S3 using outputBucketName and outputKey
                // TODO: Step 2 - Wait for transcription to complete and get the text
                // Transcription is async -- poll getTranscriptionJob() until status is COMPLETED
                // Then read the transcript JSON from S3 and extract the text:
                //   transcriptNode.get("results").get("transcripts").get(0).get("transcript").asText()
                // TODO: Step 3 - Call Bedrock with the transcript
                // Replace "transcript" below with the actual transcript string from Step 2:
                //
                //   String quizJson = this.quizGenerator.generateQuiz(transcript);
                //
                // While MOCK_BEDROCK=true this returns canned questions at zero cost.
                // Flip MOCK_BEDROCK=false when you're ready to test real AI generation.
                String quizJson = "";
                try {
                    // Ensure the Bedrock call happens AFTER the variable is guaranteed to be set
                    if (transcriptText.isEmpty()) {
                        throw new RuntimeException("Cannot call Bedrock: Transcript is empty!");
                    }
                    quizJson = this.quizGenerator.generateQuiz(transcriptText);
                } catch (Exception e) {
                    System.out.println("Error generating quiz: " + e.getMessage());
                }
                // TODO: Step 4 - Build the quiz object
                // Wrap the quiz JSON array in an object that includes metadata:
                //   { "sourceVideo": objectKey, "generatedAt": "...", "questions": [...] }
                ObjectMapper quizMapper = new ObjectMapper();
                ObjectNode rootNode = quizMapper.createObjectNode();
                System.out.println("Quiz JSON: " + quizJson);
                try {
                    // Add suggested metadata
                    rootNode.put("sourceVideo", objectKey);
                    rootNode.put("generatedAt", Instant.now().toString());
                    //Add questions
                    rootNode.set("Response", quizMapper.readTree(quizJson));
                } catch (Exception e) {
                    System.out.println("Error parsing JSON: " + e.getMessage());
                }
                // TODO: Step 5 - Write the quiz JSON back to S3
                // Use S3Client to put a JSON file at "quizzes/<videoName>-quiz.json"
                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                    mapper.writeValue(out, rootNode);

                    //Create correct filepath based on video name
                    String name = objectKey.contains(".")
                            ? objectKey.substring(0, objectKey.lastIndexOf('.'))
                            : objectKey;

                    String destinationKey = "quizzes/" + name + "-quiz.json";

                    // Create the S3 Put request
                    PutObjectRequest putRequest = PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(destinationKey)
                            .contentType("application/json")
                            .build();

                    // Upload the bytes
                    s3Client.putObject(putRequest, RequestBody.fromBytes(out.toByteArray()));

                    System.out.println("Success! JSON object with metadata uploaded to S3.");

                } catch (Exception e) {
                    context.getLogger().log("Failed to create or upload JSON: " + e.getMessage());
                }
            } catch (S3Exception e) {
                //If the file is not in S3
                context.getLogger().log("Error reading transcript: " + e.getMessage());
                //More log debugging
                System.err.println("S3 ERROR: " + e.awsErrorDetails().errorCode());
                System.err.println("REASON: " + e.awsErrorDetails().errorMessage());
            }
        }
        return "Processed " + s3Event.getRecords().size() + " record(s)";
    }
}
