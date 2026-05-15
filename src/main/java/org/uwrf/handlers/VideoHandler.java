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
                transcript = """
                        In this lecture we cover cloud computing fundamentals and AWS services.
                        Cloud computing is the delivery of computing services over the internet, including servers,
                        storage, databases, networking, software, and analytics. The three main cloud service models
                        are Infrastructure as a Service or IaaS, Platform as a Service or PaaS, and Software as a
                        Service or SaaS. IaaS provides virtualized computing resources, PaaS provides a platform
                        for developers to build applications, and SaaS delivers software over the internet.

                        Amazon Web Services, or AWS, is the world's most comprehensive cloud platform. Key AWS
                        services include Amazon S3 for object storage, Amazon EC2 for virtual machines, AWS Lambda
                        for serverless computing, Amazon RDS for managed databases, and Amazon DynamoDB for NoSQL.

                        AWS Lambda is a serverless compute service that runs code in response to events without
                        requiring you to manage servers. You only pay for the compute time you consume. Lambda
                        functions can be triggered by events from S3, API Gateway, DynamoDB, and many other services.
                        Lambda automatically scales your application by running code in response to each trigger.

                        Amazon S3, or Simple Storage Service, provides object storage with high availability and
                        durability of 99.999999999 percent. S3 organizes data into buckets and objects. Each object
                        can be up to 5 terabytes in size. S3 supports event notifications that can trigger Lambda
                        functions when objects are created, deleted, or modified.

                        AWS CDK, the Cloud Development Kit, is an open source framework for defining cloud
                        infrastructure using familiar programming languages like Java, Python, and TypeScript.
                        CDK synthesizes infrastructure into CloudFormation templates which are then deployed to AWS.
                        This approach is called Infrastructure as Code and allows teams to version control and
                        review infrastructure changes just like application code.

                        Amazon Transcribe is a speech to text service that uses machine learning to convert audio
                        to text. It supports multiple languages and can identify different speakers. Amazon Bedrock
                        is a fully managed service that provides access to foundation models from leading AI
                        companies including Anthropic, Meta, and Amazon itself. You can use Bedrock to build
                        generative AI applications without managing any machine learning infrastructure.

                        IAM, Identity and Access Management, controls who can access AWS resources and what actions
                        they can perform. IAM uses policies, roles, and users to manage permissions. A Lambda
                        execution role grants the function permission to call other AWS services like S3 and Bedrock.
                        Following the principle of least privilege means granting only the permissions required to
                        perform a task and nothing more.
                        """;
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
