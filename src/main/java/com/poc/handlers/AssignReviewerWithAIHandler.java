package com.poc.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AssignReviewerWithAIHandler
 *
 * This Lambda combines database lookup with AI intelligence:
 * 1. Queries DynamoDB for available reviewers at the recommended level
 * 2. Passes reviewer data to Claude AI
 * 3. AI selects the best reviewer based on workload, expertise, and context
 *
 * This is the BEST approach: Real-world data + AI intelligence
 */
public class AssignReviewerWithAIHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamoDbClient;
    private final BedrockRuntimeClient bedrockClient;
    private final S3Client s3Client;
    private final Gson gson;
    private final ObjectMapper objectMapper;

    private static final String PROMPT_BUCKET = System.getenv().getOrDefault("PROMPT_BUCKET", "poc-stpfunctions-bucket");
    private static final String PROMPT_KEY = System.getenv().getOrDefault("PROMPT_KEY", "prompts/reviewer-selection-prompt.txt");
    private static final String RULES_KEY = System.getenv().getOrDefault("RULES_KEY", "business-rules/mandatory-rules.txt");
    private static final String MODEL_ID = "arn:aws:bedrock:us-east-2:529544622571:inference-profile/global.anthropic.claude-opus-4-5-20251101-v1:0";
    private static final int MAX_TOKENS = 4000;

    public AssignReviewerWithAIHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().build();
        this.bedrockClient = BedrockRuntimeClient.builder().build();
        this.s3Client = S3Client.builder().build();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        context.getLogger().log("AssignReviewerWithAI invoked: " + gson.toJson(event));

        try {
            // Extract validation results from previous step
            // ValidateWithAIHandler puts analysis directly under the "validation" key
            Map<String, Object> analysis = (Map<String, Object>) event.get("validation");

            String submissionId = (String) analysis.get("submissionId");
            int riskScore = ((Number) analysis.get("riskScore")).intValue();
            List<String> issues = (List<String>) analysis.get("issues");
            String organizationName = (String) analysis.get("organizationName");

            context.getLogger().log("Fetching ALL available reviewers from database...");
            context.getLogger().log("AI will decide the appropriate reviewer level based on risk score: " + riskScore);

            // Get ALL available reviewers - let AI decide the appropriate level
            List<Map<String, Object>> availableReviewers = getAllAvailableReviewers();

            if (availableReviewers.isEmpty()) {
                throw new RuntimeException("No reviewers available in the system!");
            }

            context.getLogger().log("Found " + availableReviewers.size() + " available reviewers across all levels");

            // Ask AI to select the best reviewer
            Map<String, Object> aiSelection = askAIToSelectReviewer(
                submissionId, organizationName, riskScore, issues, availableReviewers, context
            );

            context.getLogger().log("✅ AI selected reviewer: " + aiSelection.get("reviewerName") +
                                   " (" + aiSelection.get("reviewerEmail") + ")");
            context.getLogger().log("Reason: " + aiSelection.get("selectionReason"));

            // Update reviewer workload in DB
            incrementReviewerWorkload((String) aiSelection.get("reviewerId"));

            // Create assignment record
            String assignmentId = createAssignmentRecord(submissionId, aiSelection, riskScore);

            // Build response
            Map<String, Object> result = new HashMap<>(event);
            result.put("reviewer", Map.of(
                "reviewerId", aiSelection.get("reviewerId"),
                "reviewerEmail", aiSelection.get("reviewerEmail"),
                "reviewerName", aiSelection.get("reviewerName"),
                "reviewerLevel", aiSelection.get("reviewerLevel"),
                "selectionReason", aiSelection.get("selectionReason"),
                "assignmentId", assignmentId
            ));

            return result;

        } catch (Exception e) {
            context.getLogger().log("❌ Error assigning reviewer: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to assign reviewer with AI: " + e.getMessage(), e);
        }
    }




    /**
     * Get all available reviewers regardless of level
     */
    private List<Map<String, Object>> getAllAvailableReviewers() {
        try {
            ScanResponse response = dynamoDbClient.scan(
                ScanRequest.builder()
                    .tableName("HealthcareReviewers")
                    .filterExpression("isAvailable = :available AND currentWorkload < maxConcurrentReviews")
                    .expressionAttributeValues(Map.of(
                        ":available", AttributeValue.builder().bool(true).build()
                    ))
                    .build()
            );

            return response.items().stream()
                .map(this::itemToMap)
                .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Ask Claude AI to select the best reviewer from available options
     */
    private Map<String, Object> askAIToSelectReviewer(String submissionId, String organizationName,
                                                       int riskScore, List<String> issues,
                                                       List<Map<String, Object>> reviewers,
                                                       Context context) {
        try {
            // Build prompt for AI
            String prompt = buildReviewerSelectionPrompt(
                submissionId, organizationName, riskScore, issues, reviewers
            );

            context.getLogger().log("Asking Claude AI to select best reviewer...");

            // Call Bedrock
            Map<String, Object> requestBody = Map.of(
                "anthropic_version", "bedrock-2023-05-31",
                "max_tokens", 1000,
                "temperature", 0.3,
                "messages", List.of(
                    Map.of("role", "user", "content", prompt)
                )
            );

            InvokeModelResponse response = bedrockClient.invokeModel(
                InvokeModelRequest.builder()
                    .modelId(MODEL_ID)
                    .body(SdkBytes.fromUtf8String(gson.toJson(requestBody)))
                    .build()
            );

            // Parse response
            String responseBody = response.body().asUtf8String();
            Map<String, Object> responseJson = gson.fromJson(responseBody, Map.class);
            List<Map<String, Object>> content = (List<Map<String, Object>>) responseJson.get("content");
            String aiResponseText = (String) content.get(0).get("text");

            context.getLogger().log("AI Response: " + aiResponseText);

            return parseAISelectionResponse(aiResponseText, context);

        } catch (Exception e) {
            throw new RuntimeException("Failed to get AI reviewer selection: " + e.getMessage(), e);
        }
    }

    /**
     * Load prompt template from S3
     */
    private String loadPromptFromS3() {
        try {
            return s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                    .bucket(PROMPT_BUCKET)
                    .key(PROMPT_KEY)
                    .build()
            ).asUtf8String();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load prompt from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Build prompt asking AI to select best reviewer
     */
    private String buildReviewerSelectionPrompt(String submissionId, String organizationName,
                                               int riskScore, List<String> issues,
                                               List<Map<String, Object>> reviewers) {
        // Load base prompt template from S3
        String promptTemplate = loadPromptFromS3();

        // Build submission details
        StringBuilder submissionDetails = new StringBuilder();
        submissionDetails.append("- Submission ID: ").append(submissionId).append("\n");
        submissionDetails.append("- Organization: ").append(organizationName).append("\n");
        submissionDetails.append("- Risk Score: ").append(riskScore).append("/100\n");
        submissionDetails.append("- Issues Found: ").append(issues.isEmpty() ? "None" : String.join(", ", issues)).append("\n");

        // Build reviewers list
        StringBuilder reviewersList = new StringBuilder();
        for (int i = 0; i < reviewers.size(); i++) {
            Map<String, Object> reviewer = reviewers.get(i);
            reviewersList.append((i + 1)).append(". ").append(reviewer.get("name")).append("\n");
            reviewersList.append("   - Email: ").append(reviewer.get("email")).append("\n");
            reviewersList.append("   - Level: ").append(reviewer.get("reviewerLevel")).append("\n");
            reviewersList.append("   - Current Workload: ").append(reviewer.get("currentWorkload"))
                  .append("/").append(reviewer.get("maxConcurrentReviews")).append("\n");
            reviewersList.append("   - Expertise: ").append(reviewer.get("expertise")).append("\n");
            reviewersList.append("   - Average Review Time: ").append(reviewer.get("avgReviewTimeMinutes")).append(" minutes\n");
            reviewersList.append("   - Approval Rate: ").append(reviewer.get("approvalRate")).append("\n\n");
        }

        // Replace placeholders in template
        return promptTemplate
            .replace("{{SUBMISSION_DETAILS}}", submissionDetails.toString())
            .replace("{{AVAILABLE_REVIEWERS}}", reviewersList.toString());
    }

    /**
     * Increment reviewer's current workload in DB
     */
    private void incrementReviewerWorkload(String reviewerId) {
        try {
            dynamoDbClient.updateItem(
                UpdateItemRequest.builder()
                    .tableName("HealthcareReviewers")
                    .key(Map.of("reviewerId", AttributeValue.builder().s(reviewerId).build()))
                    .updateExpression("SET currentWorkload = if_not_exists(currentWorkload, :zero) + :inc, updatedAt = :now")
                    .expressionAttributeValues(Map.of(
                        ":inc", AttributeValue.builder().n("1").build(),
                        ":zero", AttributeValue.builder().n("0").build(),
                        ":now", AttributeValue.builder().s(Instant.now().toString()).build()
                    ))
                    .build()
            );
        } catch (Exception e) {
            System.err.println("Warning: Could not update reviewer workload: " + e.getMessage());
        }
    }

    /**
     * Create assignment record in DynamoDB
     */
    private String createAssignmentRecord(String submissionId, Map<String, Object> reviewer, int riskScore) {
        String assignmentId = "ASSIGN-" + Instant.now().toString().replaceAll("[:\\-.]", "");

        try {
            dynamoDbClient.putItem(
                PutItemRequest.builder()
                    .tableName("ReviewerAssignments")
                    .item(Map.of(
                        "assignmentId", AttributeValue.builder().s(assignmentId).build(),
                        "submissionId", AttributeValue.builder().s(submissionId).build(),
                        "reviewerId", AttributeValue.builder().s((String) reviewer.get("reviewerId")).build(),
                        "reviewerEmail", AttributeValue.builder().s((String) reviewer.get("reviewerEmail")).build(),
                        "riskScore", AttributeValue.builder().n(String.valueOf(riskScore)).build(),
                        "assignedAt", AttributeValue.builder().s(Instant.now().toString()).build(),
                        "assignmentReason", AttributeValue.builder().s((String) reviewer.get("selectionReason")).build(),
                        "status", AttributeValue.builder().s("PENDING").build(),
                        "dueBy", AttributeValue.builder().s(Instant.now().plusSeconds(86400).toString()).build()
                    ))
                    .build()
            );
        } catch (Exception e) {
            System.err.println("Warning: Could not create assignment record: " + e.getMessage());
        }

        return assignmentId;
    }

    /**
     * Parse AI reviewer selection response with multi-strategy JSON cleaning and Jackson parsing
     */
    private Map<String, Object> parseAISelectionResponse(String aiResponseText, Context context) {
        String cleanJson = null;
        try {
            cleanJson = aiResponseText.trim();

            // Strategy 1: Remove markdown code blocks
            if (cleanJson.contains("```")) {
                cleanJson = cleanJson.replaceAll("^```(?:json)?\\s*\\n?", "");
                cleanJson = cleanJson.replaceAll("\\n?```\\s*$", "");
                cleanJson = cleanJson.trim();
            }

            // Strategy 2: Find JSON object boundaries
            int jsonStart = cleanJson.indexOf('{');
            int jsonEnd = -1;
            if (jsonStart != -1) {
                int braceCount = 0;
                for (int i = jsonStart; i < cleanJson.length(); i++) {
                    char c = cleanJson.charAt(i);
                    if (c == '{') braceCount++;
                    else if (c == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            jsonEnd = i;
                            break;
                        }
                    }
                }
            }

            if (jsonStart == -1 || jsonEnd == -1) {
                throw new RuntimeException("No JSON object found in AI reviewer selection response");
            }

            cleanJson = cleanJson.substring(jsonStart, jsonEnd + 1);

            // Strategy 3: Additional cleanup
            cleanJson = cleanJson.replaceAll(",\\s*}", "}");
            cleanJson = cleanJson.replaceAll(",\\s*]", "]");
            cleanJson = cleanJson.replaceAll("//.*?\\n", "\n");
            cleanJson = cleanJson.replaceAll("/\\*.*?\\*/", "");

            context.getLogger().log("=== CLEANED REVIEWER SELECTION JSON ===");
            context.getLogger().log(cleanJson);
            context.getLogger().log("=======================================");

            Map<String, Object> selection;
            try {
                selection = objectMapper.readValue(cleanJson, new TypeReference<Map<String, Object>>(){});
            } catch (Exception parseEx) {
                context.getLogger().log("Jackson parsing failed: " + parseEx.getMessage());
                context.getLogger().log("Attempting manual JSON fixing...");
                String fixedJson = fixCommonJsonIssues(cleanJson, context);
                selection = objectMapper.readValue(fixedJson, new TypeReference<Map<String, Object>>(){});
            }

            // Validate required fields
            if (!selection.containsKey("reviewerId")) {
                throw new RuntimeException("Missing required field: reviewerId");
            }
            if (!selection.containsKey("reviewerEmail")) {
                throw new RuntimeException("Missing required field: reviewerEmail");
            }

            return selection;

        } catch (Exception e) {
            context.getLogger().log("FATAL: Failed to parse AI reviewer selection response");
            context.getLogger().log("Error: " + e.getClass().getName() + ": " + e.getMessage());
            if (cleanJson != null) {
                context.getLogger().log("First 500 chars of cleaned JSON: " +
                    cleanJson.substring(0, Math.min(500, cleanJson.length())));
            }
            e.printStackTrace();
            throw new RuntimeException("Failed to parse AI reviewer selection: " + e.getMessage(), e);
        }
    }

    /**
     * Fix common JSON formatting issues from AI responses
     */
    private String fixCommonJsonIssues(String json, Context context) {
        context.getLogger().log("Applying JSON fixes...");

        // Fix unquoted keys
        json = json.replaceAll("([{,])\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*:", "$1\"$2\":");

        // Fix single quotes to double quotes
        json = json.replace("'", "\"");

        // Remove trailing commas
        json = json.replaceAll(",\\s*([}\\]])", "$1");

        // Fix boolean/null values that might be quoted
        json = json.replaceAll("\"(true|false|null)\"", "$1");

        context.getLogger().log("JSON fixes applied");
        return json;
    }

    /**
     * Convert DynamoDB item to Map
     */
    private Map<String, Object> itemToMap(Map<String, AttributeValue> item) {
        Map<String, Object> map = new HashMap<>();
        map.put("reviewerId", item.get("reviewerId").s());
        map.put("email", item.get("email").s());
        map.put("name", item.get("name").s());
        map.put("reviewerLevel", item.get("reviewerLevel").s());
        map.put("currentWorkload", Integer.parseInt(item.get("currentWorkload").n()));
        map.put("maxConcurrentReviews", Integer.parseInt(item.get("maxConcurrentReviews").n()));
        map.put("avgReviewTimeMinutes", Integer.parseInt(item.get("avgReviewTimeMinutes").n()));
        map.put("approvalRate", Double.parseDouble(item.get("approvalRate").n()));

        if (item.containsKey("expertise") && item.get("expertise").hasSs()) {
            map.put("expertise", item.get("expertise").ss());
        }

        return map;
    }
}
