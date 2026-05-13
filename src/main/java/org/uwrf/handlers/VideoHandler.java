package org.uwrf.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.uwrf.services.BedrockQuizGenerator;
import org.uwrf.services.MockQuizGenerator;
import org.uwrf.services.QuizGenerator;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
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

import java.time.Clock;
import java.time.Duration;
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
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(10);
    private static final int MAX_POLLS = 24;

    private final QuizGenerator quizGenerator;
    private final TranscriptReader transcriptReader;
    private final QuizWriter quizWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TranscribeClient transcribeClient;
    private final S3Client s3Client;

    /**
     * Default constructor used by AWS Lambda.
     * Checks the MOCK_BEDROCK environment variable to select the quiz generator.
     */
    public VideoHandler() {
        this("true".equalsIgnoreCase(System.getenv("MOCK_BEDROCK"))
                        ? new MockQuizGenerator()
                        : new BedrockQuizGenerator(),
                null,
                null,
                new ObjectMapper(),
                Clock.systemUTC(),
                TranscribeClient.create(),
                S3Client.create());
    }

    /**
     * Constructor for unit tests -- inject any QuizGenerator implementation directly.
     */
    VideoHandler(QuizGenerator quizGenerator) {
        this(quizGenerator,
                (bucketName, objectKey) -> "",
                (bucketName, objectKey, quizJson) -> { },
                new ObjectMapper(),
                Clock.systemUTC(),
                null,
                null);
    }

    VideoHandler(
            QuizGenerator quizGenerator,
            TranscriptReader transcriptReader,
            QuizWriter quizWriter,
            ObjectMapper objectMapper,
            Clock clock,
            TranscribeClient transcribeClient,
            S3Client s3Client
    ) {
        this.quizGenerator = quizGenerator;
        this.transcriptReader = transcriptReader;
        this.quizWriter = quizWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transcribeClient = transcribeClient;
        this.s3Client = s3Client;
    }

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

    // TODO: Step 4 - Build the quiz object
    // Wrap the quiz JSON array in an object that includes metadata:
    //   { "sourceVideo": objectKey, "generatedAt": "...", "questions": [...] }

    // TODO: Step 5 - Write the quiz JSON back to S3
    // Use S3Client to put a JSON file at "quizzes/<videoName>-quiz.json"


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
                String transcript = readTranscript(bucketName, objectKey);
                System.out.println("Transcript generated with " + transcript.length() + " characters");

                String quizQuestionsJson = this.quizGenerator.generateQuiz(transcript);
                String quizDocument = buildQuizDocument(objectKey, quizQuestionsJson);
                String outputKey = buildQuizKey(objectKey);

                writeQuiz(bucketName, outputKey, quizDocument);
                System.out.println("Quiz written to s3://" + bucketName + "/" + outputKey);
            } catch (Exception exception) {
                throw new RuntimeException("Failed to process uploaded video: " + objectKey, exception);
            }
        }

        return "Processed " + s3Event.getRecords().size() + " record(s)";
    }

    private String readTranscript(String bucketName, String objectKey) throws Exception {
        if (this.transcriptReader != null) {
            return this.transcriptReader.read(bucketName, objectKey);
        }

        if (this.transcribeClient == null || this.s3Client == null) {
            throw new IllegalStateException("AWS clients are not configured");
        }

        String transcriptKey = buildTranscriptKey(objectKey);
        String jobName = buildJobName(objectKey);
        String mediaUri = "s3://" + bucketName + "/" + objectKey;

        transcribeClient.startTranscriptionJob(StartTranscriptionJobRequest.builder()
                .transcriptionJobName(jobName)
                .languageCode("en-US")
                .media(Media.builder().mediaFileUri(mediaUri).build())
                .mediaFormat(resolveMediaFormat(objectKey))
                .outputBucketName(bucketName)
                .outputKey(transcriptKey)
                .build());

        waitForTranscription(jobName);

        try (ResponseInputStream<?> transcriptStream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucketName)
                .key(transcriptKey)
                .build())) {
            JsonNode transcriptNode = objectMapper.readTree(transcriptStream);
            return transcriptNode.get("results")
                    .get("transcripts")
                    .get(0)
                    .get("transcript")
                    .asText();
        }
    }

    private void writeQuiz(String bucketName, String objectKey, String quizJson) throws Exception {
        if (this.quizWriter != null) {
            this.quizWriter.writeQuiz(bucketName, objectKey, quizJson);
            return;
        }

        if (this.s3Client == null) {
            throw new IllegalStateException("S3 client is not configured");
        }

        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .contentType("application/json")
                        .build(),
                RequestBody.fromString(quizJson));
    }

    private String buildQuizDocument(String sourceVideo, String quizQuestionsJson) throws Exception {
        JsonNode questionsNode = objectMapper.readTree(quizQuestionsJson);
        if (!questionsNode.isArray()) {
            throw new IllegalArgumentException("Quiz generator must return a JSON array of questions");
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("sourceVideo", sourceVideo);
        root.put("generatedAt", Instant.now(clock).toString());
        root.set("questions", (ArrayNode) questionsNode);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private String buildQuizKey(String objectKey) {
        String fileName = fileNameOnly(objectKey);
        return "quizzes/" + stripExtension(fileName) + "-quiz.json";
    }

    private String buildTranscriptKey(String objectKey) {
        return "transcripts/" + stripExtension(fileNameOnly(objectKey)) + "-transcript.json";
    }

    private String buildJobName(String objectKey) {
        String baseName = stripExtension(fileNameOnly(objectKey))
                .replaceAll("[^A-Za-z0-9-_]", "-")
                .toLowerCase(Locale.ROOT);
        return "quiz-job-" + baseName + "-" + System.currentTimeMillis();
    }

    private String fileNameOnly(String objectKey) {
        int slashIndex = objectKey.lastIndexOf('/');
        return slashIndex >= 0 ? objectKey.substring(slashIndex + 1) : objectKey;
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    @FunctionalInterface
    interface TranscriptReader {
        String read(String bucketName, String objectKey) throws Exception;
    }

    @FunctionalInterface
    interface QuizWriter {
        void writeQuiz(String bucketName, String objectKey, String quizJson) throws Exception;
    }

    private void waitForTranscription(String jobName) throws InterruptedException {
        for (int attempt = 0; attempt < MAX_POLLS; attempt++) {
            GetTranscriptionJobResponse response = transcribeClient.getTranscriptionJob(GetTranscriptionJobRequest.builder()
                    .transcriptionJobName(jobName)
                    .build());

            TranscriptionJobStatus status = response.transcriptionJob().transcriptionJobStatus();
            if (status == TranscriptionJobStatus.COMPLETED) {
                return;
            }

            if (status == TranscriptionJobStatus.FAILED) {
                throw new IllegalStateException("Transcription failed: " + response.transcriptionJob().failureReason());
            }

            Thread.sleep(POLL_INTERVAL.toMillis());
        }

        throw new IllegalStateException("Transcription did not complete before Lambda timed out");
    }

    private MediaFormat resolveMediaFormat(String objectKey) {
        String lowerKey = objectKey.toLowerCase(Locale.ROOT);
        if (lowerKey.endsWith(".mp4")) {
            return MediaFormat.MP4;
        }
        if (lowerKey.endsWith(".mp3")) {
            return MediaFormat.MP3;
        }
        if (lowerKey.endsWith(".wav")) {
            return MediaFormat.WAV;
        }
        if (lowerKey.endsWith(".flac")) {
            return MediaFormat.FLAC;
        }
        if (lowerKey.endsWith(".m4a")) {
            return MediaFormat.MP4;
        }
        throw new IllegalArgumentException("Unsupported media format for key: " + objectKey);
    }
}
