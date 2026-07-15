# Sample Healthcare Submission Data Files

This directory contains sample healthcare plan submission files for testing the Step Functions POC workflow.

## Files Overview

### 1. `submission-good.txt`
**Expected Result:** ✅ AUTO-APPROVED (Low Risk)

**Characteristics:**
- Complete and well-formatted submission
- All required fields present
- Valid organization ID format (ORG-HC-XXXXX)
- Valid email addresses
- Premiums in normal range ($450-$650)
- Deductibles in normal range ($1500-$2500)
- Clean compliance history (2 previous approvals, no issues)
- Professional attestation with complete information

**AI Risk Score:** 15-25/100 (Low Risk)

**Workflow Path:**
```
ReadSubmission → ValidateWithAI → StoreResults → RouteSubmission → AutoApprove → NotifyAutoApproval
```

**SNS Notifications:**
- Submission Received
- AI Validation Starting
- AI Validation Complete (Low Risk)
- Auto-Approved
- Workflow Complete

---

### 2. `submission-medium-risk.txt`
**Expected Result:** 👤 HUMAN REVIEW REQUIRED (Medium Risk)

**Characteristics:**
- Mostly complete submission
- Low premium ($285) - below market average
- High deductible ($5500) - concerning for consumers
- Missing deductible for Plan 2
- Mixed compliance history (3 approved, 2 pending)
- Outstanding issues with state regulator
- Limited network size (5000 providers)

**Issues Flagged:**
- Premium amount $285 is suspiciously low
- Deductible $5500 is very high (potential barrier to care)
- Plan 2 missing critical deductible information
- Pending documentation from state regulator
- Small provider network may limit access

**AI Risk Score:** 55-70/100 (Medium Risk)

**Workflow Path:**
```
ReadSubmission → ValidateWithAI → StoreResults → RouteSubmission → HumanReviewRequired →
AssignToReviewer → WaitForHumanDecision ⏸️ (PAUSES)
```

**SNS Notifications:**
- Submission Received
- AI Validation Starting
- AI Validation Complete (Medium Risk)
- Human Review Required
- Assigned to Reviewer (with action links)

---

### 3. `submission-high-risk.txt`
**Expected Result:** ❌ AUTO-REJECTED (High Risk)

**Characteristics:**
- Multiple critical issues
- Invalid organization ID format (ORG-12345 instead of ORG-HC-XXXXX)
- Invalid email format (contact@quickcare - no TLD)
- Unrealistic Plan 1: $99/month premium (too low to be viable)
- Unrealistic Plan 2: $1850/month with $0 copays for everything (too good to be true)
- Poor compliance history (8 rejected, pending state investigation)
- Missing state license number
- Incomplete attestation (no proper signature)
- Claims "all states" coverage area (not credible)

**Critical Issues:**
- Organization ID format violation
- Email format violation
- Extreme premium outliers ($99 and $1850)
- Multiple compliance violations noted
- State investigation pending
- Missing required documentation

**AI Risk Score:** 85-95/100 (High Risk)

**Workflow Path:**
```
ReadSubmission → ValidateWithAI → StoreResults → RouteSubmission → AutoReject → NotifyAutoRejection
```

**SNS Notifications:**
- Submission Received
- AI Validation Starting
- AI Validation Complete (High Risk)
- Auto-Rejected
- Workflow Complete

---

### 4. `submission-incomplete.txt`
**Expected Result:** 👤 HUMAN REVIEW REQUIRED (Medium Risk)

**Characteristics:**
- Partial submission with acknowledgment
- Plan 1 complete except prescription drug details
- Plan 2 completely missing
- Organization explicitly notes this is partial
- Otherwise professional formatting
- Good compliance history (1 previous approval)
- Promises completion by specific date

**Issues Flagged:**
- Plan 2 completely empty (all fields blank)
- Missing prescription drug coverage details for Plan 1
- Acknowledged as partial submission
- Missing NAIC Company Code

**AI Risk Score:** 60-65/100 (Medium Risk)

**Workflow Path:**
```
ReadSubmission → ValidateWithAI → StoreResults → RouteSubmission → HumanReviewRequired →
AssignToReviewer → WaitForHumanDecision ⏸️ (PAUSES)
```

**Use Case:** Tests the "REQUEST MORE INFORMATION" workflow path when reviewer clicks that option.

---

### 5. `submission-invalid-format.txt`
**Expected Result:** 👤 HUMAN REVIEW REQUIRED or AUTO-REJECTED (Medium-High Risk)

**Characteristics:**
- Completely wrong format (casual email style)
- Missing required structure
- Incomplete information
- No formal attestation
- Casual language ("Hey there!", "Let me know")
- Missing many required fields (copay details, out-of-pocket max, NAIC code, etc.)
- No submission ID provided

**Issues Flagged:**
- Does not follow required submission format
- Missing critical fields (deductibles for Silver/Gold, out-of-pocket maximums)
- No formal attestation signature
- Incomplete plan details
- Unprofessional communication style

**AI Risk Score:** 70-80/100 (Medium-High Risk)

**Workflow Path:**
```
ReadSubmission → ValidateWithAI → StoreResults → RouteSubmission → HumanReviewRequired →
AssignToReviewer
```

**Use Case:** Tests AI's ability to handle unexpected formats and flag format violations.

---

## Testing Scenarios

### Scenario 1: Happy Path (Auto-Approval)
```bash
# Upload submission-good.txt to S3
aws s3 cp submission-good.txt s3://your-bucket/submissions/SUB-2024-001234.txt

# Start execution
aws stepfunctions start-execution \
  --state-machine-arn arn:aws:states:us-east-1:ACCOUNT:stateMachine:HealthcareValidationWorkflow \
  --input '{"s3Bucket":"your-bucket","s3Key":"submissions/SUB-2024-001234.txt"}'

# Expected: Completes in 5-10 seconds with AUTO-APPROVED status
```

### Scenario 2: Human Review Path (Medium Risk)
```bash
# Upload submission-medium-risk.txt to S3
aws s3 cp submission-medium-risk.txt s3://your-bucket/submissions/SUB-2024-002456.txt

# Start execution
aws stepfunctions start-execution \
  --state-machine-arn arn:aws:states:us-east-1:ACCOUNT:stateMachine:HealthcareValidationWorkflow \
  --input '{"s3Bucket":"your-bucket","s3Key":"submissions/SUB-2024-002456.txt"}'

# Expected: Pauses at WaitForHumanDecision state
# Check email for review request
# Click APPROVE/REJECT/REQUEST_MORE_INFO
# Workflow resumes and completes
```

### Scenario 3: Auto-Rejection Path (High Risk)
```bash
# Upload submission-high-risk.txt to S3
aws s3 cp submission-high-risk.txt s3://your-bucket/submissions/SUB-2024-003789.txt

# Start execution
aws stepfunctions start-execution \
  --state-machine-arn arn:aws:states:us-east-1:ACCOUNT:stateMachine:HealthcareValidationWorkflow \
  --input '{"s3Bucket":"your-bucket","s3Key":"submissions/SUB-2024-003789.txt"}'

# Expected: Completes in 5-10 seconds with AUTO-REJECTED status
```

### Scenario 4: Request More Info Path
```bash
# Upload submission-incomplete.txt to S3
aws s3 cp submission-incomplete.txt s3://your-bucket/submissions/SUB-2024-004012.txt

# Start execution
aws stepfunctions start-execution \
  --state-machine-arn arn:aws:states:us-east-1:ACCOUNT:stateMachine:HealthcareValidationWorkflow \
  --input '{"s3Bucket":"your-bucket","s3Key":"submissions/SUB-2024-004012.txt"}'

# Expected: Pauses at WaitForHumanDecision
# Click "REQUEST MORE INFORMATION" in email
# Workflow routes to RequestMoreInfo state
```

### Scenario 5: Timeout Handling
```bash
# Upload any medium-risk submission
# Start execution
# DO NOT click any buttons in the review email
# Wait for timeout (24 hours in production, can be reduced to 60 seconds for demo)
# Expected: Workflow automatically routes to ReviewTimeout and sends escalation notification
```

## File Upload Commands

```bash
# Create S3 bucket (if not exists)
aws s3 mb s3://healthcare-submissions-poc

# Upload all sample files
aws s3 cp submission-good.txt s3://healthcare-submissions-poc/submissions/SUB-2024-001234.txt
aws s3 cp submission-medium-risk.txt s3://healthcare-submissions-poc/submissions/SUB-2024-002456.txt
aws s3 cp submission-high-risk.txt s3://healthcare-submissions-poc/submissions/SUB-2024-003789.txt
aws s3 cp submission-incomplete.txt s3://healthcare-submissions-poc/submissions/SUB-2024-004012.txt
aws s3 cp submission-invalid-format.txt s3://healthcare-submissions-poc/submissions/SUB-2024-005000.txt

# Verify uploads
aws s3 ls s3://healthcare-submissions-poc/submissions/
```

## Expected AI Validation Responses

### For submission-good.txt
```json
{
  "riskScore": 20,
  "recommendation": "AUTO_APPROVE",
  "reasoning": "Complete submission with all required fields. Organization has clean compliance history. Premiums and deductibles are within normal market ranges. Professional attestation provided. No red flags identified.",
  "issues": [],
  "validationPassed": true
}
```

### For submission-medium-risk.txt
```json
{
  "riskScore": 65,
  "recommendation": "HUMAN_REVIEW",
  "reasoning": "Submission has several concerns that require human judgment. Low premium ($285) is below market average. High deductible ($5500) may create access barriers. Missing deductible for Plan 2. Organization has pending issues with state regulator.",
  "issues": [
    "Premium $285 is suspiciously low for stated coverage",
    "Deductible $5500 is very high and may violate ACA guidelines",
    "Plan 2 missing required deductible information",
    "Outstanding issues with state regulator noted",
    "Limited provider network (5000 providers)"
  ],
  "validationPassed": false
}
```

### For submission-high-risk.txt
```json
{
  "riskScore": 90,
  "recommendation": "AUTO_REJECT",
  "reasoning": "Multiple critical violations detected. Organization ID format invalid. Email address incomplete. Unrealistic premium pricing ($99 and $1850). Poor compliance history with 8 rejections and pending state investigation. Missing required license information.",
  "issues": [
    "Organization ID 'ORG-12345' does not match required format 'ORG-HC-XXXXX'",
    "Email 'contact@quickcare' is invalid (missing domain)",
    "Premium $99 is not financially viable for stated coverage",
    "Premium $1850 with $0 copays is unrealistic (possible fraud)",
    "8 previous rejections and state investigation pending",
    "State license number not yet issued",
    "Incomplete attestation without proper signature"
  ],
  "validationPassed": false
}
```

## Demo Tips

1. **Start with good submission** to show the happy path (5-second auto-approval)
2. **Show medium-risk submission** to demonstrate human callback (wait-pause-resume)
3. **Show high-risk submission** to demonstrate auto-rejection with detailed reasoning
4. **Keep the execution graphs open side-by-side** to show different paths visually

This covers all major workflow paths and demonstrates the power of AI-driven risk assessment combined with human-in-the-loop decision making.
