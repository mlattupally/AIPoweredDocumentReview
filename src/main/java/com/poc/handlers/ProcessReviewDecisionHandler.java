package com.poc.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * ProcessReviewDecisionHandler
 *
 * Exposed via Lambda Function URL. Triggered when a reviewer clicks
 * Approve / Reject / Request More Info in the notification email.
 *
 * Query params (from the link built in SendReviewRequestWithCallbackHandler):
 *   token        – Step Functions task token
 *   decision     – APPROVED | REJECTED | REQUEST_MORE_INFO
 *   submissionId – submission being reviewed
 *   reviewerId   – email of the reviewer who clicked
 *
 * Steps:
 *   1. Validate query params
 *   2. Call sfn:SendTaskSuccess to resume the paused workflow
 *   3. Update DynamoDB status
 *   4. Return an HTML confirmation page
 *
 * IAM permissions required:
 *   states:SendTaskSuccess, dynamodb:UpdateItem on HealthcareSubmissions
 */
public class ProcessReviewDecisionHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final String TABLE_NAME = "HealthcareSubmissions";

    private final SfnClient sfnClient;
    private final DynamoDbClient dynamoDbClient;
    private final Gson gson;

    public ProcessReviewDecisionHandler() {
        this.sfnClient = SfnClient.builder().build();
        this.dynamoDbClient = DynamoDbClient.builder().build();
        this.gson = new GsonBuilder().create();
    }

    // Test constructor for dependency injection
    public ProcessReviewDecisionHandler(SfnClient sfnClient, DynamoDbClient dynamoDbClient) {
        this.sfnClient = sfnClient;
        this.dynamoDbClient = dynamoDbClient;
        this.gson = new GsonBuilder().create();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        LambdaLogger logger = context.getLogger();
        logger.log("[INFO] ProcessReviewDecision invoked");

        try {
            Map<String, String> queryParams = extractQueryParams(event);

            String taskToken   = queryParams.get("token");
            String decision    = queryParams.get("decision");
            String submissionId = queryParams.get("submissionId");
            String reviewerId  = queryParams.getOrDefault("reviewerId", "unknown");

            if (taskToken == null || taskToken.isBlank()) {
                return errorResponse(400, "Missing required parameter: token");
            }
            if (decision == null || decision.isBlank()) {
                return errorResponse(400, "Missing required parameter: decision");
            }
            if (submissionId == null || submissionId.isBlank()) {
                return errorResponse(400, "Missing required parameter: submissionId");
            }
            if (!decision.equals("APPROVED") && !decision.equals("REJECTED") && !decision.equals("REQUEST_MORE_INFO")) {
                return errorResponse(400, "Invalid decision value: " + decision);
            }

            logger.log("[INFO] Decision=" + decision + " submissionId=" + submissionId + " reviewer=" + reviewerId);

            // Resume the paused Step Functions workflow
            Map<String, String> output = new HashMap<>();
            output.put("decision", decision);
            output.put("submissionId", submissionId);
            output.put("status", decision);
            output.put("reviewerId", reviewerId);
            output.put("decidedAt", Instant.now().toString());

            sfnClient.sendTaskSuccess(SendTaskSuccessRequest.builder()
                .taskToken(taskToken)
                .output(gson.toJson(output))
                .build());

            logger.log("[INFO] Step Functions workflow resumed successfully");

            // Update DynamoDB with the reviewer's decision
            updateSubmissionStatus(submissionId, decision, reviewerId);

            logger.log("[INFO] DynamoDB updated for submissionId=" + submissionId);

            return htmlResponse(200, buildConfirmationHtml(decision, submissionId, reviewerId));

        } catch (IllegalArgumentException e) {
            return errorResponse(400, e.getMessage());
        } catch (Exception e) {
            context.getLogger().log("[ERROR] " + e.getMessage());
            e.printStackTrace();
            return errorResponse(500, "Internal error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractQueryParams(Map<String, Object> event) {
        Object raw = event.get("queryStringParameters");
        if (raw instanceof Map) {
            return (Map<String, String>) raw;
        }
        return new HashMap<>();
    }

    private void updateSubmissionStatus(String submissionId, String decision, String reviewerId) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of("submissionId", AttributeValue.builder().s(submissionId).build()))
            .updateExpression("SET #st = :status, reviewerId = :reviewer, decidedAt = :ts")
            .expressionAttributeNames(Map.of("#st", "status"))
            .expressionAttributeValues(Map.of(
                ":status",   AttributeValue.builder().s(decision).build(),
                ":reviewer", AttributeValue.builder().s(reviewerId).build(),
                ":ts",       AttributeValue.builder().s(Instant.now().toString()).build()
            ))
            .build());
    }

    private String buildConfirmationHtml(String decision, String submissionId, String reviewerId) {
        String color   = decision.equals("APPROVED") ? "#28a745"
                       : decision.equals("REJECTED") ? "#dc3545" : "#fd7e14";
        String label   = decision.equals("APPROVED") ? "Approved"
                       : decision.equals("REJECTED") ? "Rejected" : "More Information Requested";
        String message = decision.equals("APPROVED")
                       ? "The submission has been approved and will proceed to the next processing stage."
                       : decision.equals("REJECTED")
                       ? "The submission has been rejected. The submitter will be notified."
                       : "A request for additional information has been sent to the submitter.";

        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family:Arial,sans-serif;max-width:500px;margin:60px auto;text-align:center;color:#333">
              <div style="border:2px solid %s;border-radius:8px;padding:40px">
                <h1 style="color:%s">%s</h1>
                <p style="font-size:1.1em">%s</p>
                <hr style="margin:24px 0"/>
                <table style="margin:auto;text-align:left;width:100%%">
                  <tr><td style="font-weight:bold;padding:4px">Submission ID</td><td>%s</td></tr>
                  <tr><td style="font-weight:bold;padding:4px">Reviewed By</td><td>%s</td></tr>
                  <tr><td style="font-weight:bold;padding:4px">Decision Time</td><td>%s</td></tr>
                </table>
                <p style="margin-top:32px;color:#999;font-size:0.85em">
                  You may close this window. The automated workflow has been updated.
                </p>
              </div>
            </body>
            </html>
            """,
            color, color, label, message,
            submissionId, reviewerId, Instant.now().toString()
        );
    }

    private Map<String, Object> htmlResponse(int statusCode, String html) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("headers", Map.of("Content-Type", "text/html"));
        response.put("body", html);
        return response;
    }

    private Map<String, Object> errorResponse(int statusCode, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("headers", Map.of("Content-Type", "text/html"));
        response.put("body", String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family:Arial,sans-serif;max-width:500px;margin:60px auto;text-align:center;color:#333">
              <div style="border:2px solid #dc3545;border-radius:8px;padding:40px">
                <h1 style="color:#dc3545">Error %d</h1>
                <p>%s</p>
              </div>
            </body>
            </html>
            """, statusCode, message));
        return response;
    }
}
