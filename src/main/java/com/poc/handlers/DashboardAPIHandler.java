package com.poc.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DashboardAPIHandler
 *
 * Provides REST API endpoint for the real-time dashboard.
 * Returns aggregated statistics and recent submissions.
 *
 * API Gateway Integration:
 * GET /dashboard
 *
 * Response:
 * {
 *   "totalCount": 25,
 *   "approvedCount": 18,
 *   "pendingCount": 5,
 *   "rejectedCount": 2,
 *   "recentSubmissions": [...],
 *   "reviewers": [...],
 *   "currentlyProcessing": null
 * }
 */
public class DashboardAPIHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamoDbClient;
    private final Gson gson;

    private static final String SUBMISSIONS_TABLE = "HealthcareSubmissions";
    private static final String REVIEWERS_TABLE = "HealthcareReviewers";

    public DashboardAPIHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().build();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        context.getLogger().log("Dashboard API invoked");

        try {
            // Query recent submissions from DynamoDB
            List<Map<String, Object>> recentSubmissions = getRecentSubmissions(context);

            // Get reviewer workload
            List<Map<String, Object>> reviewers = getReviewerWorkload(context);

            // Calculate statistics
            Map<String, Object> stats = calculateStatistics(recentSubmissions);

            // Build response
            Map<String, Object> dashboardData = new HashMap<>();
            dashboardData.put("totalCount", stats.get("total"));
            dashboardData.put("approvedCount", stats.get("approved"));
            dashboardData.put("pendingCount", stats.get("pending"));
            dashboardData.put("rejectedCount", stats.get("rejected"));
            dashboardData.put("recentSubmissions", recentSubmissions);
            dashboardData.put("reviewers", reviewers);
            dashboardData.put("currentlyProcessing", null); // Could be enhanced to show in-flight executions

            // Return API Gateway response format
            Map<String, Object> response = new HashMap<>();
            response.put("statusCode", 200);
            response.put("headers", Map.of(
                "Content-Type", "application/json",
                "Access-Control-Allow-Origin", "*" // Enable CORS
            ));
            response.put("body", gson.toJson(dashboardData));

            return response;

        } catch (Exception e) {
            context.getLogger().log("❌ Error in dashboard API: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("statusCode", 500);
            errorResponse.put("headers", Map.of("Content-Type", "application/json"));
            errorResponse.put("body", gson.toJson(Map.of("error", e.getMessage())));

            return errorResponse;
        }
    }

    /**
     * Get recent submissions from DynamoDB
     */
    private List<Map<String, Object>> getRecentSubmissions(Context context) {
        try {
            // Scan submissions table (in production, use query with GSI on timestamp)
            ScanResponse response = dynamoDbClient.scan(
                ScanRequest.builder()
                    .tableName(SUBMISSIONS_TABLE)
                    .limit(20) // Get last 20 submissions
                    .build()
            );

            return response.items().stream()
                .map(this::convertSubmissionItem)
                .sorted((a, b) -> {
                    String timeA = (String) a.get("timestamp");
                    String timeB = (String) b.get("timestamp");
                    return timeB.compareTo(timeA); // Descending order
                })
                .limit(10)
                .collect(Collectors.toList());

        } catch (Exception e) {
            context.getLogger().log("⚠️ Error fetching submissions: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get reviewer workload from DynamoDB
     */
    private List<Map<String, Object>> getReviewerWorkload(Context context) {
        try {
            ScanResponse response = dynamoDbClient.scan(
                ScanRequest.builder()
                    .tableName(REVIEWERS_TABLE)
                    .filterExpression("isAvailable = :available")
                    .expressionAttributeValues(Map.of(
                        ":available", AttributeValue.builder().bool(true).build()
                    ))
                    .build()
            );

            return response.items().stream()
                .map(this::convertReviewerItem)
                .sorted((a, b) -> {
                    Integer workloadA = (Integer) a.get("workload");
                    Integer workloadB = (Integer) b.get("workload");
                    return workloadB.compareTo(workloadA); // Descending by workload
                })
                .collect(Collectors.toList());

        } catch (Exception e) {
            context.getLogger().log("⚠️ Error fetching reviewers: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Calculate statistics from submissions
     */
    private Map<String, Object> calculateStatistics(List<Map<String, Object>> submissions) {
        Map<String, Object> stats = new HashMap<>();

        int total = submissions.size();
        int approved = 0;
        int pending = 0;
        int rejected = 0;

        for (Map<String, Object> sub : submissions) {
            String status = (String) sub.get("status");
            if ("APPROVED".equals(status)) {
                approved++;
            } else if ("REJECTED".equals(status)) {
                rejected++;
            } else if ("PENDING_REVIEW".equals(status) || "PENDING".equals(status)) {
                pending++;
            }
        }

        stats.put("total", total);
        stats.put("approved", approved);
        stats.put("pending", pending);
        stats.put("rejected", rejected);

        return stats;
    }

    /**
     * Convert DynamoDB submission item to Map
     */
    private Map<String, Object> convertSubmissionItem(Map<String, AttributeValue> item) {
        Map<String, Object> submission = new HashMap<>();

        submission.put("submissionId", getStringValue(item, "submissionId"));
        submission.put("organizationName", getStringValue(item, "organizationName"));
        submission.put("status", getStringValue(item, "status"));
        submission.put("riskScore", getNumberValue(item, "riskScore"));
        submission.put("decision", getStringValue(item, "decision"));
        submission.put("timestamp", getStringValue(item, "processedAt", "submittedAt"));
        submission.put("reviewer", getStringValue(item, "reviewerName"));
        submission.put("processingTime", getStringValue(item, "processingTimeSeconds") + "s");

        return submission;
    }

    /**
     * Convert DynamoDB reviewer item to Map
     */
    private Map<String, Object> convertReviewerItem(Map<String, AttributeValue> item) {
        Map<String, Object> reviewer = new HashMap<>();

        reviewer.put("name", getStringValue(item, "name"));
        reviewer.put("level", getStringValue(item, "reviewerLevel"));
        reviewer.put("workload", getNumberValue(item, "currentWorkload"));
        reviewer.put("maxWorkload", getNumberValue(item, "maxConcurrentReviews"));

        return reviewer;
    }

    /**
     * Helper to get string value from DynamoDB item
     */
    private String getStringValue(Map<String, AttributeValue> item, String... keys) {
        for (String key : keys) {
            if (item.containsKey(key) && item.get(key).s() != null) {
                return item.get(key).s();
            }
        }
        return "";
    }

    /**
     * Helper to get number value from DynamoDB item
     */
    private int getNumberValue(Map<String, AttributeValue> item, String key) {
        if (item.containsKey(key) && item.get(key).n() != null) {
            try {
                return Integer.parseInt(item.get(key).n());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
