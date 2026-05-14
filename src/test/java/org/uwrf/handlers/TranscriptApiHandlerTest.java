package org.uwrf.handlers;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptApiHandlerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void handleRequest_withTranscript_uploadsTranscriptToS3() throws Exception {
        UploadedTranscript uploadedTranscript = new UploadedTranscript();
        TranscriptApiHandler handler = new TranscriptApiHandler(
                (bucketName, transcriptKey, transcript) -> {
                    uploadedTranscript.bucketName = bucketName;
                    uploadedTranscript.transcriptKey = transcriptKey;
                    uploadedTranscript.transcript = transcript;
                },
                quizKey -> "{}",
                "lecture-bucket"
        );

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody("""
                        {
                          "sourceName": "SampleVideo.txt",
                          "transcript": "This lecture covers distributed storage."
                        }""");

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);
        JsonNode body = objectMapper.readTree(response.getBody());

        assertEquals(200, response.getStatusCode());
        assertEquals("lecture-bucket", body.get("bucket").asText());
        assertEquals("transcripts/samplevideo.txt", body.get("transcriptKey").asText());
        assertEquals("quizzes/samplevideo-quiz.json", body.get("expectedQuizKey").asText());
        assertEquals("lecture-bucket", uploadedTranscript.bucketName);
        assertEquals("transcripts/samplevideo.txt", uploadedTranscript.transcriptKey);
        assertEquals("This lecture covers distributed storage.", uploadedTranscript.transcript);
    }

    @Test
    void handleRequest_withoutTranscript_returnsBadRequest() throws Exception {
        TranscriptApiHandler handler = new TranscriptApiHandler((bucketName, transcriptKey, transcript) -> {}, quizKey -> "{}", "lecture-bucket");

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody("{\"sourceName\":\"empty.txt\",\"transcript\":\"\"}");

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);
        JsonNode body = objectMapper.readTree(response.getBody());

        assertEquals(400, response.getStatusCode());
        assertTrue(body.get("error").asText().contains("Transcript text is required"));
    }

    @Test
    void handleRequest_getQuiz_returnsQuizJsonFromS3() throws Exception {
        TranscriptApiHandler handler = new TranscriptApiHandler(
                (bucketName, transcriptKey, transcript) -> {},
                quizKey -> """
                        {
                          "sourceVideo": "SampleVideo.txt",
                          "questions": [
                            {
                              "question": "What is discussed?",
                              "options": {"A": "Distributed systems", "B": "Gardening", "C": "Painting", "D": "Cooking"},
                              "correctAnswer": "A"
                            }
                          ]
                        }""",
                "lecture-bucket"
        );

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withQueryStringParameters(Map.of("key", "quizzes/SampleVideo-quiz.json"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);
        JsonNode body = objectMapper.readTree(response.getBody());

        assertEquals(200, response.getStatusCode());
        assertEquals("What is discussed?", body.get("questions").get(0).get("question").asText());
    }

    private static class UploadedTranscript {
        String bucketName;
        String transcriptKey;
        String transcript;
    }
}
