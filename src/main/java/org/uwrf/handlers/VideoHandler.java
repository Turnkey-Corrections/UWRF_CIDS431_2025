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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobResponse;
import software.amazon.awssdk.services.transcribe.model.Media;
import software.amazon.awssdk.services.transcribe.model.MediaFormat;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.TranscriptionJobStatus;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

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

    private static final int POLL_INTERVAL_MILLIS = 10_000;

    private final QuizGenerator quizGenerator;
    private final S3Client s3Client;
    private final TranscribeClient transcribeClient;
    private final ObjectMapper objectMapper;
    private final boolean localTestMode;

    /**
     * Default constructor used by AWS Lambda.
     * Checks the MOCK_BEDROCK environment variable to select the quiz generator.
     */
    public VideoHandler() {
        this("true".equalsIgnoreCase(System.getenv("MOCK_BEDROCK"))
                ? new MockQuizGenerator()
                : new BedrockQuizGenerator(),
                S3Client.create(),
                TranscribeClient.create(),
                false);
    }

    /**
     * Constructor for unit tests -- inject any QuizGenerator implementation directly.
     */
    VideoHandler(QuizGenerator quizGenerator) {
        this(quizGenerator, null, null, true);
    }

    VideoHandler(QuizGenerator quizGenerator, S3Client s3Client, TranscribeClient transcribeClient, boolean localTestMode) {
        this.quizGenerator = quizGenerator;
        this.s3Client = s3Client;
        this.transcribeClient = transcribeClient;
        this.objectMapper = new ObjectMapper();
        this.localTestMode = localTestMode;
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

            try {
                String decodedObjectKey = URLDecoder.decode(objectKey, StandardCharsets.UTF_8);
                String transcript;

                if (localTestMode) {
                    transcript = "Local test transcript for " + decodedObjectKey;
                } else if (isTranscriptObject(decodedObjectKey)) {
                    transcript = readTranscriptObject(bucketName, decodedObjectKey);
                } else {
                    String transcriptionJobName = buildJobName(decodedObjectKey);
                    String transcriptOutputKey = "transcripts/" + transcriptionJobName + ".json";

                    startTranscriptionJob(bucketName, decodedObjectKey, transcriptionJobName, transcriptOutputKey);
                    waitForTranscriptionJob(transcriptionJobName, context);
                    transcript = readTranscript(bucketName, transcriptOutputKey);
                }

                String quizJson = this.quizGenerator.generateQuiz(transcript);
                String finalQuizJson = buildQuizObject(decodedObjectKey, quizJson);
                String quizOutputKey = buildQuizOutputKey(decodedObjectKey);

                if (localTestMode) {
                    System.out.println("[LOCAL TEST] Would write quiz JSON to s3://" + bucketName + "/" + quizOutputKey);
                } else {
                    writeQuiz(bucketName, quizOutputKey, finalQuizJson);
                    System.out.println("Wrote quiz JSON to s3://" + bucketName + "/" + quizOutputKey);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to process S3 object " + bucketName + "/" + objectKey, e);
            }
        }

        return "Processed " + s3Event.getRecords().size() + " record(s)";
    }

    private boolean isTranscriptObject(String objectKey) {
        String lowerKey = objectKey.toLowerCase(Locale.ROOT);
        return lowerKey.startsWith("transcripts/") && (lowerKey.endsWith(".txt") || lowerKey.endsWith(".json"));
    }

    private void startTranscriptionJob(String bucketName, String objectKey, String jobName, String outputKey) {
        String mediaUri = "s3://" + bucketName + "/" + objectKey;
        System.out.println("Starting Transcribe job: " + jobName);

        transcribeClient.startTranscriptionJob(StartTranscriptionJobRequest.builder()
                .transcriptionJobName(jobName)
                .languageCode("en-US")
                .mediaFormat(detectMediaFormat(objectKey))
                .media(Media.builder().mediaFileUri(mediaUri).build())
                .outputBucketName(bucketName)
                .outputKey(outputKey)
                .build());
    }

    private void waitForTranscriptionJob(String jobName, Context context) throws InterruptedException {
        while (true) {
            GetTranscriptionJobResponse response = transcribeClient.getTranscriptionJob(GetTranscriptionJobRequest.builder()
                    .transcriptionJobName(jobName)
                    .build());

            TranscriptionJobStatus status = response.transcriptionJob().transcriptionJobStatus();
            System.out.println("Transcribe job " + jobName + " status: " + status);

            if (status == TranscriptionJobStatus.COMPLETED) {
                return;
            }
            if (status == TranscriptionJobStatus.FAILED) {
                throw new IllegalStateException("Transcribe job failed: " + response.transcriptionJob().failureReason());
            }
            if (context != null && context.getRemainingTimeInMillis() < POLL_INTERVAL_MILLIS + 5_000) {
                throw new IllegalStateException("Lambda is about to time out before Transcribe completed");
            }

            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
    }

    private String readTranscript(String bucketName, String transcriptOutputKey) throws Exception {
        System.out.println("Reading transcript from s3://" + bucketName + "/" + transcriptOutputKey);

        String transcriptJson = s3Client.getObject(GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(transcriptOutputKey)
                        .build(),
                ResponseTransformer.toBytes()).asUtf8String();

        JsonNode transcriptNode = objectMapper.readTree(transcriptJson);
        return transcriptNode.get("results").get("transcripts").get(0).get("transcript").asText();
    }

    private String readTranscriptObject(String bucketName, String transcriptObjectKey) throws Exception {
        System.out.println("Reading uploaded transcript from s3://" + bucketName + "/" + transcriptObjectKey);

        String transcriptObject = s3Client.getObject(GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(transcriptObjectKey)
                        .build(),
                ResponseTransformer.toBytes()).asUtf8String();

        if (transcriptObjectKey.toLowerCase(Locale.ROOT).endsWith(".json")) {
            JsonNode transcriptNode = objectMapper.readTree(transcriptObject);
            JsonNode transcript = transcriptNode.path("results").path("transcripts").path(0).path("transcript");
            if (transcript.isMissingNode() || transcript.asText().isBlank()) {
                throw new IllegalArgumentException("Transcript JSON does not contain results.transcripts[0].transcript");
            }
            return transcript.asText();
        }

        return transcriptObject;
    }

    private String buildQuizObject(String sourceVideo, String quizJson) throws Exception {
        ObjectNode quizObject = objectMapper.createObjectNode();
        quizObject.put("sourceVideo", sourceVideo);
        quizObject.put("generatedAt", Instant.now().toString());
        quizObject.set("questions", objectMapper.readTree(quizJson));
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(quizObject);
    }

    private void writeQuiz(String bucketName, String quizOutputKey, String quizJson) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(quizOutputKey)
                        .contentType("application/json")
                        .build(),
                RequestBody.fromString(quizJson, StandardCharsets.UTF_8));
    }

    private String buildJobName(String objectKey) {
        String baseName = fileNameWithoutExtension(objectKey);
        String safeName = baseName.replaceAll("[^A-Za-z0-9._-]", "-");
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        return trimToMaxLength(safeName + "-" + timestamp, 200);
    }

    private String buildQuizOutputKey(String objectKey) {
        return "quizzes/" + fileNameWithoutExtension(objectKey) + "-quiz.json";
    }

    private String fileNameWithoutExtension(String objectKey) {
        String fileName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    private String trimToMaxLength(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(value.length() - maxLength);
    }

    private MediaFormat detectMediaFormat(String objectKey) {
        String lowerKey = objectKey.toLowerCase(Locale.ROOT);
        if (lowerKey.endsWith(".mp3")) {
            return MediaFormat.MP3;
        }
        if (lowerKey.endsWith(".wav")) {
            return MediaFormat.WAV;
        }
        if (lowerKey.endsWith(".flac")) {
            return MediaFormat.FLAC;
        }
        if (lowerKey.endsWith(".ogg")) {
            return MediaFormat.OGG;
        }
        if (lowerKey.endsWith(".amr")) {
            return MediaFormat.AMR;
        }
        if (lowerKey.endsWith(".webm")) {
            return MediaFormat.WEBM;
        }
        return MediaFormat.MP4;
    }
}
