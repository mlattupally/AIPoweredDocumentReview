package com.poc.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Lambda Function: UpdateSubmissionStatusHandler
 * Purpose: Update submission status in DynamoDB after approval/review
 *
 * Input Event:
 * {
 *   "submissionId": "SUB-2024-001",      // Required
 *   "status": "APPROVED",                // Required
 *   "assignedTo": "AUTO_SYSTEM"          // Optional - only updates if provided
 * }
 *
 * Note: Only status and updatedAt are always updated.
 *       Other fields are only updated if provided and non-null.
 *
 * Output:
 * {
 *   "statusCode": 200,
 *   "submissionId": "SUB-2024-001",
 *   "message": "Status updated successfully",
 *   "status": "success"
 * }
 */
public class UpdateSubmissionStatusHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamoDbClient;
    private static final String TABLE_NAME = "HealthcareSubmissions";

    // Constructor
    public UpdateSubmissionStatusHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().build();
    }

    // Constructor for testing
    public UpdateSubmissionStatusHandler(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        LambdaLogger logger = context.getLogger();

        logger.log("[INFO] UpdateSubmissionStatus invoked at " + Instant.now().toString());
        logger.log("[INFO] Event: " + event.toString());

        Map<String, Object> response = new HashMap<>();

        try {
            // Extract parameters
            String submissionId = (String) event.get("submissionId");
            String status = (String) event.get("status");
            String assignedTo = (String) event.get("assignedTo");

            // Validate inputs
            if (submissionId == null || submissionId.isEmpty()) {
                throw new IllegalArgumentException("submissionId is required");
            }
            if (status == null || status.isEmpty()) {
                throw new IllegalArgumentException("status is required");
            }

            logger.log("[INFO] Updating submission: " + submissionId + " to status: " + status);

            // Build dynamic update expression based on provided fields
            StringBuilder updateExpression = new StringBuilder("SET #status = :status, #updatedAt = :updatedAt");
            Map<String, String> expressionAttributeNames = new HashMap<>();
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();

            // Always update status and timestamp
            expressionAttributeNames.put("#status", "status");
            expressionAttributeNames.put("#updatedAt", "updatedAt");
            expressionAttributeValues.put(":status", AttributeValue.builder().s(status).build());
            expressionAttributeValues.put(":updatedAt", AttributeValue.builder().s(Instant.now().toString()).build());

            // Only update assignedTo if provided
            if (assignedTo != null && !assignedTo.isEmpty()) {
                updateExpression.append(", #assignedTo = :assignedTo");
                expressionAttributeNames.put("#assignedTo", "assignedTo");
                expressionAttributeValues.put(":assignedTo", AttributeValue.builder().s(assignedTo).build());
            }

            // Add approval timestamp if status is APPROVED
            if ("APPROVED".equals(status)) {
                updateExpression.append(", #approvedAt = :approvedAt");
                expressionAttributeNames.put("#approvedAt", "approvedAt");
                expressionAttributeValues.put(":approvedAt", AttributeValue.builder().s(Instant.now().toString()).build());
            }

            // Update item in DynamoDB
            UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("submissionId", AttributeValue.builder().s(submissionId).build()))
                    .updateExpression(updateExpression.toString())
                    .expressionAttributeNames(expressionAttributeNames)
                    .expressionAttributeValues(expressionAttributeValues)
                    .returnValues(ReturnValue.UPDATED_NEW)
                    .build();

            UpdateItemResponse updateResponse = dynamoDbClient.updateItem(updateRequest);

            logger.log("[SUCCESS] Status updated successfully for submission: " + submissionId);
            logger.log("[INFO] Updated attributes: " + updateResponse.attributes());

            // Build success response
            response.put("statusCode", 200);
            response.put("submissionId", submissionId);
            response.put("message", "Status updated successfully to " + status);
            response.put("updatedAttributes", convertAttributeMapToStringMap(updateResponse.attributes()));
            response.put("status", "success");

            return response;

        } catch (ResourceNotFoundException e) {
            logger.log("[ERROR] Table not found: " + e.getMessage());

            response.put("statusCode", 404);
            response.put("error", "TableNotFound");
            response.put("message", "DynamoDB table '" + TABLE_NAME + "' not found");
            response.put("status", "error");

            return response;

        } catch (IllegalArgumentException e) {
            logger.log("[ERROR] Invalid input: " + e.getMessage());

            response.put("statusCode", 400);
            response.put("error", "InvalidInput");
            response.put("message", e.getMessage());
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
     * Convert DynamoDB AttributeValue map to simple String map for response
     */
    private Map<String, String> convertAttributeMapToStringMap(Map<String, AttributeValue> attributeMap) {
        Map<String, String> result = new HashMap<>();
        if (attributeMap != null) {
            attributeMap.forEach((key, value) -> result.put(key, value.s()));
        }
        return result;
    }
}
