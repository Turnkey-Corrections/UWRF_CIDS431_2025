package org.uwrf.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP Lambda handler for browser uploads of already-created transcript text.
 */
public class TranscriptApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final int MAX_TRANSCRIPT_CHARS = 120_000;

    private final TranscriptUploader transcriptUploader;
    private final QuizReader quizReader;
    private final ObjectMapper objectMapper;
    private final String quizBucketName;

    public TranscriptApiHandler() {
        this(S3Client.create(), System.getenv("QUIZ_BUCKET_NAME"), true);
    }

    TranscriptApiHandler(S3Client s3Client, String quizBucketName) {
        this((bucketName, transcriptKey, transcript) -> s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(transcriptKey)
                        .contentType("text/plain; charset=utf-8")
                        .build(),
                RequestBody.fromString(transcript, StandardCharsets.UTF_8)), quizBucketName);
    }

    TranscriptApiHandler(TranscriptUploader transcriptUploader, String quizBucketName) {
        this(transcriptUploader, null, quizBucketName);
    }

    TranscriptApiHandler(TranscriptUploader transcriptUploader, QuizReader quizReader, String quizBucketName) {
        this.transcriptUploader = transcriptUploader;
        this.quizReader = quizReader;
        this.quizBucketName = quizBucketName;
        this.objectMapper = new ObjectMapper();
    }

    TranscriptApiHandler(S3Client s3Client, String quizBucketName, boolean enableReads) {
        this(
                (bucketName, transcriptKey, transcript) -> s3Client.putObject(PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(transcriptKey)
                                .contentType("text/plain; charset=utf-8")
                                .build(),
                        RequestBody.fromString(transcript, StandardCharsets.UTF_8)),
                quizKey -> s3Client.getObject(builder -> builder.bucket(quizBucketName).key(quizKey),
                        ResponseTransformer.toBytes()).asUtf8String(),
                quizBucketName
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            if ("OPTIONS".equalsIgnoreCase(request.getHttpMethod())) {
                return response(204, "");
            }
            if ("GET".equalsIgnoreCase(request.getHttpMethod())) {
                return readQuiz(request);
            }

            if (request.getBody() == null || request.getBody().isBlank()) {
                return error(400, "Request body is required.");
            }

            JsonNode body = objectMapper.readTree(request.getBody());
            String sourceName = textValue(body, "sourceName", "uploaded-transcript.txt");
            String transcript = textValue(body, "transcript", "");

            if (transcript.isBlank()) {
                return error(400, "Transcript text is required.");
            }
            if (transcript.length() > MAX_TRANSCRIPT_CHARS) {
                return error(413, "Transcript is too large. Maximum is " + MAX_TRANSCRIPT_CHARS + " characters.");
            }
            if (quizBucketName == null || quizBucketName.isBlank() || transcriptUploader == null) {
                return error(500, "QUIZ_BUCKET_NAME is not configured.");
            }

            String transcriptKey = "transcripts/" + safeFileName(fileNameWithoutExtension(sourceName)) + ".txt";
            String outputKey = "quizzes/" + fileNameWithoutExtension(transcriptKey) + "-quiz.json";
            writeTranscript(transcriptKey, transcript);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("message", "Transcript uploaded to S3. The S3 event will trigger quiz generation.");
            result.put("bucket", quizBucketName);
            result.put("transcriptKey", transcriptKey);
            result.put("expectedQuizKey", outputKey);

            return response(200, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, "Failed to upload transcript: " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent readQuiz(APIGatewayProxyRequestEvent request) {
        try {
            if (quizReader == null) {
                return error(500, "Quiz reads are not configured.");
            }

            Map<String, String> params = request.getQueryStringParameters();
            String quizKey = params == null ? null : params.get("key");
            if (quizKey == null || quizKey.isBlank()) {
                return error(400, "Missing required query parameter: key");
            }
            if (!quizKey.startsWith("quizzes/") || !quizKey.endsWith(".json")) {
                return error(400, "Quiz key must be under quizzes/ and end with .json");
            }

            String quizJson = quizReader.read(quizKey);
            return response(200, quizJson);
        } catch (NoSuchKeyException e) {
            ObjectNode pending = objectMapper.createObjectNode();
            pending.put("status", "PENDING");
            pending.put("message", "Quiz has not been written to S3 yet.");
            return response(202, pending.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return error(500, "Failed to read quiz: " + e.getMessage());
        }
    }

    private String textValue(JsonNode node, String fieldName, String fallback) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return fallback;
        }
        return value.asText(fallback);
    }

    private void writeTranscript(String transcriptKey, String transcript) {
        transcriptUploader.upload(quizBucketName, transcriptKey, transcript);
    }

    private String fileNameWithoutExtension(String objectKey) {
        String fileName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    private String safeFileName(String value) {
        String safeName = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        return safeName.isBlank() ? "uploaded-transcript" : safeName;
    }

    private APIGatewayProxyResponseEvent error(int statusCode, String message) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("error", message);
        return response(statusCode, error.toString());
    }

    private APIGatewayProxyResponseEvent response(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of(
                        "Access-Control-Allow-Origin", "*",
                        "Access-Control-Allow-Headers", "Content-Type",
                        "Access-Control-Allow-Methods", "OPTIONS,POST,GET",
                        "Content-Type", "application/json"
                ))
                .withBody(body);
    }

    @FunctionalInterface
    interface TranscriptUploader {
        void upload(String bucketName, String transcriptKey, String transcript);
    }

    @FunctionalInterface
    interface QuizReader {
        String read(String quizKey);
    }
}
