package com.poc.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * SendReviewRequestWithCallbackHandler
 *
 * This Lambda is invoked by Step Functions with waitForTaskToken.
 * It sends an SNS email to the reviewer with action links containing the task token.
 * The workflow PAUSES until ProcessReviewDecisionHandler calls sendTaskSuccess().
 *
 * Input from Step Functions:
 * - taskToken: The token Step Functions provides (THIS IS KEY!)
 * - submissionId: From validation results
 * - riskScore: From AI analysis
 * - issues: List of issues found
 * - reviewerEmail: Who to assign this to
 * - organizationName: From submission
 *
 * Output: None (workflow stays paused until callback)
 */
public class SendReviewRequestWithCallbackHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final SesClient sesClient;
    private final DynamoDbClient dynamoDbClient;
    private final Gson gson;

    // Configuration - you'll update these when deploying
    private static final String SENDER_EMAIL = System.getenv().getOrDefault("SENDER_EMAIL", "noreply@yourcompany.com");
    private static final String LAMBDA_FUNCTION_URL = System.getenv("LAMBDA_FUNCTION_URL");
    // Example: https://abc123xyz.lambda-url.us-east-1.on.aws

    public SendReviewRequestWithCallbackHandler() {
        this.sesClient = SesClient.builder().build();
        this.dynamoDbClient = DynamoDbClient.builder().build();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        context.getLogger().log("SendReviewRequestWithCallback invoked: " + gson.toJson(event));

        try {
            // Extract the TASK TOKEN - this is what allows callback to work!
            String taskToken = (String) event.get("taskToken");
            if (taskToken == null || taskToken.isEmpty()) {
                throw new IllegalArgumentException("Missing required field: taskToken");
            }

            // Extract submission details
            String submissionId = (String) event.get("submissionId");
            Integer riskScore = (Integer) event.get("riskScore");
            String organizationName = (String) event.get("organizationName");
            String reviewerEmail = (String) event.getOrDefault("reviewerEmail", "reviewer@cms.gov");
            String primaryContact = (String) event.getOrDefault("primaryContact", "Unknown");

            // Extract issues list (AI found these)
            String issuesList = "None";
            if (event.containsKey("issues") && event.get("issues") != null) {
                java.util.List<String> issues = (java.util.List<String>) event.get("issues");
                if (!issues.isEmpty()) {
                    issuesList = String.join("\n• ", issues);
                    issuesList = "• " + issuesList;
                }
            }

            context.getLogger().log("Sending review request for submission: " + submissionId);
            context.getLogger().log("Task token (first 20 chars): " + taskToken.substring(0, Math.min(20, taskToken.length())) + "...");

            // Store task token in DynamoDB for lookup later (optional but helpful)
            storeTaskToken(submissionId, taskToken, reviewerEmail);

            // Build action URLs with embedded task token
            String approveUrl = buildActionUrl("approve", taskToken, submissionId, reviewerEmail);
            String rejectUrl = buildActionUrl("reject", taskToken, submissionId, reviewerEmail);
            String moreInfoUrl = buildActionUrl("more-info", taskToken, submissionId, reviewerEmail);

            // Build email message
            String emailSubject = String.format("🔍 URGENT: Healthcare Submission Review Required - %s", submissionId);
            String emailBody = buildEmailBody(
                submissionId, organizationName, primaryContact, riskScore,
                issuesList, reviewerEmail, approveUrl, rejectUrl, moreInfoUrl
            );

            // Send email notification directly to specific reviewer via SES
            SendEmailResponse sesResponse = sesClient.sendEmail(
                SendEmailRequest.builder()
                    .source(SENDER_EMAIL)
                    .destination(Destination.builder()
                        .toAddresses(reviewerEmail)
                        .build())
                    .message(Message.builder()
                        .subject(Content.builder().data(emailSubject).build())
                        .body(Body.builder()
                            .text(Content.builder().data(emailBody).build())
                            .html(Content.builder().data(buildHtmlEmailBody(
                                submissionId, organizationName, primaryContact, riskScore,
                                issuesList, reviewerEmail, approveUrl, rejectUrl, moreInfoUrl
                            )).build())
                            .build())
                        .build())
                    .build()
            );

            String messageId = sesResponse.messageId();
            context.getLogger().log("✅ Review request sent via SES to: " + reviewerEmail + ". MessageId: " + messageId);
            context.getLogger().log("⏸️ Workflow is now PAUSED waiting for human decision...");

            // Return status info (just for logging, workflow stays paused)
            Map<String, Object> result = new HashMap<>();
            result.put("status", "WAITING_FOR_HUMAN_DECISION");
            result.put("messageId", messageId);
            result.put("reviewerEmail", reviewerEmail);
            result.put("submissionId", submissionId);
            result.put("timestamp", Instant.now().toString());

            return result;

        } catch (Exception e) {
            context.getLogger().log("❌ Error sending review request: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to send review request: " + e.getMessage(), e);
        }
    }

    /**
     * Store task token in DynamoDB for later lookup
     */
    private void storeTaskToken(String submissionId, String taskToken, String reviewerEmail) {
        try {
            dynamoDbClient.putItem(
                PutItemRequest.builder()
                    .tableName("HealthcareSubmissions")
                    .item(Map.of(
                        "submissionId", AttributeValue.builder().s(submissionId).build(),
                        "taskToken", AttributeValue.builder().s(taskToken).build(),
                        "reviewerEmail", AttributeValue.builder().s(reviewerEmail).build(),
                        "status", AttributeValue.builder().s("PENDING_REVIEW").build(),
                        "timestamp", AttributeValue.builder().s(Instant.now().toString()).build()
                    ))
                    .build()
            );
        } catch (Exception e) {
            System.err.println("Warning: Could not store task token in DynamoDB: " + e.getMessage());
            // Don't fail the whole function if DynamoDB write fails
        }
    }

    /**
     * Build action URL with embedded task token
     */
    private String buildActionUrl(String action, String taskToken, String submissionId, String reviewerEmail) {
        try {
            String encodedToken = URLEncoder.encode(taskToken, StandardCharsets.UTF_8.toString());
            String encodedSubmissionId = URLEncoder.encode(submissionId, StandardCharsets.UTF_8.toString());
            String encodedReviewerId = URLEncoder.encode(reviewerEmail, StandardCharsets.UTF_8.toString());

            // Map action to decision value
            String decision = action.equals("approve") ? "APPROVED" :
                            action.equals("reject") ? "REJECTED" :
                            "REQUEST_MORE_INFO";

            return String.format(
                "%s?token=%s&decision=%s&submissionId=%s&reviewerId=%s",
                LAMBDA_FUNCTION_URL, encodedToken, decision, encodedSubmissionId, encodedReviewerId
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to build action URL: " + e.getMessage(), e);
        }
    }

    /**
     * Build formatted email body
     */
    private String buildEmailBody(String submissionId, String organizationName, String primaryContact,
                                  int riskScore, String issuesList, String reviewerEmail,
                                  String approveUrl, String rejectUrl, String moreInfoUrl) {

        String riskLevel = riskScore < 40 ? "MEDIUM-LOW" : riskScore < 60 ? "MEDIUM" : "MEDIUM-HIGH";

        return String.format(
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "   HEALTHCARE SUBMISSION REQUIRES HUMAN REVIEW\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
            "A healthcare plan submission has been flagged by our AI validation\n" +
            "system and requires human review before processing can continue.\n\n" +
            "📋 SUBMISSION DETAILS\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "Submission ID:     %s\n" +
            "Organization:      %s\n" +
            "Primary Contact:   %s\n" +
            "Risk Score:        %d/100 (%s)\n" +
            "Assigned Reviewer: %s\n\n" +
            "⚠️ ISSUES IDENTIFIED BY AI\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "%s\n\n" +
            "⚡ ACTION REQUIRED\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "Please review the submission and click ONE of the links below:\n\n" +
            "✅ APPROVE SUBMISSION\n" +
            "   %s\n\n" +
            "❌ REJECT SUBMISSION\n" +
            "   %s\n\n" +
            "📝 REQUEST MORE INFORMATION\n" +
            "   %s\n\n" +
            "⏱️ IMPORTANT: Please respond within 24 hours.\n" +
            "   After 24 hours with no response, this submission will be\n" +
            "   automatically escalated to senior management.\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "This is an automated message from the Healthcare Plan Validation System.\n" +
            "The workflow is currently PAUSED awaiting your decision.\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n",
            submissionId, organizationName, primaryContact, riskScore, riskLevel,
            reviewerEmail, issuesList, approveUrl, rejectUrl, moreInfoUrl
        );
    }

    private String buildHtmlEmailBody(String submissionId, String organizationName, String primaryContact,
                                      int riskScore, String issuesList, String reviewerEmail,
                                      String approveUrl, String rejectUrl, String moreInfoUrl) {

        String riskLevel = riskScore < 40 ? "MEDIUM-LOW" : riskScore < 60 ? "MEDIUM" : "MEDIUM-HIGH";
        String issuesHtml = issuesList.equals("None") ? "<li>None</li>"
            : "<li>" + issuesList.replace("• ", "").replace("\n• ", "</li><li>") + "</li>";

        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family:Arial,sans-serif;max-width:600px;margin:auto;color:#333">
              <h2 style="background:#1a3c5e;color:#fff;padding:12px;border-radius:4px">
                Healthcare Submission Requires Human Review
              </h2>
              <p>A healthcare plan submission has been flagged by our AI validation system
                 and requires human review before processing can continue.</p>

              <h3>Submission Details</h3>
              <table style="border-collapse:collapse;width:100%%">
                <tr><td style="padding:6px;font-weight:bold">Submission ID</td><td>%s</td></tr>
                <tr><td style="padding:6px;font-weight:bold">Organization</td><td>%s</td></tr>
                <tr><td style="padding:6px;font-weight:bold">Primary Contact</td><td>%s</td></tr>
                <tr><td style="padding:6px;font-weight:bold">Risk Score</td><td>%d/100 (%s)</td></tr>
                <tr><td style="padding:6px;font-weight:bold">Assigned Reviewer</td><td>%s</td></tr>
              </table>

              <h3>Issues Identified by AI</h3>
              <ul>%s</ul>

              <p>
                <a href="https://submissions.example.com/view/%s" style="color:#1a3c5e;font-weight:bold">
                  &#128196; View Full Submission
                </a>
              </p>

              <h3>Action Required</h3>
              <p>Please review the submission and click <strong>one</strong> of the buttons below:</p>
              <p>
                <a href="%s" style="background:#28a745;color:#fff;padding:10px 20px;border-radius:4px;text-decoration:none;margin-right:8px">
                  &#10003; Approve Submission
                </a>
                <a href="%s" style="background:#dc3545;color:#fff;padding:10px 20px;border-radius:4px;text-decoration:none;margin-right:8px">
                  &#10007; Reject Submission
                </a>
                <a href="%s" style="background:#fd7e14;color:#fff;padding:10px 20px;border-radius:4px;text-decoration:none">
                  &#128221; Request More Info
                </a>
              </p>
              <p style="color:#666;font-size:0.9em">
                Please respond within 24 hours. After 24 hours with no response, this submission
                will be automatically escalated to senior management.
              </p>
              <hr/>
              <p style="color:#999;font-size:0.8em">
                This is an automated message from the Healthcare Plan Validation System.
                The workflow is currently PAUSED awaiting your decision.
              </p>
            </body>
            </html>
            """,
            submissionId, organizationName, primaryContact, riskScore, riskLevel,
            reviewerEmail, issuesHtml, submissionId, approveUrl, rejectUrl, moreInfoUrl
        );
    }
}
