# AGP pattern detection — porting spec

This document describes how the Diafit backend derives the human-readable
**pattern** strings ("agp_trends") from an AGP (Ambulatory Glucose Profile).
It is written so that another implementation (e.g. the Android app, in Kotlin)
can reproduce the output exactly, given the same AGP profile. How the AGP
itself is computed is out of scope.

Source of truth in this repo:

| What | File |
|---|---|
| Thresholds and time windows | `summary/features/agp/config.py` |
| Pattern detection | `summary/features/agp/patterns.py` (`detect_agp_patterns`) |

All glucose values are **mg/dL**. All "hours" are **local wall-clock hours of
the user**.

---

## 1. Input

The AGP profile: five arrays `p10, p25, p50, p75, p90`, each with **288
points** (one every 5 minutes, index 0 = 00:00, index 287 = 23:55;
`index = hour * 12 + minute / 5`). In the backend every value is rounded to
**1 decimal** before detection runs, so round the same way if your profile has
more precision. If there is no profile, the result is `null`.

---

## 2. Time-of-day periods

Used by most detectors. Iteration order matters
(it determines the order of the output strings):

| Order | Name | Hours (local) | Indices in 288-array |
|---|---|---|---|
| 1 | `night` | 22:00 – 07:00 (wraps midnight) | 264..287 and 0..83 |
| 2 | `morning` | 07:00 – 11:00 | 84..131 |
| 3 | `noon` | 11:00 – 15:00 | 132..179 |
| 4 | `afternoon` | 15:00 – 18:00 | 180..215 |
| 5 | `evening` | 18:00 – 22:00 | 216..263 |

General rule: period `(start, end)` → indices `start*12 ..< end*12`; if
`start > end` it's `start*12 ..< 288` followed by `0 ..< end*12`.

---

## 3. Pattern detection (`agp_trends`)

### 3.1 Derived arrays (all length 288)
```
iqr        = p75 - p25
outer_band = p90 - p10
```
`mean` = arithmetic mean, `min`/`max` over the given index range. Slices
`a[i:j]` below are **half-open** (`i` inclusive, `j` exclusive).

### 3.2 Constants (mg/dL unless noted)
| Name | Value | Name | Value |
|---|---|---|---|
| HYPO_THRESHOLD | 70 | SEVERE_HYPO_THRESHOLD | 54 |
| TARGET_HIGH | 180 | VERY_HIGH | 250 |
| OPTIMAL_LOW / OPTIMAL_HIGH | 70 / 140 | FASTING_OPTIMAL_LOW / HIGH | 70 / 100 |
| FASTING_TARGET_HIGH | 130 | WIDE_IQR | 60 |
| TIGHT_IQR | 30 | CONSISTENCY_THRESHOLD | 100 |
| CONSISTENT_OUTER_BAND | 40 | MEAL_SPIKE_THRESHOLD | 50 |
| DAWN_RISE_THRESHOLD | 20 | RAPID_SPIKE_TIME_THRESHOLD | 30 min |
| RAPID_SPIKE_RISE_THRESHOLD | 50 | PROLONGED_ELEVATION_DURATION | 90 min |
| ELEVATION_THRESHOLD_OFFSET | 30 | RECOVERY_THRESHOLD | 20 |
| RECOVERY_THRESHOLD_DELAYED | 30 | GOOD_MEAL_RISE_MIN / MAX | 20 / 40 |
| GOOD_MEAL_PEAK_TIME_MIN / MAX | 30 / 90 min | | |

Meal windows (hours): `(meal, pre, start, end)` =
`(breakfast, 6, 7, 10)`, `(lunch, 11, 12, 15)`, `(dinner, 18, 19, 22)` —
always iterated in this order. Index = hour × 12.

`config.py` also defines constants that are **not used** by any detector
(`TARGET_LOW`, `MORNING/BEDTIME_CONSISTENCY_*`, `INCONSISTENT_*_THRESHOLD`,
`STABLE_NIGHT_IQR`, `FLUCTUATING_NIGHT_IQR`). Don't implement anything for them.

### 3.3 Detectors — run in exactly this order, appending to one list

`{period}` is the period name from §2 (`night`, `morning`, `noon`,
`afternoon`, `evening`); `{meal}` is `breakfast`, `lunch` or `dinner`.
Strings are English, case-sensitive, no trailing full stop. They intentionally
omit the word "period" (e.g. `Tight glucose control during night`), unlike the
backend, which currently appends it (`… during night period`).

**1. Hypoglycemia** — for each period (order of §2); first matching branch only:
| Condition | Text |
|---|---|
| `min(p50[period]) < 70` | `Consistent hypoglycemia during {period}` |
| else `min(p10[period]) < 54` | `Sporadic, very dangerous hypoglycemia during {period}` |
| else `min(p25[period]) < 70` | `Recurring hypoglycemia during {period}` |

**2. Hyperglycemia** — for each period; first matching branch only:
| Condition | Text |
|---|---|
| `mean(p50[period]) > 250` | `Very high glucose during {period}` |
| else `mean(p50[period]) > 180` | `Elevated glucose during {period}` |
| else `mean(p75[period]) > 180` | `Frequent glucose elevations during {period}` |

**3. Meal spikes** — for each meal:
```
pre  = mean(p50[pre*12 : start*12])
post = max (p50[start*12 : end*12])
if post - pre > 50 → "Post-{meal} glucose spike"
```

**4. Dawn phenomenon**
```
startG = mean(p50[36:42])   // 03:00–03:25
endG   = mean(p50[78:84])   // 06:30–06:55
if endG - startG > 20 → "Dawn phenomenon detected"
```

**5. Somogyi effect**
```
nightMin   = min (p50[24:48])   // 02:00–03:55
morningAvg = mean(p50[72:96])   // 06:00–07:55
if nightMin < 70 and morningAvg > 180 → "Possible rebound hyperglycemia (Somogyi effect)"
```

**6. Fasting** — window `[60:84]` (05:00–06:55); first matching branch only:
```
m = mean(p50[60:84]);  q = mean(iqr[60:84])
if m > 130                         → "Elevated fasting glucose levels"
else if 70 <= m <= 100 and q < 30  → "Optimal fasting glucose control"
else if m < 70                     → "Low fasting glucose levels"
(100 < m <= 130, or optimal m with q >= 30 → nothing)
```

**7. Meal response quality** — for each meal (`pre, start, end` → indices ×12):
```
baseline  = mean(p50[pre : start])
window    = p50[start : end]
peak      = max(window)
ttpMin    = argmax(window) * 5          // FIRST index of the max, minutes after meal start
rise      = peak - baseline
elevMin   = count(window > baseline + 30) * 5
finalG    = mean(p50[end-6 : end])      // last 30 min of the window
recovered = abs(finalG - baseline) < 20
```
Independent checks (each may fire, in this order):
| Condition | Text |
|---|---|
| `ttpMin < 30 and rise > 50` | `Rapid post-{meal} glucose spike` |
| `elevMin > 90` | `Extended post-{meal} elevation` |
| `!recovered and finalG > baseline + 30` | `Slow post-{meal} glucose recovery` |
| `20 < rise < 40 and 30 < ttpMin < 90 and recovered` | `Well-controlled post-{meal} glucose response` |

Note: detector 3 and 7 are separate loops — all breakfast/lunch/dinner
"spike" strings come first, then (after dawn/Somogyi/fasting) the detailed
meal-response strings, grouped by meal.

**8. Variability** — for each period:
`mean(iqr[period]) > 60` → `High glucose variability during {period}`

**9. Tight control** — for each period:
`70 <= mean(p50[period]) <= 140 and mean(iqr[period]) < 30` →
`Tight glucose control during {period}`

**10. Overall** — over all 288 points; first matching branch only:
```
m = mean(p50); q = mean(iqr)
if 70 <= m <= 140 and q < 30 → "Excellent overall glucose control"
else if m < 70                → "Overall glucose trending low"
else if m > 180               → "Overall glucose trending high"
```

**11. Consistency** — for each period; first matching branch only:
| Condition | Text |
|---|---|
| `mean(outer_band[period]) > 100` | `Inconsistent glucose patterns during {period}` |
| else `mean(outer_band[period]) < 40` | `Consistent glucose patterns during {period}` |

### 3.4 Result
- The list in the order produced above. Duplicates of meaning are possible and
  intended (e.g. `Post-dinner glucose spike` and `Rapid post-dinner glucose spike`).
- Empty list → the backend stores `null` (shows "No patterns detected for this
  period."). Treat `null` and `[]` the same.
- Comparisons are exactly as written (strict `<`/`>` vs inclusive `<=`/`>=`).

### 3.5 Complete catalog of possible strings
```
Consistent hypoglycemia during {period}
Sporadic, very dangerous hypoglycemia during {period}
Recurring hypoglycemia during {period}
Very high glucose during {period}
Elevated glucose during {period}
Frequent glucose elevations during {period}
Post-{meal} glucose spike
Dawn phenomenon detected
Possible rebound hyperglycemia (Somogyi effect)
Elevated fasting glucose levels
Optimal fasting glucose control
Low fasting glucose levels
Rapid post-{meal} glucose spike
Extended post-{meal} elevation
Slow post-{meal} glucose recovery
Well-controlled post-{meal} glucose response
High glucose variability during {period}
Tight glucose control during {period}
Excellent overall glucose control
Overall glucose trending low
Overall glucose trending high
Inconsistent glucose patterns during {period}
Consistent glucose patterns during {period}
```
`{period}` ∈ {night, morning, noon, afternoon, evening};
`{meal}` ∈ {breakfast, lunch, dinner}.

---

## 4. UI hint (optional): highlighting the chart for a pattern

The web dashboard lets the user tap a pattern to highlight a time range on the
AGP chart. It lower-cases the string and picks the **first** key (in this
order) that is a substring of it:

| Key | Hours |
|---|---|
| night | 22 – 7 |
| morning | 7 – 11 |
| afternoon | 15 – 18 |
| noon | 11 – 15 |
| lunch | 11 – 15 |
| evening | 18 – 22 |
| dinner | 18 – 22 |
| breakfast | 7 – 10 |
| dawn | 3 – 7 |
| fasting | 5 – 7 |
| overnight | 22 – 7 |

No match (e.g. "Overall …", "Somogyi") → no highlight. (`afternoon` is checked
before `noon` because "afternoon" contains "noon".)

---

## 5. Reference test vectors

Generated with the backend's actual code; use them as unit tests. Each profile
is built from a mean curve `m(h)` and a spread `s(h)`, with `h = i / 12`
(`i = 0..287`):
```
p10 = round1(m - 2s)   p25 = round1(m - s)   p50 = round1(m)
p75 = round1(m + s)    p90 = round1(m + 2s)
```
(`round1` = round to 1 decimal.)

**A** — `m(h) = 120 + 60·e^(-(h-8.5)²/1.5) + 50·e^(-(h-20)²/2) - 55·e^(-(h-3)²/1.0)`, `s = 15`
```json
["Consistent hypoglycemia during night",
 "Post-breakfast glucose spike",
 "Dawn phenomenon detected",
 "Extended post-breakfast elevation",
 "Well-controlled post-dinner glucose response"]
```

**B** — `m(h) = 200 + 70·e^(-(h-13)²/0.5)`, `s = 35`
```json
["Elevated glucose during night",
 "Elevated glucose during morning",
 "Elevated glucose during noon",
 "Elevated glucose during afternoon",
 "Elevated glucose during evening",
 "Post-lunch glucose spike",
 "Elevated fasting glucose levels",
 "High glucose variability during night",
 "High glucose variability during morning",
 "High glucose variability during noon",
 "High glucose variability during afternoon",
 "High glucose variability during evening",
 "Overall glucose trending high",
 "Inconsistent glucose patterns during night",
 "Inconsistent glucose patterns during morning",
 "Inconsistent glucose patterns during noon",
 "Inconsistent glucose patterns during afternoon",
 "Inconsistent glucose patterns during evening"]
```

**C** — `m = 110`, `s = 8` (fasting 110 falls in the 100–130 gap, so no fasting string)
```json
["Tight glucose control during night",
 "Tight glucose control during morning",
 "Tight glucose control during noon",
 "Tight glucose control during afternoon",
 "Tight glucose control during evening",
 "Excellent overall glucose control",
 "Consistent glucose patterns during night",
 "Consistent glucose patterns during morning",
 "Consistent glucose patterns during noon",
 "Consistent glucose patterns during afternoon",
 "Consistent glucose patterns during evening"]
```
