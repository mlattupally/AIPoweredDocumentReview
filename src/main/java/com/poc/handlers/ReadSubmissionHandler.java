package com.poc.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lambda Function: ReadSubmissionHandler
 * Purpose: Read healthcare submission text file from S3 when file is uploaded
 *
 * Triggered by:
 *   1. S3 Event Notification (single file upload - original flow)
 *      Input: { "Records": [{ "s3": { "bucket": { "name": "..." }, "object": { "key": "..." } } }] }
 *
 *   2. Step Functions Distributed Map (batch flow - new)
 *      Input: { "s3Bucket": "healthcare-submissions-poc", "s3Key": "submissions/SUB-001.txt" }
 *
 * Output:
 * {
 *   "statusCode": 200,
 *   "submissionId": "SUB-2024-001234",
 *   "submissionText": "HEALTHCARE PLAN SUBMISSION...",
 *   "s3Bucket": "healthcare-submissions-poc",
 *   "s3Key": "submissions/SUB-2024-001234.json",
 *   "fileSize": 2048,
 *   "readTimestamp": "2024-01-15T14:23:11Z",
 *   "status": "success"
 * }
 */
public class ReadSubmissionHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final S3Client s3Client;

    // Constructor - S3 client initialization
    public ReadSubmissionHandler() {
        this.s3Client = S3Client.builder().build();
    }

    // Constructor for testing with mock S3 client
    public ReadSubmissionHandler(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        LambdaLogger logger = context.getLogger();

        logger.log("[INFO] Lambda invoked at " + Instant.now().toString());

        Map<String, Object> response = new HashMap<>();

        try {
            // Detect input format: S3 Event (single file) vs Distributed Map (batch)
            String s3Bucket;
            String s3Key;

            if (event.containsKey("Records")) {
                // ── Format 1: EventBridge input template → Step Functions → Lambda (original single-file flow) ──
                // EventBridge reconstructs Records[] structure matching standard S3 event format
                logger.log("[INFO] Input format: S3 Records (via EventBridge input template)");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> records = (List<Map<String, Object>>) event.get("Records");
                @SuppressWarnings("unchecked")
                Map<String, Object> s3Section = (Map<String, Object>) records.get(0).get("s3");
                @SuppressWarnings("unchecked")
                Map<String, Object> bucketSection = (Map<String, Object>) s3Section.get("bucket");
                @SuppressWarnings("unchecked")
                Map<String, Object> objectSection = (Map<String, Object>) s3Section.get("object");

                s3Bucket = (String) bucketSection.get("name");
                s3Key = URLDecoder.decode((String) objectSection.get("key"), StandardCharsets.UTF_8.toString());
            } else if (event.containsKey("s3Key")) {
                // ── Format 2: Distributed Map invocation (batch flow) ──
                logger.log("[INFO] Input format: Distributed Map");
                s3Bucket = (String) event.get("s3Bucket");
                s3Key = (String) event.get("s3Key");
            } else {
                throw new IllegalArgumentException("Unrecognized input format. Expected S3 Records[] or Distributed Map ({s3Bucket, s3Key})");
            }

            // Extract submission ID from filename (e.g., "submissions/SUB-2024-001234.json" -> "SUB-2024-001234")
            String submissionId = extractSubmissionIdFromKey(s3Key);

            logger.log("[INFO] Bucket: " + s3Bucket);
            logger.log("[INFO] Key: " + s3Key);
            logger.log("[INFO] Extracted Submission ID: " + submissionId);
            logger.log("[INFO] Reading file from S3: s3://" + s3Bucket + "/" + s3Key);

            // Read file from S3
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(s3Key)
                    .build();

            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);

            // Read content
            String submissionText = new BufferedReader(
                new InputStreamReader(s3Object, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

            long fileSize = s3Object.response().contentLength();
            String contentType = s3Object.response().contentType();

            logger.log("[SUCCESS] File read successfully. Size: " + fileSize + " bytes");
            logger.log("[INFO] First 100 chars: " + submissionText.substring(0, Math.min(100, submissionText.length())) + "...");

            // Build metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("s3Bucket", s3Bucket);
            metadata.put("s3Key", s3Key);
            metadata.put("contentType", contentType != null ? contentType : "text/plain");

            // Build success response
            response.put("statusCode", 200);
            response.put("submissionId", submissionId);
            response.put("submissionText", submissionText);
            response.put("s3Bucket", s3Bucket);
            response.put("s3Key", s3Key);
            response.put("fileSize", fileSize);
            response.put("readTimestamp", Instant.now().toString());
            response.put("metadata", metadata);
            response.put("status", "success");

            return response;

        } catch (NoSuchKeyException e) {
            logger.log("[ERROR] File not found: " + e.getMessage());

            response.put("statusCode", 404);
            response.put("error", "FileNotFound");
            response.put("message", "File not found in S3");
            response.put("status", "error");

            return response;

        } catch (Exception e) {
            logger.log("[ERROR] Unexpected error: " + e.getMessage());
            e.printStackTrace();

            response.put("statusCode", 500);
            response.put("error", e.getClass().getSimpleName());
            response.put("message", e.getMessage());
            response.put("status", "error");

            return response;
        }
    }

    /**
     * Extract submission ID from S3 key
     * Examples:
     *   "submissions/SUB-2024-001234.json" -> "SUB-2024-001234"
     *   "submissions/SUB-2024-001234.txt" -> "SUB-2024-001234"
     *   "SUB-2024-001234.json" -> "SUB-2024-001234"
     */
    private String extractSubmissionIdFromKey(String s3Key) {
        // Get filename from path
        String filename = s3Key.substring(s3Key.lastIndexOf('/') + 1);

        // Remove file extension
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            return filename.substring(0, dotIndex);
        }-good.

        return filename;
    }
}
