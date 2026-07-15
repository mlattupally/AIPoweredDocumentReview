# Healthcare Plan Submission Underwriter's Handbook
# CMS Regional Office — Plan Review Division
# Version 3.2 | Effective January 1, 2024 | INTERNAL USE ONLY

---

## Section 1: Purpose and Scope

This handbook is the authoritative reference for reviewing healthcare plan submissions
under the Affordable Care Act (ACA). All reviewers and automated systems must apply
these guidelines when assessing plan submissions for approval, human review, or rejection.

This document covers:
- Approved premium and deductible benchmarks by region and plan type
- Organization risk classifications based on submission history
- Escalation rules and reviewer assignment criteria
- Known fraud patterns and red flags
- Past case decisions for precedent reference

---

## Section 2: Premium Benchmarks by Region (FY 2024 Approved Submissions)

These ranges are derived from our internal approved submission database and supersede
any general market data. Flag submissions outside these ranges for additional scrutiny.

### Region 1 — West (CA, NV, AZ, OR, WA)
| Plan Type    | Floor   | Median  | Ceiling  | Flag Threshold          |
|--------------|---------|---------|----------|-------------------------|
| HMO Bronze   | $310/mo | $455/mo | $680/mo  | Below $280 or above $750 |
| HMO Silver   | $420/mo | $590/mo | $810/mo  | Below $380 or above $900 |
| PPO Bronze   | $370/mo | $530/mo | $760/mo  | Below $320 or above $850 |
| PPO Silver   | $500/mo | $695/mo | $990/mo  | Below $450 or above $1,100 |
| PPO Gold     | $640/mo | $850/mo | $1,250/mo| Below $580 or above $1,400 |
| HDHP         | $195/mo | $335/mo | $510/mo  | Below $170 or above $580 |

### Region 2 — Southeast (FL, GA, SC, NC, TN)
| Plan Type    | Floor   | Median  | Ceiling  | Flag Threshold          |
|--------------|---------|---------|----------|-------------------------|
| HMO Bronze   | $260/mo | $390/mo | $580/mo  | Below $230 or above $650 |
| HMO Silver   | $340/mo | $510/mo | $730/mo  | Below $300 or above $820 |
| PPO Silver   | $420/mo | $620/mo | $890/mo  | Below $380 or above $980 |
| PPO Gold     | $570/mo | $770/mo | $1,100/mo| Below $510 or above $1,200 |

### Region 3 — Midwest (TX, IL, OH, MI, IN)
| Plan Type    | Floor   | Median  | Ceiling  | Flag Threshold          |
|--------------|---------|---------|----------|-------------------------|
| HMO Bronze   | $240/mo | $370/mo | $560/mo  | Below $210 or above $620 |
| PPO Silver   | $390/mo | $570/mo | $830/mo  | Below $350 or above $920 |
| PPO Gold     | $530/mo | $720/mo | $1,050/mo| Below $480 or above $1,150 |

### Deductible Rules (Internal Policy, supersedes ACA minimums where stricter)
- Bronze plans: $1,500–$7,500. Outside this range = flag for review.
- Silver plans: $800–$4,500. Outside this range = flag for review.
- Gold plans: $300–$2,000. Outside this range = flag for review.
- OOP maximum must be GREATER THAN OR EQUAL TO the deductible. If OOP < deductible = AUTO_REJECT.
- OOP maximum cannot exceed $9,450 (2024 ACA hard limit). If exceeded = AUTO_REJECT.
- Any plan with $0 deductible and premium below regional floor = HIGH RISK fraud indicator.

---

## Section 3: Organization Risk Classifications

### BLACKLISTED — AUTO_REJECT All Submissions

**ORG-HC-55501 — QuickCover Health LLC**
Reason: Submitted 4 plans with identical premiums across CA and NV (2023-Q2).
Actuarially impossible. Referred to state fraud unit. Do not process any submission.

**ORG-HC-29034 — FastPass Insurance Group**
Reason: Fabricated provider network of 2,400,000 (exceeds total US licensed providers).
License was expired at time of submission. Criminal referral filed 2023-Q4.

### ELEVATED SCRUTINY — Assign to SENIOR Reviewer, Do Not Auto-Approve

**ORG-HC-33287 — Sunset Premier Plans**
History: 3 submissions, 3 rejections. Premium 52% below CA benchmark, no actuarial filing.
Action: Any new submission must go to senior reviewer regardless of AI risk score.

**ORG-HC-77104 — NationWide Flex Health**
History: Claimed 48-state coverage with single Texas license. Twice rejected.
Note: Formerly operated as "AllState Health Plans" — same principals, renamed 2022.

### TRUSTED — Standard Processing with Relaxed Auto-Approve Threshold

**ORG-HC-99887 — BlueCross Health Plans Inc.**
History: 12 submissions, 12 approved. Consistent ACA compliance, accurate actuarial filings.
Policy: Auto-approve threshold lowered to risk score < 35 (standard is < 30).

**ORG-HC-44210 — Horizon Health Network**
History: 8 submissions, 8 approved. Strong provider network documentation.
Policy: Standard processing. No special accommodations needed.

**ORG-HC-12045 — Valley Care Partners**
History: 5 submissions, 4 approved, 1 returned for minor correction (missing secondary contact).
Policy: Missing secondary contact alone is not grounds for rejection — flag but proceed.

### NEW ORGANIZATIONS (No History)
Apply standard thresholds. Assign HUMAN_REVIEW cases to JUNIOR reviewers.
Require all mandatory fields including attestation with no exceptions.

---

## Section 4: Reviewer Assignment Policy

### JUNIOR Reviewers — Assign When ALL Are True
- Risk score: 31–50
- First-time submitter or trusted organization
- No active fraud flags or blacklist matches
- Single-state coverage area
- Fewer than 3 plans in submission
- Estimated review time: 30 minutes or less

### SENIOR Reviewers — Assign When ANY Is True
- Risk score: 51–70
- Organization has prior rejections on record
- Multi-state coverage (3 or more states)
- Premium outside regional benchmark range
- Elevated scrutiny organization
- Estimated review time exceeds 30 minutes

### LEAD Reviewer — Escalation Only, Do Not Assign Directly
- Risk score > 65 but below auto-reject threshold
- Legal or compliance team involvement required
- Reviewer requests second opinion
- Submission involves more than 5 distinct plans

### Current Reviewer Roster (Updated Weekly)
| Name           | Level  | Max Concurrent | Specialty                    |
|----------------|--------|----------------|------------------------------|
| Alice Wong     | SENIOR | 5              | West region, fraud detection |
| Bob Patel      | JUNIOR | 3              | General, Mon–Fri             |
| Carol Smith    | LEAD   | 8              | Escalations only             |
| David Kim      | JUNIOR | 3              | Part-time, Tue/Thu/Fri only  |
| Rachel Torres  | SENIOR | 5              | Multi-state, complex plans   |

### SLA Targets
| Level  | Decision Target | Breach Action              |
|--------|----------------|----------------------------|
| JUNIOR | 24 hours       | Auto-escalate to SENIOR    |
| SENIOR | 48 hours       | Notify supervisor          |
| LEAD   | 72 hours       | Director review required   |

---

## Section 5: Known Fraud Patterns

### Pricing Fraud
- Premium identical across all plans regardless of type or coverage area
- Premium below $100/month for any non-catastrophic plan
- $0 deductible combined with premium below regional floor
- Out-of-pocket maximum lower than the deductible (mathematically impossible)

### Credential and Identity Fraud
- License number format does not match the issuing state's standard
- NAIC code does not match organization name in public records
- Federal Tax ID fewer than 9 digits or does not match IRS EIN database format
- Contact email uses free provider domain (gmail, yahoo, hotmail) for a large insurer
- Organization name is generic with no verifiable business history

### Network Fraud
- Provider network size exceeds total licensed providers in the coverage states
- Nationwide coverage claimed by a single-state licensee
- Network described as "all licensed providers" without a contracted network agreement

### Document Red Flags
- Placeholder text left unfilled (e.g., [INSERT ORGANIZATION NAME])
- Attestation signed by a person not listed as a contact in the submission
- Effective date in the past (backdating)
- Submission missing entire required sections (Compliance Information, Attestation)

---

## Section 6: Precedent Case Decisions

**Case REF-2023-004421 | REJECTED**
Org: QuickCover Health LLC | Risk Score: 91
Reason: Four plans, identical $299/mo premium across HMO and PPO in two states.
Lesson: Identical premiums across different plan types = fraud indicator. AUTO_REJECT.

**Case REF-2023-007832 | REJECTED**
Org: Sunset Premier Plans | Risk Score: 84
Reason: Bronze HMO at $89/month in California — 80% below regional benchmark.
State license status listed as "pending." No actuarial filing included.
Lesson: Premium below floor in any Region 1 state + pending license = AUTO_REJECT.

**Case REF-2023-011045 | REJECTED**
Org: First-time submitter | Risk Score: 78
Reason: OOP maximum ($1,200) lower than deductible ($3,500). Mathematically impossible.
Lesson: Always verify OOP >= deductible. This is a hard reject with no exceptions.

**Case REF-2023-015670 | HUMAN_REVIEW → APPROVED**
Org: Valley Care Partners (ORG-HC-12045) | Risk Score: 44
Reason: Missing secondary contact, 555-format phone number on primary contact.
Outcome: Returned for correction, resubmitted, approved within 48 hours.
Lesson: Minor contact issues from trusted orgs = HUMAN_REVIEW, not immediate rejection.

**Case REF-2024-000312 | HUMAN_REVIEW → APPROVED**
Org: MountainView Health Co. (first-time) | Risk Score: 52
Reason: Premium 15% above regional benchmark — unusual but not disqualifying.
Senior reviewer confirmed actuarial filing justified the higher rate.
Lesson: Above-benchmark premiums require actuarial justification, not automatic rejection.

**Case REF-2024-001891 | REJECTED**
Org: USA Best Plans Inc. (first-time) | Risk Score: 95
Reason: Claimed all 50 states with Florida-only license. Network of 2,000,000 providers.
Contact email was a Gmail address for an organization claiming national scale.
Lesson: Any combination of nationwide claim + single-state license + impossible network = AUTO_REJECT.

---

## Section 7: Quick Reference — Decision Thresholds

| Risk Score | Recommendation  | Notes                                      |
|------------|----------------|--------------------------------------------|
| 0–30       | AUTO_APPROVE   | Trusted org: threshold lowered to 0–35     |
| 31–70      | HUMAN_REVIEW   | Route by reviewer assignment policy above  |
| 71–100     | AUTO_REJECT    | No exceptions unless Lead override         |

Any single BLACKLISTED org match = AUTO_REJECT regardless of risk score.
Any ELEVATED SCRUTINY org match = HUMAN_REVIEW minimum, assign SENIOR reviewer.

---

*End of Handbook — Version 3.2*
*Next review date: July 1, 2024*
*Owner: CMS Plan Review Operations | classification: INTERNAL*
