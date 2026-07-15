package com.poc.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ValidateWithAIHandler
 *
 * This Lambda function:
 * 1. Loads validation prompt from S3
 * 2. Loads business rules from S3
 * 3. Builds full prompt dynamically
 * 4. Calls Amazon Bedrock (Claude 3.5 Sonnet)
 * 5. Parses AI response
 * 6. Returns structured validation result
 *
 * Input:
 * {
 *   "submission": {
 *     "submissionId": "SUB-2024-001234",
 *     "submissionText": "HEALTHCARE PLAN SUBMISSION\n...",
 *     "organizationName": "BlueCross Health Plans",
 *     "s3Bucket": "healthcare-submissions-poc",
 *     "s3Key": "submissions/SUB-2024-001234.txt"
 *   }
 * }
 *
 * Output:
 * {
 *   "submission": { ... },
 *   "validation": {
 *     "riskScore": 58,
 *     "recommendation": "HUMAN_REVIEW",
 *     "reasoning": "Premium $285 below market average...",
 *     "issues": ["Premium too low", "Missing deductible"],
 *     "validationPassed": false,
 *     "submissionId": "SUB-2024-001234",
 *     "organizationName": "Valley Health Insurance Co",
 *     "promptVersion": "v2.1",
 *     "modelUsed": "claude-3-5-sonnet-20241022-v2:0",
 *     "tokensUsed": 1456
 *   }
 * }
 */
public class ValidateWithAIHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final S3Client s3Client;
    private final BedrockRuntimeClient bedrockClient;
    private final DynamoDbClient dynamoDbClient;
    private final Gson gson;
    private final ObjectMapper objectMapper;

    // Configuration - read from environment variables
    private static final String PROMPT_BUCKET = System.getenv().getOrDefault("PROMPT_BUCKET", "poc-stpfunctions-bucket");
    private static final String PROMPT_KEY = System.getenv().getOrDefault("PROMPT_KEY", "prompts/validation-prompt-intelligent.txt");
    private static final String RULES_KEY = System.getenv().getOrDefault("RULES_KEY", "business-rules/mandatory-rules.txt");
    private static final String MODEL_ID = "arn:aws:bedrock:us-east-2:529544622571:inference-profile/global.anthropic.claude-opus-4-5-20251101-v1:0";
    private static final int MAX_TOKENS = 7000;

    public ValidateWithAIHandler() {
        this.s3Client = S3Client.builder().build();
        this.bedrockClient = BedrockRuntimeClient.builder()
            .region(Region.US_EAST_2)  // Ohio region where you have Bedrock quotas
            .build();
        this.dynamoDbClient = DynamoDbClient.builder().build();
        this.gson = new GsonBuilder().setLenient().setPrettyPrinting().create();
        this.objectMapper = new ObjectMapper();
    }

    // Constructor for testing with mock clients
    public ValidateWithAIHandler(S3Client s3Client, BedrockRuntimeClient bedrockClient, DynamoDbClient dynamoDbClient) {
        this.s3Client = s3Client;
        this.bedrockClient = bedrockClient != null ? bedrockClient :
            BedrockRuntimeClient.builder().region(Region.US_EAST_2).build();
        this.dynamoDbClient = dynamoDbClient;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        context.getLogger().log("ValidateWithAI invoked: " + gson.toJson(event));

        try  {
            // Extract submission data (direct format from ReadSubmissionHandler)
            String submissionId = (String) event.get("submissionId");
            String submissionText = (String) event.get("submissionText");

            context.getLogger().log("Processing submission: " + submissionId);

            // Delete any existing reviewer assignment for this submission
            deleteReviewerAssignment(submissionId, context);

            // Step 1: Load prompt template from S3
            long startTime = System.currentTimeMillis();
            String promptTemplate = loadPromptFromS3(context);
            long promptLoadTime = System.currentTimeMillis() - startTime;
            context.getLogger().log("Prompt loaded from S3 in " + promptLoadTime + "ms");

            // Step 2: Load business rules from S3 (always fresh for POC demo)
            startTime = System.currentTimeMillis();
            String businessRules = loadBusinessRulesFromS3(context);
            long rulesLoadTime = System.currentTimeMillis() - startTime;
            context.getLogger().log("Business rules loaded from S3 in " + rulesLoadTime + "ms");

            // Step 3: Build full prompt
            String fullPrompt = buildPrompt(promptTemplate, businessRules, submissionText);
            context.getLogger().log("Full prompt built: " + fullPrompt.length() + " characters");

            // Step 4: Call Bedrock
            context.getLogger().log("Calling Amazon Bedrock (Claude 3.5 Sonnet)...");
            startTime = System.currentTimeMillis();
            Map<String, Object> aiResponse = callBedrock(fullPrompt, context);
            long bedrockTime = System.currentTimeMillis() - startTime;
            context.getLogger().log("Bedrock responded in " + bedrockTime + "ms");

            // Step 5: Parse AI response
            Map<String, Object> analysis = parseAIResponse(aiResponse, context);

            // Step 6: Add metadata
            analysis.put("modelUsed", MODEL_ID);
            analysis.put("processingTimeMs", bedrockTime);

            // Build final response
            Map<String, Object> result = new HashMap<>();
            result.putAll(event); // Keep all original data
            result.put("validation", analysis);

            context.getLogger().log("✅ Validation complete: " +
                analysis.get("recommendation") + " (risk score: " + analysis.get("riskScore") + ")");

            return result;

        } catch (Exception e) {
            context.getLogger().log("❌ Error during validation: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to validate submission: " + e.getMessage(), e);
        }
    }

    /**
     * Load prompt template from S3 (no caching - always fresh for POC demo)
     */
    private String loadPromptFromS3(Context context) {
        context.getLogger().log("Loading prompt template from S3: s3://" + PROMPT_BUCKET + "/" + PROMPT_KEY);
        try {
            // Read content from S3 (always fresh)
            String prompt = new BufferedReader(new InputStreamReader(
                s3Client.getObject(
                    GetObjectRequest.builder()
                        .bucket(PROMPT_BUCKET)
                        .key(PROMPT_KEY)
                        .build()
                )
            )).lines().collect(Collectors.joining("\n"));

            context.getLogger().log("✅ Prompt loaded from S3 (always fresh - no caching)");
            return prompt;

        } catch (Exception e) {
            context.getLogger().log("⚠️ Failed to load prompt from S3: " + e.getMessage());
            context.getLogger().log("Using fallback hardcoded prompt");
            return getFallbackPrompt();
        }
    }

    /**
     * Load business rules from S3 (no caching - always fresh for POC demo)
     */
    private String loadBusinessRulesFromS3(Context context) {
        context.getLogger().log("Loading business rules from S3: s3://" + PROMPT_BUCKET + "/" + RULES_KEY);
        try {
            // Read content from S3 (always fresh)
            String rules = new BufferedReader(new InputStreamReader(
                s3Client.getObject(
                    GetObjectRequest.builder()
                        .bucket(PROMPT_BUCKET)
                        .key(RULES_KEY)
                        .build()
                )
            )).lines().collect(Collectors.joining("\n"));

            context.getLogger().log("✅ Business rules loaded from S3 - always fresh, no caching");

            return rules;

        } catch (Exception e) {
            context.getLogger().log("⚠️ Failed to load rules from S3: " + e.getMessage());
            context.getLogger().log("Using fallback hardcoded rules");
            return getFallbackRules();
        }
    }

    /**
     * Build full prompt by combining template, rules, and submission text
     */
    private String buildPrompt(String template, String rules, String submissionText) {
        // Replace placeholders
        String prompt = template
            .replace("${BUSINESS_RULES}", rules)
            .replace("${SUBMISSION_TEXT}", submissionText);

        return prompt;
    }


    /**
     * Call Amazon Bedrock with the prompt
     */
    private Map<String, Object> callBedrock(String prompt, Context context) {
        try {
            // Build request body for Claude
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
            requestBody.put("max_tokens", MAX_TOKENS);
            requestBody.put("temperature", 0.3);
            requestBody.put("messages", java.util.List.of(
                Map.of("role", "user", "content", prompt)
            ));

            String requestBodyJson = gson.toJson(requestBody);

            // Call Bedrock
            InvokeModelResponse response = bedrockClient.invokeModel(
                InvokeModelRequest.builder()
                    .modelId(MODEL_ID)
                    .body(SdkBytes.fromUtf8String(requestBodyJson))
                    .build()
            );

            // Parse response
            String responseBody = response.body().asUtf8String();
            // Trim whitespace and remove markdown code blocks if present
            String cleanJson = responseBody.trim();
            if (cleanJson.startsWith("```")) {
                // Strips out ```json and closing ```
                cleanJson = cleanJson.replaceAll("^```(?:json)?\\s+|\s+```$", "");
            }

            System.out.println("response Body: " + cleanJson);
            Map<String, Object> responseJson = gson.fromJson(cleanJson, Map.class);

            return responseJson;

        } catch (Exception e) {
            context.getLogger().log("❌ Bedrock API error: " + e.getMessage());
            throw new RuntimeException("Failed to call Bedrock: " + e.getMessage(), e);
        }
    }

    /**
     * Parse AI response and extract validation data
     */
    private Map<String, Object> parseAIResponse(Map<String, Object> bedrockResponse, Context context) {
        String aiResponseText = null;
        String cleanJson = null;

        try {
            // Extract content from Bedrock response structure
            java.util.List<Map<String, Object>> content =
                (java.util.List<Map<String, Object>>) bedrockResponse.get("content");

            aiResponseText = (String) content.get(0).get("text");

            context.getLogger().log("=== RAW AI RESPONSE ===");
            context.getLogger().log(aiResponseText);
            context.getLogger().log("======================");

            // Clean the response - multiple strategies
            cleanJson = aiResponseText.trim();

            // Strategy 1: Remove markdown code blocks
            if (cleanJson.contains("```")) {
                // Remove opening ```json or ```
                cleanJson = cleanJson.replaceAll("^```(?:json)?\\s*\\n?", "");
                // Remove closing ```
                cleanJson = cleanJson.replaceAll("\\n?```\\s*$", "");
                cleanJson = cleanJson.trim();
            }

            // Strategy 2: Find JSON object boundaries (most reliable)
            int jsonStart = cleanJson.indexOf('{');
            int jsonEnd = -1;

            if (jsonStart != -1) {
                // Find matching closing brace
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
                context.getLogger().log("❌ No valid JSON object found in response");
                throw new RuntimeException("No JSON object found in AI response");
            }

            cleanJson = cleanJson.substring(jsonStart, jsonEnd + 1);

            // Strategy 3: Additional cleanup
            // Remove any trailing commas before closing braces/brackets
            cleanJson = cleanJson.replaceAll(",\\s*}", "}");
            cleanJson = cleanJson.replaceAll(",\\s*]", "]");

            // Remove single-line comments (//...)
            cleanJson = cleanJson.replaceAll("//.*?\\n", "\n");

            // Remove multi-line comments (/* ... */)
            cleanJson = cleanJson.replaceAll("/\\*.*?\\*/", "");

            context.getLogger().log("=== CLEANED JSON ===");
            context.getLogger().log(cleanJson);
            context.getLogger().log("===================");

            // Parse with Jackson ObjectMapper (more lenient than Gson)
            Map<String, Object> analysis;
            try {
                analysis = objectMapper.readValue(cleanJson, new TypeReference<Map<String, Object>>(){});
            } catch (Exception parseEx) {
                context.getLogger().log("❌ Jackson parsing failed: " + parseEx.getMessage());
                context.getLogger().log("Attempting manual JSON fixing...");

                // Last resort: try to fix common JSON issues
                String fixedJson = fixCommonJsonIssues(cleanJson, context);
                analysis = objectMapper.readValue(fixedJson, new TypeReference<Map<String, Object>>(){});
            }

            // Validate required fields
            if (!analysis.containsKey("riskScore")) {
                throw new RuntimeException("Missing required field: riskScore");
            }
            if (!analysis.containsKey("recommendation")) {
                throw new RuntimeException("Missing required field: recommendation");
            }

            // Add token usage info
            if (bedrockResponse.containsKey("usage")) {
                Map<String, Object> usage = (Map<String, Object>) bedrockResponse.get("usage");
                analysis.put("tokensUsed",
                    ((Number) usage.get("input_tokens")).intValue() +
                    ((Number) usage.get("output_tokens")).intValue()
                );
            }

            return analysis;

        } catch (Exception e) {
            context.getLogger().log("❌ FATAL: Failed to parse AI response");
            context.getLogger().log("Error: " + e.getClass().getName() + ": " + e.getMessage());

            if (aiResponseText != null) {
                context.getLogger().log("Original response length: " + aiResponseText.length());
            }
            if (cleanJson != null) {
                context.getLogger().log("Cleaned JSON length: " + cleanJson.length());
                context.getLogger().log("First 500 chars of cleaned JSON: " +
                    cleanJson.substring(0, Math.min(500, cleanJson.length())));
            }

            e.printStackTrace();
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage(), e);
        }
    }

    /**
     * Fix common JSON formatting issues
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
     * Fallback prompt if S3 is unavailable
     */
    private String getFallbackPrompt() {
        return "You are a healthcare plan submission validator for the Centers for Medicare & Medicaid Services (CMS).\n\n" +
               "BUSINESS RULES:\n" +
               "${BUSINESS_RULES}\n\n" +
               "RISK SCORING (0-100):\n" +
               "- 0-30: LOW RISK → AUTO_APPROVE\n" +
               "- 31-70: MEDIUM RISK → HUMAN_REVIEW\n" +
               "- 71-100: HIGH RISK → AUTO_REJECT\n\n" +
               "ANALYZE THIS SUBMISSION:\n\n" +
               "${SUBMISSION_TEXT}\n\n" +
               "Provide your response in this EXACT JSON format:\n" +
               "{\n" +
               "  \"riskScore\": <number 0-100>,\n" +
               "  \"recommendation\": \"<AUTO_APPROVE|HUMAN_REVIEW|AUTO_REJECT>\",\n" +
               "  \"reasoning\": \"<brief explanation>\",\n" +
               "  \"issues\": [\"<list of issues or empty array>\"],\n" +
               "  \"validationPassed\": <true|false>,\n" +
               "  \"submissionId\": \"<extract from text>\",\n" +
               "  \"organizationName\": \"<extract from text>\",\n" +
               "  \"primaryContact\": \"<extract from text>\",\n" +
               "  \"estimatedReviewTime\": \"<5min|30min|2hours>\"\n" +
               "}";
    }

    /**
     * Fallback rules if S3 is unavailable
     */
    private String getFallbackRules() {
        return "1. Organization ID must follow format: ORG-HC-#####\n" +
               "2. Premium range: $100-$2000/month\n" +
               "3. Deductible range: $500-$10000/year\n" +
               "4. Risk thresholds: Auto-approve < 30, Human review < 70";
    }

    /**
     * Delete reviewer assignment for reprocessed submissions
     */
    private void deleteReviewerAssignment(String submissionId, Context context) {
        try {
            context.getLogger().log("Deleting reviewer assignment for submission: " + submissionId);

            dynamoDbClient.deleteItem(
                DeleteItemRequest.builder()
                    .tableName("ReviewerAssignments")
                    .key(Map.of("submissionId", AttributeValue.builder().s(submissionId).build()))
                    .build()
            );

            context.getLogger().log("✅ Reviewer assignment deleted (if existed)");
        } catch (Exception e) {
            context.getLogger().log("⚠️ Failed to delete reviewer assignment: " + e.getMessage());
            // Don't throw - continue with validation even if delete fails
        }
    }
}