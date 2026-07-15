# seed-reviewers.ps1
# Inserts 12 sample reviewers into the HealthcareReviewers DynamoDB table

$TABLE = "HealthcareReviewers"
$REGION = "us-east-2"

$reviewers = @(
    @{
        reviewerId = "REV-001"; name = "Dr. Sarah Mitchell"; email = "s.mitchell@cms.gov"
        reviewerLevel = "SENIOR"; currentWorkload = 2; maxConcurrentReviews = 5
        avgReviewTimeMinutes = 45; approvalRate = "0.82"; isAvailable = $true
        expertise = @("medicare_advantage", "risk_adjustment", "premium_pricing")
        updatedAt = "2026-07-01T08:00:00Z"
    },
    @{
        reviewerId = "REV-002"; name = "James Okafor"; email = "j.okafor@cms.gov"
        reviewerLevel = "JUNIOR"; currentWorkload = 3; maxConcurrentReviews = 6
        avgReviewTimeMinutes = 75; approvalRate = "0.71"; isAvailable = $true
        expertise = @("medicaid", "plan_benefits", "formulary")
        updatedAt = "2026-07-01T08:00:00Z"
    },
    @{
        reviewerId = "REV-003"; name = "Dr. Linda Zhao"; email = "l.zhao@cms.gov"
        reviewerLevel = "PRINCIPAL"; currentWorkload = 1; maxConcurrentReviews = 3
        avgReviewTimeMinutes = 30; approvalRate = "0.91"; isAvailable = $true
        expertise = @("actuarial_review", "risk_adjustment", "compliance", "premium_pricing")
        updatedAt = "2026-07-01T08:00:00Z"
    },
    @{
        reviewerId = "REV-004"; name = "Marcus Williams"; email = "m.williams@cms.gov"
        reviewerLevel = "SENIOR"; currentWorkload = 4; maxConcurrentReviews = 5
        avgReviewTimeMinutes = 50; approvalRate = "0.78"; isAvailable = $true
        expertise = @("plan_benefits", "network_adequacy", "formulary")
        updatedAt = "2026-07-01T08:00:00Z"
    },
    @{
        reviewerId = "REV-005"; name = "Emily Thornton"; email = "e.thornton@cms.gov"
        reviewerLevel = "JUNIOR"; currentWorkload = 5; maxConcurrentReviews = 6
        avgReviewTimeMinutes = 90; approvalRate = "0.68"; isAvailable = $false
        expertise = @("medicaid", "eligibility")
        updatedAt = "2026-07-01T08:00:00Z"
    },
    @{
        reviewerId = "REV-006"; name = "Dr. Robert Patel"; email = "r.patel@cms.gov"
        reviewerLevel = "PRINCIPAL"; currentWorkload = 2; maxConcurrentReviews = 3
        avgReviewTimeMinutes = 25; approvalRate = "0.95"; isAvailable = $true
        expertise = @("actuarial_review", "compliance", "risk_adjustment", "fraud_detection")
        updatedAt = "2026-07-01T08:00:00Z"
    },
    @{
        reviewerId = "REV-007"; name = "Angela Torres"; email = "a.torres@cms.gov"
        reviewerLevel = "SENIOR"; currentWorkload = 1; maxConcurrentReviews = 5
        avgReviewTimeMinutes = 40; approvalRate = "0.85"; isAvailable = $true
        expertise = @("network_adequacy", "formulary", "plan_benefits")
        updatedAt = "2026-07-01T08:00:00Z"
    },
    @{
        reviewerId = "REV-008"; name = "Kevin Nakamura"; email = "k.nakamura@cms.gov"
        reviewerLevel = "JUNIOR"; currentWorkload = 2; maxConcurrentReviews = 6
        avgReviewTimeMinutes = 80; approvalRate = "0.73"; isAvailable = $true
        expertise = @("eligibility", "medicaid", "plan_benefits")
        updatedAt = "2026-07-01T08:00:00Z"
    },
    @{
        reviewerId = "REV-009"; name = "Dr. Priya Sharma"; email = "p.sharma@cms.gov"
        reviewerLevel = "SENIOR"; currentWorkload = 3; maxConcurrentReviews = 5
        avgReviewTimeMinutes = 55; approvalRate = "0.80"; isAvailable = $true
        expertise = @("risk_adjustment", "premium_pricing", "actuarial_review")
        updatedAt = "2026-07-01T08:00:00Z"
    },
    @{
        reviewerId = "REV-010"; name = "Thomas Bergman"; email = "t.bergman@cms.gov"
        reviewerLevel = "JUNIOR"; currentWorkload = 6; maxConcurrentReviews = 6
        avgReviewTimeMinutes = 85; approvalRate = "0.69"; isAvailable = $false
        expertise = @("plan_benefits", "formulary")
        updatedAt = "2026-07-01T08:00:00Z"
    },
    @{
        reviewerId = "REV-011"; name = "Dr. Claire Dupont"; email = "c.dupont@cms.gov"
        reviewerLevel = "PRINCIPAL"; currentWorkload = 0; maxConcurrentReviews = 3
        avgReviewTimeMinutes = 20; approvalRate = "0.97"; isAvailable = $true
        expertise = @("compliance", "fraud_detection", "actuarial_review", "risk_adjustment", "premium_pricing")
        updatedAt = "2026-07-01T08:00:00Z"
    },
    @{
        reviewerId = "REV-012"; name = "Samuel Adeyemi"; email = "s.adeyemi@cms.gov"
        reviewerLevel = "SENIOR"; currentWorkload = 0; maxConcurrentReviews = 5
        avgReviewTimeMinutes = 42; approvalRate = "0.88"; isAvailable = $true
        expertise = @("medicare_advantage", "network_adequacy", "compliance")
        updatedAt = "2026-07-01T08:00:00Z"
    }
)

foreach ($r in $reviewers) {
    $expertiseSS = ($r.expertise | ForEach-Object { "{`"S`": `"$_`"}" }) -join ","

    $item = @"
{
  "reviewerId":           {"S": "$($r.reviewerId)"},
  "name":                 {"S": "$($r.name)"},
  "email":                {"S": "$($r.email)"},
  "reviewerLevel":        {"S": "$($r.reviewerLevel)"},
  "currentWorkload":      {"N": "$($r.currentWorkload)"},
  "maxConcurrentReviews": {"N": "$($r.maxConcurrentReviews)"},
  "avgReviewTimeMinutes": {"N": "$($r.avgReviewTimeMinutes)"},
  "approvalRate":         {"N": "$($r.approvalRate)"},
  "isAvailable":          {"BOOL": $($r.isAvailable.ToString().ToLower())},
  "expertise":            {"SS": [$expertiseSS]},
  "updatedAt":            {"S": "$($r.updatedAt)"}
}
"@

    Write-Host "Inserting $($r.reviewerId) - $($r.name) ($($r.reviewerLevel))..."
    aws dynamodb put-item `
        --table-name $TABLE `
        --region $REGION `
        --item $item

    if ($LASTEXITCODE -eq 0) {
        Write-Host "  OK"
    } else {
        Write-Host "  FAILED"
    }
}

Write-Host "`nDone. Inserted $($reviewers.Count) reviewers."
