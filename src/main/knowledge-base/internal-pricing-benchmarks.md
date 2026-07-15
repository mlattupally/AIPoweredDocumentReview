# Internal Approved Pricing Benchmarks (Fiscal Year 2024)
# Source: CMS Regional Office Actuarial Division — CONFIDENTIAL

These benchmarks are derived from our approved submission database.
Use these ranges as the reference for market reasonability checks.
These supersede any general market data.

## Region 1 — West (CA, NV, AZ, OR, WA)

| Plan Type | Min Approved | Median Approved | Max Approved | Flag if Outside |
|-----------|-------------|-----------------|-------------|-----------------|
| HMO Bronze | $310 | $455 | $680 | < $280 or > $750 |
| HMO Silver | $420 | $590 | $810 | < $380 or > $900 |
| PPO Bronze | $370 | $530 | $760 | < $320 or > $850 |
| PPO Silver | $500 | $695 | $990 | < $450 or > $1,100 |
| PPO Gold | $640 | $850 | $1,250 | < $580 or > $1,400 |
| HDHP | $195 | $335 | $510 | < $170 or > $580 |

## Region 2 — Southeast (FL, GA, SC, NC, TN)

| Plan Type | Min Approved | Median Approved | Max Approved | Flag if Outside |
|-----------|-------------|-----------------|-------------|-----------------|
| HMO Bronze | $260 | $390 | $580 | < $230 or > $650 |
| HMO Silver | $340 | $510 | $730 | < $300 or > $820 |
| PPO Bronze | $310 | $470 | $690 | < $270 or > $770 |
| PPO Silver | $420 | $620 | $890 | < $380 or > $980 |
| PPO Gold | $570 | $770 | $1,100 | < $510 or > $1,200 |

## Region 3 — Midwest (TX, IL, OH, MI, IN)

| Plan Type | Min Approved | Median Approved | Max Approved | Flag if Outside |
|-----------|-------------|-----------------|-------------|-----------------|
| HMO Bronze | $240 | $370 | $560 | < $210 or > $620 |
| PPO Silver | $390 | $570 | $830 | < $350 or > $920 |
| PPO Gold | $530 | $720 | $1,050 | < $480 or > $1,150 |

## Deductible Acceptability Rules (Internal Policy)

- Bronze plans: Deductible MUST be $1,500–$7,500. Outside range = flag.
- Silver plans: Deductible MUST be $800–$4,500. Outside range = flag.
- Gold plans: Deductible MUST be $300–$2,000. Outside range = flag.
- Any plan with deductible = $0 and premium below regional median = HIGH RISK.

## Out-of-Pocket Maximum Rules

- OOP max must be GREATER than or equal to the deductible — if OOP < deductible, AUTO_REJECT.
- OOP max cannot exceed $9,450 (2024 ACA limit) — if exceeded, AUTO_REJECT.
