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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Lambda function that handles S3 events when a video file is uploaded.
 * YOUR TASKS:
 * 1. Call AWS Transcribe to convert the video's audio to text
 * 2. Send the transcript to AWS Bedrock to generate quiz questions
 * 3. Write the quiz JSON back to S3
 * COST TIP: This handler uses a QuizGenerator interface so you can develop locally
 * without paying for Bedrock tokens. Set the Lambda environment variable:
 *   MOCK_BEDROCK=true   → uses MockQuizGenerator (free, returns canned questions)
 *   MOCK_BEDROCK=false  → uses BedrockQuizGenerator (real AI, costs money)
 */
// TODO: Step 1 - Call AWS Transcribe
// TODO: Step 2 - Wait for transcription to complete and get the text
// TODO: Step 3 - Call Bedrock with the transcript
// TODO: Step 4 - Build the quiz object
// TODO: Step 5 - Write the quiz JSON back to S3

public class VideoHandler implements RequestHandler<S3Event, String> {

    @FunctionalInterface
    public interface TranscriptReader{
        String readTranscript(String bucketName, String objectKey);
    }

    @FunctionalInterface
    public interface QuizWriter{
        void writeQuiz(String bucketName, String objectKey, String quizJson);
    }

    private final QuizGenerator quizGenerator;
    private final TranscriptReader transcriptReader;
    private final QuizWriter quizWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Default constructor used by AWS Lambda.
     * Checks the MOCK_BEDROCK environment variable to select the quiz generator.
     */
    public VideoHandler() {
        this(
                "true".equalsIgnoreCase(System.getenv("MOCK_BEDROCK"))
                        ? new MockQuizGenerator()
                        : new BedrockQuizGenerator(),
                "true".equalsIgnoreCase(System.getenv("MOCK_TRANSCRIBE"))
                        ? buildMockTranscriptReader()
                        : buildRealTranscriptReader(),
                buildRealQuizWriter(),
                new ObjectMapper(),
                Clock.systemUTC(),
                null,
                null
        );
    }

    /**
     * Constructor for unit tests -- inject any QuizGenerator implementation directly.
     */
    VideoHandler(QuizGenerator quizGenerator) {
        this(
                quizGenerator,
                buildMockTranscriptReader(),
                buildRealQuizWriter(),
                new ObjectMapper(),
                Clock.systemUTC(),
                null,
                null
        );
    }

    public VideoHandler(QuizGenerator quizGenerator,
                        TranscriptReader transcriptReader,
                        QuizWriter quizWriter,
                        ObjectMapper objectMapper,
                        Clock clock,
                        Object transcribeClient,
                        Object s3Client) {
        this.quizGenerator = quizGenerator;
        this.transcriptReader = transcriptReader;
        this.quizWriter = quizWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
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

            // TODO: Step 1 - Call AWS Transcribe
            // Use the bucketName and objectKey to start a transcription job
            // Hint: TranscribeClient transcribeClient = TranscribeClient.create();
            // Start a job with transcribeClient.startTranscriptionJob(...)
            // Output the transcript JSON to S3 using outputBucketName and outputKey
            //String jobName = StartTranscriptionJob(transcribeClient, bucketName, objectKey);
            // TODO: Step 2 - Wait for transcription to complete and get the text
            // Transcription is async -- poll getTranscriptionJob() until status is COMPLETED
            // Then read the transcript JSON from S3 and extract the text:
            //   transcriptNode.get("results").get("transcripts").get(0).get("transcript").asText()

            String transcript = transcriptReader.readTranscript(bucketName,objectKey);
            System.out.println("transcript extracted, length is: "+ transcript.length());

            // TODO: Step 3 - Call Bedrock with the transcript
            // Replace "transcript" below with the actual transcript string from Step 2:
            //   String quizJson = this.quizGenerator.generateQuiz(transcript);
            // While MOCK_BEDROCK=true this returns canned questions at zero cost.
            // Flip MOCK_BEDROCK=false when you're ready to test real AI generation.
            String quizJson;
            try{
                quizJson = quizGenerator.generateQuiz(transcript);
            }
            catch ( Exception e){
                throw new RuntimeException("quiz generation failed :/ " + e);
            }


            // TODO: Step 4 - Build the quiz object
            // Wrap the quiz JSON array in an object that includes metadata:
            //   { "sourceVideo": objectKey, "generatedAt": "...", "questions": [...] }
            //String videoName= objectKey.replace("/", "-").replace(".mp4", "");
            //packages the quiz up
            String videoName = objectKey
                    .substring(objectKey.lastIndexOf('/')+1)
                    .replace(".mp4", "");
            String finalQuiz;
            try{
                JsonNode questionsNode = objectMapper.readTree(quizJson);
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("sourceVideo",objectKey);
                payload.put("generatedAt", Instant.now(clock).toString());
                payload.set("questions", questionsNode);
                finalQuiz = objectMapper.writeValueAsString(payload);
            }
            catch (Exception e){
                throw new RuntimeException("failed to build quiz payload "+ e);
            }

            // TODO: Step 5 - Write the quiz JSON back to S3
            // Use S3Client to put a JSON file at "quizzes/<videoName>-quiz.json"
            String quizKey = "quizzes/" + videoName + "-quiz.json";
            quizWriter.writeQuiz(bucketName,quizKey,finalQuiz);
            System.out.println("quiz written to : "+ quizKey);
        }

        return "Processed " + s3Event.getRecords().size() + " record(s)";
    }
    private static TranscriptReader buildRealTranscriptReader(){
        TranscribeClient transcribeClient = TranscribeClient.create();
        S3Client s3Client = S3Client.create();
        return (bucketName, objectKey) -> {
            String jobName = objectKey.replace("/","-").replace(".mp4","")+"-"+ UUID.randomUUID();
            StartTranscriptionJobRequest startRequest = StartTranscriptionJobRequest.builder()
                    .outputBucketName(bucketName)
                    .transcriptionJobName(jobName)
                    .mediaFormat(MediaFormat.MP4)
                    .languageCode(LanguageCode.EN_US)
                    .media(Media.builder()
                            .mediaFileUri("s3://" + bucketName + "/" + objectKey)
                            .build())
                    .build();
            transcribeClient.startTranscriptionJob(startRequest);

            GetTranscriptionJobRequest getRequest = GetTranscriptionJobRequest.builder()
                    .transcriptionJobName(jobName)
                    .build();
            TranscriptionJobStatus status = TranscriptionJobStatus.IN_PROGRESS;
            while (status == TranscriptionJobStatus.IN_PROGRESS || status == TranscriptionJobStatus.QUEUED) {
                try{
                    Thread.sleep(5000);
                }
                catch (InterruptedException e){
                    throw new RuntimeException(e);
                }

                status = transcribeClient.getTranscriptionJob(getRequest)
                        .transcriptionJob()
                        .transcriptionJobStatus();
                System.out.println("Transcription Job Status: " + status);
            }
            if (status != TranscriptionJobStatus.COMPLETED) {
                throw new RuntimeException("Transcription Job Failed, Status: "+ status);
            }
            String transcriptJson = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(jobName + ".json")
                            .build()
            ).asUtf8String();

            try{
                JsonNode root = new ObjectMapper().readTree(transcriptJson);
                return root.get("results").get("transcripts").get(0).get("transcript").asText();
            }
            catch (Exception e){
                throw new RuntimeException("Failed to parse transcript JSON: "+ e);
            }
        };

    }
    private static QuizWriter buildRealQuizWriter(){
        S3Client s3Client = S3Client.create();
        return (bucketName, objectKey, quizJson) ->
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(objectKey)
                                .contentType("application/json")
                                .build(),
                        RequestBody.fromString(quizJson)
                );
    }

    private static TranscriptReader buildMockTranscriptReader(){
        return (bucketName, objectKey) -> {
            System.out.println("making mock transcriptor for : "+ objectKey);
            return "transcript for "+ bucketName +"/"+ objectKey;
        };
    }
}

