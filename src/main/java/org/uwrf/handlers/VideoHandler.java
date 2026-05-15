package org.uwrf.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.uwrf.services.BedrockQuizGenerator;
import org.uwrf.services.MockQuizGenerator;
import org.uwrf.services.QuizGenerator;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
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

            // Steps 1 & 2: get transcript -- skip real AWS calls if MOCK_TRANSCRIBE=true
            String transcript;
            if ("true".equalsIgnoreCase(System.getenv("MOCK_TRANSCRIBE"))) {
                System.out.println("[MOCK] Skipping Transcribe -- returning canned transcript");
                transcript = "This is a mock transcript for local testing.";
            } else {
                // Step 1: start the transcription job
                String jobName = "quiz-" + System.currentTimeMillis(); // unique per run so jobs don't collide
                String s3Uri = "s3://" + bucketName + "/" + objectKey; // full S3 path Transcribe needs

                TranscribeClient transcribeClient = TranscribeClient.create();

                // tell Transcribe to convert the video audio to text and save the result to S3
                transcribeClient.startTranscriptionJob(StartTranscriptionJobRequest.builder()
                        .transcriptionJobName(jobName)
                        .mediaFormat(MediaFormat.MP4)
                        .media(Media.builder().mediaFileUri(s3Uri).build())
                        .languageCode(LanguageCode.EN_US)
                        .outputBucketName(bucketName)
                        .outputKey("transcripts/" + jobName + ".json") // save transcript under transcripts/ folder
                        .build());

                // Step 2: poll until the async transcription job finishes
                while (true) {
                    TranscriptionJob job = transcribeClient.getTranscriptionJob(
                                    GetTranscriptionJobRequest.builder().transcriptionJobName(jobName).build())
                            .transcriptionJob();

                    TranscriptionJobStatus status = job.transcriptionJobStatus();
                    System.out.println("Transcription status: " + status);

                    if (status == TranscriptionJobStatus.COMPLETED) break; // exit loop when done
                    if (status == TranscriptionJobStatus.FAILED) {
                        throw new RuntimeException("Transcription failed: " + job.failureReason());
                    }

                    try {
                        Thread.sleep(15000); // wait 15 seconds before checking again
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // restore interrupted flag for callers
                        throw new RuntimeException("Interrupted while waiting for transcription", e);
                    }
                }

                // read the transcript JSON file that Transcribe wrote to S3
                S3Client s3Client = S3Client.create();
                String transcriptKey = "transcripts/" + jobName + ".json";
                String transcriptJson = s3Client.getObjectAsBytes(
                        GetObjectRequest.builder().bucket(bucketName).key(transcriptKey).build()
                ).asUtf8String();

                // dig into the JSON structure to get the plain text string
                try {
                    transcript = new ObjectMapper().readTree(transcriptJson)
                            .get("results")
                            .get("transcripts")
                            .get(0)
                            .get("transcript")
                            .asText();

                    System.out.println("Transcript length: " + transcript.length() + " characters");
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("JSON processing error", e);
                }
            }

            // Step 3: send transcript to Bedrock (or mock) to generate quiz questions
            String quizJson = "";
            try {
                quizJson = this.quizGenerator.generateQuiz(transcript);
            } catch (Exception e) {
                throw new RuntimeException("Error generating quiz: ", e);
            }
            System.out.println("Quiz JSON: " + quizJson);

            // Step 4: strip folder prefix so videoName is just the filename (e.g. "SampleVideo.mp4")
            String videoName = objectKey.contains("/")
                    ? objectKey.substring(objectKey.lastIndexOf("/") + 1)
                    : objectKey;

            // wrap the quiz questions array in an outer object with metadata
            String quizOutput = String.format("""
                {
                  "sourceVideo": "%s",
                  "generatedAt": "%s",
                  "questions": %s
                }
                """, videoName, Instant.now().toString(), quizJson);

            // Step 5: save the final quiz JSON to the quizzes/ folder in S3
            String quizKey = "quizzes/" + videoName.replace(".mp4", "") + "-quiz.json";

            if ("true".equalsIgnoreCase(System.getenv("MOCK_S3"))) {
                System.out.println("[MOCK] Skipping S3 write -- would have saved to " + quizKey);
            } else {
                S3Client s3Client = S3Client.create();
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(quizKey)
                                .contentType("application/json")
                                .build(),
                        RequestBody.fromString(quizOutput)
                );
                System.out.println("Quiz saved to s3://" + bucketName + "/" + quizKey);
            }
        }
        return "Processed " + s3Event.getRecords().size() + " record(s)";
    }
}
