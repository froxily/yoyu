# Yoyū (余裕) specification

Yoyū is a working name; the code name is Shift28 (Java package, database, repository). The product name lives only in `frontend/src/brand.ts`.

## 1. Purpose

International students in Japan with permission for part-time work may work at most 28 hours in any 7 consecutive days, counting all jobs and overtime together. During their school's long vacations the limit becomes 8 hours per day and 40 hours per 7 days. Employers' tools only see their own shifts; the student is the only person who sees every job. Shift28 is the student-side tool: log shifts from every job, see how many hours are left on any day, get warned before accepting a shift that breaks the limit, and estimate pay.

Informational only — not legal advice — [official rules (ISA)](https://www.moj.go.jp/isa/applications/procedures/nyuukokukanri07_00003.html). If you hold an individual permission, your limits may differ. Where the rules are unclear the app applies the stricter reading and says so. Every screen ends with this notice, the official rules link and the individual-permission line.

## 2. Domain rules

### 2.1 Constants

| Name | Value |
| --- | --- |
| ZONE | Asia/Tokyo |
| REGULAR_WINDOW_LIMIT | 1680 minutes (28 h) |
| VACATION_WINDOW_LIMIT | 2400 minutes (40 h) |
| VACATION_DAY_LIMIT | 480 minutes (8 h) |
| WINDOW_DAYS | 7 |
| MAX_SHIFT_LENGTH | 24 hours |
| LATE_NIGHT | 22:00 to 05:00 in ZONE |
| OVERTIME_THRESHOLD | 480 worked minutes per job per work day |

The three limits are the baseline rule version (2.10); the version in effect on a date decides its limits.

### 2.2 Shifts and worked time

- A shift has a job, `startAt` and `endAt` (half-open interval `[startAt, endAt)`), and an optional single break interval `[breakStart, breakEnd)` inside the shift.
- Worked minutes = minutes in `[startAt, endAt)` minus minutes in the break.
- Constraints: `endAt > startAt`; length at most 24 hours; break both null or both set, with `startAt <= breakStart < breakEnd <= endAt`.
- A user cannot have two overlapping shifts, across any jobs. Touching intervals (one ends exactly when the next starts) are allowed.
- Breaks are stored as an interval rather than a number of minutes because both day attribution and pay depend on when the break happened.

### 2.3 Day attribution

Worked minutes are assigned to dates in two ways. Compliance is evaluated under both, and the worse result is reported.

- **CALENDAR_SPLIT**: each date in ZONE receives the worked minutes that fall between its 00:00 and the next 00:00.
- **START_DAY**: the whole shift's worked minutes go to the date (in ZONE) on which the shift starts. Japanese labour practice treats one continuous shift crossing midnight as belonging to its start day.

### 2.4 Vacation periods

- The user records long-vacation periods as inclusive date ranges. A date is a vacation date if any period covers it.

### 2.5 Compliance statuses

Status order from best to worst: `OK` < `AMBIGUOUS` < `VIOLATION`.

**Windows.** For every 7-date window `[d, d+6]` and each attribution, total = sum of attributed minutes on those dates.

- No vacation dates in the window: `VIOLATION` if total > 1680, else `OK`.
- All dates are vacation dates: `VIOLATION` if total > 2400, else `OK`.
- Mixed window: `VIOLATION` if total > 2400; `AMBIGUOUS` if 1680 < total <= 2400; else `OK`. The rules do not clearly say which limit applies to a week that crosses into or out of a vacation, so the app flags it instead of guessing.

**Days.** On a vacation date, under each attribution: `VIOLATION` if that date's total > 480, else `OK`. Regular dates have no daily cap from this rule.

**Reported status** for a window or day is the worse of its two attributions, and the response says which attribution produced it.

### 2.6 Capacity (hours left on a date)

Capacity on date D = how many more minutes can be worked on D without any window or day becoming `AMBIGUOUS` or `VIOLATION`, assuming no other new shifts:

- For each attribution: the minimum over the 7 windows containing D of (limit − total), where the limit is 2400 for an all-vacation window and 1680 for any other window (the strict limit, so following capacity never produces `AMBIGUOUS`).
- If D is a vacation date, also cap by 480 − that date's total.
- Take the minimum across both attributions and floor at 0.

### 2.7 Checking a draft shift

Given a draft shift (and optionally the id of a shift being edited, which is excluded), evaluate compliance with existing shifts plus the draft. Return the worst status and the list of windows and days whose status is not `OK`, each with dates, total minutes, limit, status and attribution. Only windows and days that include a date the draft touches can change.

### 2.7.1 Explanations

Every window or day whose status is not `OK` lists the shifts that contribute to it under its reported attribution: shift id, job, start, and the minutes that shift adds to that window or day (only the minutes on its dates). Shifts are in start order and their minutes add up to the reported total. A draft is marked as the draft (and has no id when it is new). The UI turns this into a sentence, e.g. "Over 28h in the 7 days from Fri 6 Nov because Cafe Fri 10:00 (7h) + Shop Sat 22:00 (6h) fall in them."

### 2.8 Pay estimate

Each job has an hourly wage in yen and an optional transport allowance in yen per shift.

- Late-night minutes: worked minutes between 22:00 and 05:00 in ZONE.
- Overtime minutes: per job and per work day (START_DAY attribution, shifts in start order, cumulative across that job's shifts on that day), the worked minutes after the first 480.
- Multipliers: normal 1.00, late-night only 1.25, overtime only 1.25, overtime during late-night 1.50.
- Shift pay = round_half_up(Σ minutes × wage × multiplier ÷ 60) + transport allowance.
- Not included (state this in the UI): legal-holiday premium, the 50% premium beyond 60 overtime hours a month, overtime arising from combining hours across different employers (labour law aggregates them, Labour Standards Act Article 38), taxes and deductions. Every figure is labelled "estimate".

### 2.9 Out of scope tonight

Paid leave, multiple breaks per shift, employer accounts, residence-card data of any kind, Japanese UI (stretch), notifications, phone-number sign-in.

### 2.10 Rule versions

- The limits (regular window, vacation window, vacation day) are stored as rule versions, each with an `effective_from` date. The version in effect on a date is the latest one whose `effective_from` is on or before it; dates before the earliest version use the earliest.
- The baseline version (effective 2000-01-01, a sentinel) holds 1680, 2400 and 480 minutes. New versions are added only by a Flyway migration; there is no admin API. A version never changes results for dates before it takes effect, or for windows that end before it.
- A day check uses the vacation day limit of its date.
- A window whose 7 dates fall under one version is evaluated as in 2.5 with that version's limits. A window that spans a change uses, for each limit, the strictest value among the versions in effect on its dates, then applies 2.5; the result lists the versions involved and says the rules changed.
- Capacity (2.6) uses the same per-window limits and each date's own day limit.
- Sources: 28 hours per week and 8 hours per day in long vacations come from the Immigration Services Agency page; the 40 hours per 7 days during long vacations is not on that page and comes from the Labour Standards Act 40-hour week (Townwork). It is kept as an extra, stricter cap.

## 3. Data model (Flyway V1)

Later migrations add `rule_versions` (V4, 2.10), `idempotency_keys` (V5, section 4) and a `version bigint NOT NULL DEFAULT 0` column on `shifts`, `jobs` and `vacation_periods` (V6, section 4). V7 (section 4.1) adds `full_name`, `username` (lower-case `^[a-z0-9_]{3,20}$`, unique) and `email_verified_at` to `users`, makes `email` and `password_hash` optional for social-only accounts (a password still needs an email), and adds `user_identities` (provider, subject; unique per provider subject and per user and provider) and `email_codes` (hashed one-time codes). V8 adds `users.session_version`, which a password reset, password change or email change increments to end sessions created with the old credentials.

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE users (
  id uuid PRIMARY KEY,
  email text NOT NULL,
  password_hash text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email));

CREATE TABLE jobs (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name text NOT NULL,
  hourly_wage_yen integer NOT NULL CHECK (hourly_wage_yen > 0),
  transport_yen integer NOT NULL DEFAULT 0 CHECK (transport_yen >= 0),
  color text NOT NULL DEFAULT '#3b82f6',
  archived boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE shifts (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE RESTRICT,
  start_at timestamptz NOT NULL,
  end_at timestamptz NOT NULL,
  break_start timestamptz,
  break_end timestamptz,
  note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (end_at > start_at),
  CHECK (end_at - start_at <= interval '24 hours'),
  CHECK ((break_start IS NULL) = (break_end IS NULL)),
  CHECK (break_start IS NULL OR (break_start >= start_at AND break_end > break_start AND break_end <= end_at)),
  EXCLUDE USING gist (user_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
);
CREATE INDEX shifts_user_start_idx ON shifts (user_id, start_at);

CREATE TABLE vacation_periods (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  start_date date NOT NULL,
  end_date date NOT NULL,
  label text,
  CHECK (end_date >= start_date)
);
CREATE INDEX vacation_user_idx ON vacation_periods (user_id, start_date);
```

Jobs with shifts are archived, not deleted. If the hosting provider does not allow `btree_gist`, drop the exclusion constraint, enforce the overlap rule in the service inside the same transaction (lock the user's row), and record the change in `docs/DECISIONS.md`.

## 4. API

All endpoints under `/api`, JSON, session-cookie authentication with CSRF protection (`CookieCsrfTokenRepository` plus the SPA token handling recommended by Spring Security). Instants are ISO-8601 with offset; dates are `YYYY-MM-DD` in ZONE. Errors are `ProblemDetail`.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | /auth/register | `{fullName, username, email, password}` → 202 `{verificationId}`; emails a code (4.1) |
| POST | /auth/register/verify | `{verificationId, code}` → 201, creates the account and signs in |
| POST | /auth/codes/resend | `{verificationId}` → 202; 60 s cooldown |
| POST | /auth/login | `{identifier, password}` (email or username); an unverified account gets 403 `verificationRequired` |
| POST | /auth/login/verify | `{verificationId, code}` |
| POST | /auth/password/forgot | `{identifier}` → 202 always |
| POST | /auth/password/reset | `{verificationId, code, password}` → 204; ends every session |
| POST | /auth/reauthenticate, /auth/reauthenticate/email(/verify) | prove it is you (password or emailed code) |
| GET | /auth/providers | `{google, line, email}`: which sign-in methods are configured |
| GET/POST | /auth/social/pending, /auth/social/complete | finish a new Google or LINE sign-up with a name and username |
| POST | /auth/logout | |
| GET | /auth/me | current user: id, email, fullName, username, emailVerified, hasPassword, identities, recentlyAuthenticated |
| PUT | /account/profile | `{fullName, username}` |
| POST | /account/email, /account/email/verify | change or add the email after a code sent to the new address |
| PUT | /account/password | `{password}`: set a first password or change it; needs a recent sign-in |
| POST/DELETE | /account/identities/{provider}/link, /account/identities/{provider} | link (via the provider) or unlink Google or LINE |
| DELETE | /account | delete the user and all their data; needs a recent sign-in |
| GET/POST | /jobs | list (with `?includeArchived`), create |
| PUT/DELETE | /jobs/{id} | update; delete archives if shifts exist |
| POST | /jobs/{id}/restore | un-archive a job |
| GET | /shifts?from=&to= | shifts overlapping the date range |
| POST | /shifts | create; response includes the compliance result for the affected dates; 409 on overlap |
| PUT/DELETE | /shifts/{id} | update, delete |
| GET/POST | /vacations | list, create |
| PUT/DELETE | /vacations/{id} | update, delete |
| GET | /compliance?from=&to= | per-date totals, non-OK windows and days, capacity for today and the next 7 dates |
| POST | /compliance/check | draft shift (plus optional `excludeShiftId`) → worst status and issues |
| GET | /pay?from=&to= | per-job minutes by category, transport, totals in yen |

**Concurrent edits.** Shifts, jobs and vacations carry a `version` in every response. `PUT` requires the `version` the client loaded (400 if missing); a different current version is 409 with `"stale": true` and changes nothing, and the UI offers Reload. Shift and vacation writes lock the user's row (`FOR NO KEY UPDATE`) before checking compliance, so a check and the save it guards never race with another write of the same user. A write that uses an idempotency key takes the key row first and the user row second, never the reverse.

**Idempotent creates.** `POST /jobs`, `/shifts` and `/vacations` accept an optional `Idempotency-Key` header (1 to 255 visible ASCII characters, else 400). Keys are stored per user for 24 hours. The same key with the same method, path and JSON body (field order and whitespace ignored) replays the stored status and body with `Idempotent-Replayed: true` and writes nothing; the same key with a different request is 422. Only successful responses are stored, so a request that failed (400, 409, 422 needing confirmation) can be retried with the same key. Concurrent requests with one key write once.

### 4.1 Accounts and sign-in

- Sign-in methods: email or username with a password, Continue with Google and Continue with LINE (OpenID Connect, PKCE, state and nonce). A provider is offered only when its credentials are configured.
- Email codes: 6 digits from a secure random source, stored only as an HMAC, valid 10 minutes, single use, 5 wrong attempts at most, resend after 60 s, 5 sends at most. Codes are never logged. An email sign-up creates the account only when its code is entered.
- No enumeration: sign-up, forgot password and change email answer the same way whether or not an address has an account; the owner of an address that already has an account is emailed a notice instead of a code. Login gives one 401 for every failure.
- Rate limits: login 5 failures per account from one client IP, 20 failures per account from all clients (both shared by email and username) and 50 attempts per client IP in 15 minutes; sign-up 5 per email and 20 per IP an hour; forgot password 3 per account from one client IP, 10 per account and 10 per IP an hour. An attempt counts from the moment it starts, so parallel requests can't exceed a limit. 429 carries `Retry-After`.
- Accounts are never merged by email. A Google identity whose verified email belongs to an account is refused until the owner signs in and links Google in Settings. LINE emails are never treated as verified.
- A new Google or LINE identity chooses a full name and username before the account is created; Google's verified email becomes the account email. Accounts without a username or name are asked for them after signing in.
- Recent sign-in: deleting the account, changing the email, setting or changing the password and linking or unlinking a provider need proof within 10 minutes: the password, a code to the verified email, or a provider sign-in whose `auth_time` is at most 5 minutes old (LINE is asked with `prompt=login`; LINE auto and SSO logins don't count; completing a Google or LINE sign-up counts only when that provider sign-in was fresh). LINE's ID tokens omit `auth_time`, so for a re-authentication whose own authorization request forced a login (`prompt=login`), an ID token issued within 5 minutes whose `amr` reports a password, QR code or two-factor login also counts. Unlinking the last way to sign in is refused.
- Accounts that sign in with Google or LINE can add a password in Settings once they have a confirmed email, because a password needs an email to be reset; a LINE-only account adds and confirms an email first. Setting or changing a password doesn't ask for the old one separately (the recent sign-in covers it), ends the account's other sessions and emails a notice.
- Forgot password emails a code; resetting ends every session of the account. Accounts that only use Google or LINE are told so instead.
- Changing the email ends the account's other sessions and notifies the old address.
- Email features are off until mail is enabled (`MAIL_ENABLED`); while off, existing accounts with an unverified email sign in without the code. While on, they must enter a code at their next sign-in.
- Phone-number sign-in is out of scope: verifying a phone number through LINE needs LINE notification messages, which require corporate approval, and SMS costs money.

`/compliance` response shape:

```json
{
  "days": [{"date": "2026-11-02", "minutes": 420, "vacation": false, "status": "OK"}],
  "issues": [{"type": "WINDOW", "start": "2026-11-06", "end": "2026-11-12", "minutes": 1740, "limitMinutes": 1680, "status": "VIOLATION", "attribution": "CALENDAR_SPLIT",
              "contributions": [{"shiftId": "…", "jobId": "…", "jobName": "Cafe", "startAt": "2026-11-06T10:00:00+09:00", "minutes": 420, "draft": false}]}],
  "capacity": [{"date": "2026-11-05", "remainingMinutes": 420}]
}
```

## 5. Screens (mobile-first, English)

1. **Home**: hours left today (large), a 7-day strip of hours left per date, and a banner for any current issue. Reloads when the app becomes visible again and at midnight in ZONE.
2. **Add or edit shift**: job, start, end, optional break; a live check result under the form before saving; a confirmation step for future shifts that are not `OK`.
3. **History**: shifts grouped by week with per-date totals and highlighted issues.
4. **Jobs**: create, edit, archive, restore; wage and transport.
5. **Vacations**: create, edit, delete periods.
6. **Pay**: monthly estimate per job with the category breakdown and the "estimate" label.
7. **Settings**: disclaimer ("Informational only — not legal advice", official rules (ISA) link, individual-permission line), sources, profile (name, username), email, sign-in methods (set or change the password, link or unlink Google and LINE), logout, delete account.
8. **Sign in, create account, forgot password, welcome**: Continue with Google or LINE, email or username with a password, the emailed code step, and choosing a name and username.

## 6. PWA requirements

- Web app manifest: name "Yoyū (余裕)", short name "Yoyū", `start_url: "/"`, `display: "standalone"`, theme and background colours, icons at 192 px and 512 px plus a 512 px maskable icon.
- Service worker via vite-plugin-pwa caching only the app shell and static assets. `/oauth2/**` and `/login/oauth2/**` navigations always reach the server. `/api/**` is network-only; when offline, show "Offline — hours can't be checked right now". When online but the server can't be reached (network error, 502, 503, 504), show "Can't reach the server — try again in a moment".
- Served over HTTPS in production.
- Client-side routes: any GET that is not under `/api`, not a static file and not `/.well-known/**` is forwarded to `index.html`, so refreshing or opening a deep link (e.g. `/history`) works in the browser and the installed app.
- Spring Boot serves `/.well-known/assetlinks.json` from static resources when the file exists (needed later for the Android wrapper).

## 7. Test cases

Dates are in Asia/Tokyo. 2026-11-02 is a Monday. "7h" means 420 worked minutes. Vacation period for T8 to T12: 2026-12-21 to 2027-01-06. Domain tests cover T1 to T15, T17 to T19 and T21; persistence and API tests cover T16 and T20.

| ID | Setup | Expected |
| --- | --- | --- |
| T1 | Nov 2–5, 10:00–17:00 each | Every window `OK` (max total 1680) |
| T2 | T1 plus Nov 6 10:00–10:01 | Windows containing Nov 2–6 are `VIOLATION` with total 1681 |
| T3 | Nov 6, 7, 8, 9 10:00–17:00; Nov 10 10:00–11:00 | Calendar weeks look fine (Nov 2–8 = 1260, Nov 9–15 = 480) but window Nov 6–12 = 1740 → `VIOLATION` |
| T4 | Job A Nov 2–5 10:00–15:00; Job B Nov 7 09:00–18:00 | Window Nov 2–8 = 1740 → `VIOLATION` (jobs combined) |
| T5 | Nov 7 22:00 → Nov 8 03:00 | CALENDAR_SPLIT: Nov 7 = 120, Nov 8 = 180. START_DAY: Nov 7 = 300 |
| T6 | Nov 2 10:00–18:00, break 13:00–14:00 | Worked 420 |
| T7 | Nov 7 20:00 → Nov 8 04:00, break 23:30–00:30 | CALENDAR_SPLIT: Nov 7 = 210, Nov 8 = 210. START_DAY: Nov 7 = 420 |
| T8 | Dec 22 09:00–18:00 | Day Dec 22 `VIOLATION` (540 > 480) |
| T9 | Dec 28 – Jan 1 09:00–17:00 | All `OK`; window Dec 28 – Jan 3 = 2400 (every window over 1680 lies fully inside the vacation) |
| T10 | T9 plus Jan 2 09:00–09:01 | Window Dec 28 – Jan 3 = 2401 → `VIOLATION` |
| T11 | Dec 17, 18, 19 09:00–17:00 (regular); Dec 21, 22 09:00–17:00 (vacation) | Window Dec 17–23 = 2400, mixed → `AMBIGUOUS`; no `VIOLATION` |
| T12 | T11 plus Dec 23 09:00–10:00 | Window Dec 17–23 = 2460 → `VIOLATION` |
| T13 | Nov 2, 3, 4 10:00–17:00 | Capacity Nov 5 = 420; capacity Nov 8 = 420; capacity Nov 11 = 1680 |
| T14 | T13 plus a draft Nov 5 10:00–18:00 | Draft check returns `VIOLATION` (window total 1740) |
| T15 | Shift 2026-11-02T19:30+05:30 → 2026-11-02T21:30+05:30 | Equals Nov 2 23:00 → Nov 3 01:00 JST. CALENDAR_SPLIT: Nov 2 = 60, Nov 3 = 60. START_DAY: Nov 2 = 120. Identical results with JVM default zone UTC and Asia/Kolkata |
| T16 | Nov 2 10:00–14:00, then Nov 2 13:00–15:00 (any job) | Second insert rejected (409). Nov 2 14:00–16:00 accepted |
| T17 | Wage 1200, Nov 2 20:00 → Nov 3 00:00 | 2 h × 1200 + 2 h × 1500 = 5400 yen |
| T18 | Wage 1000, Nov 2 13:00 → Nov 3 00:00, break 17:00–18:00 | 8 h normal = 8000; 2 h overtime late-night × 1.5 = 3000; total 11000 yen |
| T19 | Wage 1000, Nov 7 18:00 → Nov 8 04:00 | 18–22 = 4000; 22–02 late-night = 5000; 02–04 overtime late-night = 3000; total 12000 yen |
| T20 | End before start; shift of 24 h 1 min; break outside shift | Each rejected with 400 (validation) and by the database CHECK |
| T21 | Nov 2, 3, 4 10:00–17:00; Nov 5 10:00–16:00; Nov 8 23:00 → Nov 9 03:00 | CALENDAR_SPLIT: window Nov 2–8 = 1680 `OK`. START_DAY: window Nov 2–8 = 1860 `VIOLATION`. Reported: `VIOLATION`, attribution START_DAY |

Rule versions (2.10). Version B takes effect 2026-11-09 with a regular window limit of 1440; version C takes effect 2026-12-23 with a vacation day limit of 420. Domain tests cover T22 to T29; persistence tests cover T30.

| ID | Setup | Expected |
| --- | --- | --- |
| T22 | Baseline only, T1 to T21 | Identical results |
| T23 | Nov 2–5 10:00–17:00; baseline + B | Window Nov 2–8 = 1680 `OK`, identical with or without B |
| T24 | Nov 6, 7, 8 10:00–17:00; Nov 9 10:00–13:00; baseline + B | Window Nov 3–9 = 1440 `OK`; adding Nov 9 13:00–13:01 makes 1441 `VIOLATION`, limit 1440, rules changed, versions 2000-01-01 and 2026-11-09 |
| T25 | T24 with the extra minute, baseline only | Window Nov 3–9 = 1441 `OK` |
| T26 | Baseline + C; vacation Dec 21 – Jan 6; Dec 22 and Dec 23 09:00–16:30 | Day Dec 22 `OK` (limit 480); day Dec 23 `VIOLATION` (limit 420) |
| T27 | Baseline + B, no shifts | Capacity Nov 2 = 1680, Nov 3 = 1440, Nov 9 = 1440 |
| T28 | Baseline + B | 1999-12-31 uses the baseline; Nov 8 the baseline; Nov 9 version B |
| T29 | A rule book with no versions, two versions on one date, or a limit of 0 | Rejected |
| T30 | Fresh database | One seeded version equal to the baseline; effective dates unique; limits must be positive |

Explanations (2.7.1). Domain tests cover E1 to E5; API tests cover E5 and E6.

| ID | Setup | Expected |
| --- | --- | --- |
| E1 | T2 | Window Nov 2–8 lists the five shifts with 420, 420, 420, 420 and 1 minutes (sum 1681) |
| E2 | T21 | The reported START_DAY window Nov 2–8 lists 420, 420, 420, 360 and 240 (the overnight shift counted whole) |
| E3 | Vacation; Dec 22 20:00 → Dec 23 04:00 and Dec 23 09:00–15:00 | Day Dec 23 (CALENDAR_SPLIT, 600) lists 240 and 360 |
| E4 | T1 | `OK` checks list no shifts |
| E5 | T14 | The draft is listed with `draft: true` and no id; an edited shift keeps its id |
| E6 | T2, then archive the job | Contributions still carry the job name |

Also test: a draft that edits an existing shift excludes the original; user A cannot read or modify user B's jobs, shifts or vacations (404); deleting the account removes all rows.

## 8. Build order

| Phase | Work | Done when |
| --- | --- | --- |
| 0. Scaffold | Maven project, Docker Compose PostgreSQL 16, Flyway, health endpoint, Vite React TS app, frontend build copied into static resources, README stub | `./mvnw test` green; app serves the React page at `/` |
| 1. Domain engine | Pure Java: worked time and segments, both attributions, compliance (windows, days, statuses), capacity, draft check, pay calculator | All domain test cases green |
| 2. Persistence | V1 migration exactly as section 3, entities, repositories | Testcontainers tests for every constraint, including T16 and T20 |
| 3. API and auth | Security, controllers, validation, ProblemDetail errors | Integration tests for main flows, user isolation, account deletion |
| 4. UI | Section 5 screens, mobile-first | Usable at 360 px wide; every screen works against the real API |
| 5. PWA and deploy | Manifest, icons, service worker, multi-stage Dockerfile (Node build, Maven build, JRE runtime), env config, deploy | Live HTTPS URL; Chrome on Android offers "Install app" |
| 6. Stretch | SQL report using `SUM(...) OVER (ORDER BY day RANGE BETWEEN INTERVAL '6 days' PRECEDING AND CURRENT ROW)` cross-checked against the engine in a test; Japanese UI; CSV export; JDBC-backed sessions | Each item tested |

## 9. Sources

- Official rules for "Student" status: 28 hours per week, 8 hours per day during the school's long vacations: [Immigration Services Agency](https://www.moj.go.jp/isa/applications/procedures/nyuukokukanri07_00003.html) (Japanese)

- 28 hours in any 7 days, all jobs combined, long-vacation exception: [Shiodome Partners](https://shiodome.co.jp/column/7855/), [Mynavi Global](https://global-saponet.mgl.mynavi.jp/visa/8701)
- Long vacation 40 h in any 7 days (the Labour Standards Act 40-hour week; not on the ISA page), overtime included: [Townwork](https://townwork.net/magazine/?p=73524)
- Employer tool that checks any 7 consecutive days: [freee support](https://support.freee.co.jp/hc/ja/articles/59085137401881)
- Ambiguity around weeks that cross into a vacation: [jinjibu Q&A](https://jinjibu.jp/qa/detl/70284/1/)
- Late-night 25%, overtime 25%, combined 50%: [Money Forward](https://biz.moneyforward.com/payroll/basic/82774/), [Townwork pay example](https://townwork.net/magazine/?p=8000)
