# Open Macros

<p align="center"><img src="docs/icon-512.png" width="160" alt="Open Macros icon"></p>

A free, open-source **calorie, macro, weight and workout tracker** for Android: a basic alternative to paid
apps like MacroFactor. Kotlin + Jetpack Compose + Material 3, minSdk 29 (Android 10), targetSdk 36.
No account, no ads, no subscription. Your data stays on your phone.
(The optional photo-logging feature sends meal photos to xAI, only when you use it; see below.)

See [ARCHITECTURE.md](ARCHITECTURE.md) for how it's put together.

## Features (v1)

- **Food search** across [Open Food Facts](https://world.openfoodfacts.org) (packaged products, no key)
  and [USDA FoodData Central](https://fdc.nal.usda.gov) (whole foods + US branded products), merged and
  de-duplicated, with your own foods pinned on top.
- **Barcode scanning** with CameraX + ML Kit (bundled on-device model, no Google Play Services download),
  looked up in Open Food Facts. Not found? Create it as a custom food with the barcode pre-filled. You can
  also type a barcode.
- **Food diary** per day with breakfast, lunch, dinner and snacks. Amounts in servings, grams or ounces.
  **Recent** and **favorite** foods, **quick add** (calories only, or macros), and **custom foods**.
- **Daily summary**: a calorie ring and protein / carbs / fat bars against your targets.
- **Weight**: log weigh-ins (lb or kg), see a chart with a **smoothed trend line** (time-aware exponential
  moving average) plus a 7-day average and week/month change.
- **Goals**: onboarding asks sex, age, height, weight, activity level and goal (lose / maintain / gain at
  a chosen rate). Target = **Mifflin-St Jeor BMR × activity**, ± 7700 kcal per kg of weekly change ÷ 7,
  never below 1200 kcal. The macro split defaults to 30/40/30 % and is editable; you can also set a fixed
  calorie target. Logging a new latest weigh-in updates the weight used for the target.
- **Your data**: export a full JSON backup or a spreadsheet-friendly CSV, and import either one
  (Storage Access Framework, so you pick where files go). Nothing is uploaded anywhere.
- **Photo logging (optional, your own xAI key)**: take a photo or pick one from the gallery, and Grok
  estimates each food's grams, calories and macros. Review everything before it's logged: edit grams
  (macros scale proportionally) or macros, see a confidence badge per item, swap any item for an Open Food
  Facts / USDA match, pick the meal, then log. See [Photo logging](#photo-logging-optional-paid) below.
- **Training**: an exercise library (113 built-in, plus your own), routines with days and per-exercise
  targets, a split recommender for 2–6 days a week, workout logging with a rest timer, per-exercise
  progress charts and PRs, and double-progression suggestions. See [Training](#training) below.
- Follows the system light/dark theme with Material You dynamic color on Android 12+, edge-to-edge.

## Build and install

1. JDK 17 and the Android SDK (platform 36). Android Studio Ladybug or newer works.
2. `local.properties` (gitignored) only needs `sdk.dir=/path/to/Android/sdk`. Android Studio writes it.
   No API keys go into the build.
3. `./gradlew assembleDebug` (APK in `app/build/outputs/apk/debug/`) or `./gradlew installDebug`.
4. Unit tests: `./gradlew testDebugUnitTest` (TDEE math, trend/moving average, parsers, merge, backup).

CI: the workflow lives at [`ci/android.yml`](ci/android.yml). Move it to `.github/workflows/android.yml`
to enable it on GitHub Actions; it runs `assembleDebug` + unit tests and uploads the debug APK.

## USDA API key (optional, free)

USDA search needs an api.data.gov key. Without one the app uses the shared `DEMO_KEY`, which is limited to
about **30 requests an hour and 50 a day** per IP address, so you'll see "rate limit" messages quickly.
A free personal key allows about 1,000 requests an hour. **How to get one** (also under "How do I get this?" in
Settings):

1. Open the sign-up form at <https://fdc.nal.usda.gov/api-key-signup> (Settings → **Get a free key**) and enter
   your name and email.
2. Check your email: the api.data.gov key arrives within a minute or so. No account or password is needed.
3. Copy the key from the email.
4. Paste it in **Settings → USDA FoodData Central key** and tap **Save key**.

Cost: free. API docs: <https://fdc.nal.usda.gov/api-guide>. It's stored only on the device, encrypted with
`EncryptedSharedPreferences` under an Android Keystore master key, never logged, never exported in backups,
and Android backup is disabled for the app.

Open Food Facts needs no key. Every request sends a `User-Agent` identifying the app
(`OpenMacros/<version> (Android; https://github.com/glinesbdev/open-macros)`) as OFF asks. Search is only run
when you submit, to stay well within OFF's rate limits.

## Photo logging (optional, paid)

Photo logging is **off unless you add your own xAI API key** in **Settings → Photo logging**.
**How to get one** (also under "How do I get this?" in Settings):

1. Sign up or log in at <https://console.x.ai> and verify your email.
2. Add credits under Billing (the API is pay-as-you-go).
3. Open the API Keys page, <https://console.x.ai/team/default/api-keys> (Settings → **Get a key** goes straight
   there), and create a new key.
4. Copy the key (it's shown only once), paste it in **Settings → Photo logging** and tap **Save key**.

Cost: paid per request, billed to your own xAI account (details below). API docs: <https://docs.x.ai/developers/quickstart>.
Settings links open in a Custom Tab (or your browser). The key is stored like the USDA key: encrypted on the device with
`EncryptedSharedPreferences` / Android Keystore, sent only in the `Authorization` header to `api.x.ai`,
never logged, and never included in backups.

- **It costs money.** Each photo is a paid request to the xAI API, **billed to your own xAI account** at
  xAI's per-token rates for the model (currently `grok-4.7`, with low reasoning effort). Open Macros adds no
  fee. When xAI reports it, the review screen shows what that estimate cost.
- **Privacy.** Photos are sent to xAI **only when you use this feature**: the moment you take or pick a photo
  on the photo-log screen. Nothing is sent otherwise. The photo is downscaled to about 1024 px and re-encoded
  as JPEG first (which also strips EXIF metadata such as location), sent through the stateless chat completions
  endpoint, and not saved by Open Macros. xAI's own data terms apply to what it receives.
- **Accuracy.** AI estimates are approximate (portion size is the hardest part, often ±20–30 %). Treat them as
  a starting point and correct them, or swap items for database foods, before logging.

How it works: `XaiFoodPhotoEstimator` posts the image as a base64 `image_url` data URL to
`https://api.x.ai/v1/chat/completions` with a strict JSON schema (`response_format: json_schema`); the reply
(`items[]` with name, grams, kcal, protein, carbs, fat, confidence, plus `notes`) is validated and range-checked
before the review screen shows it.

## Training

The **Training** tab (bottom bar, next to Weight) has three parts: **Workout** (start or resume, your routines),
**History** (finished workouts) and **Exercises** (the library and per-exercise progress).

**Exercise library.** 113 common exercises bundled with the app: barbell, dumbbell, cable, machine and bodyweight
lifts plus cardio, each with primary and secondary muscles, equipment and type (compound, isolation or cardio).
The list was written for Open Macros, not copied from a dataset. Add your own with **Custom exercise**; custom
ones can be edited or deleted (deleting removes it from routines; logged sets keep the name).

**Routines.** A routine is a list of days (e.g. "Upper A"), each an ordered list of exercises with target sets,
a rep range, rest time and notes. Create, edit, reorder (days, exercises and routines), duplicate and delete them,
and star one as **active**. "Start next" follows the active routine's days in order, picking the day after the
one you trained last, so a 4-day routine fits any 4 days of your week.

**Split recommender** (Workout → Recommend). Pick days per week (2–6, default 4), goal, experience and equipment
(full gym, dumbbells only, or bodyweight). You get two or three templates, the best fit first, each with a short
"why" and an estimate of weekly hard sets per muscle group:

| Days | Options (first = recommended) |
|---|---|
| 2 | Full Body A/B · Upper/Lower |
| 3 | Full Body A/B/C · Upper/Lower/Full · Push/Pull/Legs |
| 4 | Upper/Lower ×2 · Push/Pull/Legs + Full Body · Full Body ×4 |
| 5 | Upper/Lower + Push/Pull/Legs · Push/Pull/Legs + Upper/Lower |
| 6 | Push/Pull/Legs ×2 · Upper/Lower ×3 (recommended first for beginners) |

The goal sets reps, sets and rest (main lift / secondary / isolation):

| Goal | Main lifts | Secondary | Isolation | Notes |
|---|---|---|---|---|
| Muscle gain (bulk) | 3 × 6–10, 150 s | 3 × 8–12 | 3 × 10–15 | ~10–20 hard sets per muscle a week |
| Fat loss (cut) | 3 × 5–8, 150 s | 3 × 8–12 | 2 × 10–15 | stay heavy, slightly less volume |
| Maintain / recomp | 3 × 6–10, 120 s | 3 × 8–12 | 2 × 10–15 | moderate |
| Strength | 4 × 3–5, 180 s | 3 × 6–8 | 3 × 8–12 | conventional deadlift as a main lift |

Beginners get at most 3 sets and fewer exercises; advanced lifters get an extra set on main and secondary lifts.
Bodyweight plans use higher rep ranges. **Create this routine** saves an ordinary routine you can edit.
When you pick a goal and have a nutrition profile, the screen **offers** (never forces) a matching nutrition goal:
cut → lose about 0.75 % of body weight a week; bulk → gain 0.25–0.5 % a week depending on experience; recomp →
maintain; strength → no change.

**Logging a workout.** Start the next routine day, any other day, or an empty workout. Each exercise is pre-filled
from last time (warm-ups and working sets, reps and weight; nothing is ticked). For every set enter weight and
reps, tap the set number to switch between **warm-up (W)** and **working**, and tick it when done, which starts the
**rest timer** (−15 s / +15 s / skip; the phone buzzes when rest is over, while the app is open). Weights are entered
in your **training unit** (Settings → Units, separate from the body-weight unit), and each exercise has an
lb ⇄ kg switch. Weights are stored in kg, with each set's entered unit kept for display, so switching units never
changes the stored value. Add workout notes, then **Finish**: ticked sets are saved; unticked or empty ones are dropped.
Cardio is logged as minutes. The screen stays on during a workout.

**Progress.** Each exercise has a chart of estimated 1RM per session (Epley: weight × (1 + reps / 30); best reps for
unloaded bodyweight moves, minutes for cardio), PRs (best e1RM, heaviest set, most reps, best session volume, all from
working sets only) and its full history. **Double progression** suggestions show on each exercise during a workout:
if every working set last time reached the top of the rep range, add **5 lb / 2.5 kg** (compounds) or **2.5 lb / 1 kg**
(isolations) and restart at the bottom; otherwise keep the weight and add reps; if every set fell short of the bottom
of the range two sessions running, drop about 10 %. **Apply** fills the suggestion into the unticked sets.

**Backups.** The JSON backup includes the exercise library, routines and workouts (older backups without training
data import fine and leave training data alone). Settings → Your data → **Export training sets (CSV)** writes one row
per set: `date, workout, exercise, set, set_type, reps, weight, unit, weight_kg, e1rm_kg, bodyweight`. For bodyweight
sets `weight` is the added load (negative = assistance, blank = plain BW), `bodyweight` is `yes` and `e1rm_kg` is blank.

**Bodyweight exercises.** Exercises tagged bodyweight (pull-up, chin-up, Body Dips, push-up, inverted row, assisted
pull-up, …) log **BW** by default: leave the weight blank for plain bodyweight, enter `25` for +25 lb on a belt or vest,
or `-40` for 40 lb of assistance. Sets read `BW × 10`, `BW + 25 lb × 8` or `BW − 40 lb × 6`. For e1RM, heaviest set,
volume and PRs the app uses your latest weigh-in on or before that day plus the added load, and labels those numbers
"incl. body weight". With no weigh-ins it can't know the load, so it tracks rep-based PRs (most reps) only. Progression
suggestions add the increment as extra load (or remove that much assistance). Upgrading migrates the database to v3:
existing 0-lb sets on bodyweight exercises become BW.

References behind the recommender: Schoenfeld, Ogborn & Krieger 2016 (training frequency, *Sports Med*
46:1689); Schoenfeld, Ogborn & Krieger 2017 (weekly volume dose-response, *J Sports Sci* 35:1073); Schoenfeld et al.
2017 (low vs high loads, *J Strength Cond Res* 31:3508); Schoenfeld et al. 2016 (longer rest periods, *J Strength
Cond Res* 30:1805); Helms, Aragon & Fitschen 2014 (contest prep, 0.5–1 % a week loss, *JISSN* 11:20); Iraki et al.
2019 (off-season gain rate, *Sports* 7:154). These are general guidelines, not personal coaching or medical advice.

## Signing: CI builds install over each other

CI signs debug APKs with a **stable debug key** kept in GitHub Actions secrets, so each new CI build installs
as an update over the last one (no uninstall, data kept). The keystore itself is never in the repo.

- Secrets (Settings → Secrets and variables → Actions): `SIGNING_KEYSTORE_B64` (base64 of the keystore),
  `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
- The workflow decodes the keystore to `$RUNNER_TEMP`, sets `SIGNING_KEYSTORE_PATH`, passes the passwords as
  env vars to Gradle, and deletes the file afterwards. The workflow source is in [`ci/android.yml`](ci/android.yml)
  (copy it to `.github/workflows/android.yml`).
- `app/build.gradle.kts` uses the stable key for the `debug` build type only when the keystore path and all
  three values are present (env vars, or `signing.storeFile` / `signing.storePassword` / `signing.keyAlias` /
  `signing.keyPassword` in your gitignored `local.properties`). Otherwise it uses the normal Android debug
  key, so local builds and **pull requests from forks (which get no secrets) still build**. The build log says
  which key was used.
- CI sets `versionCode = 100 + GITHUB_RUN_NUMBER`, so every CI build has a higher version code. Local builds
  keep the low default and won't install over a CI build without uninstalling (signatures differ anyway).

**Switching to the stable key:** APKs signed with the old default debug key can't be updated by ones signed with
the new key. Uninstall the app **one last time** (export a backup first if the app has data you want), then
install the new CI build. From then on, CI builds update in place.

## Data sources and attribution

- **Open Food Facts**: product data © Open Food Facts contributors, available under the
  [Open Database License (ODbL 1.0)](https://opendatacommons.org/licenses/odbl/1-0/); individual contents
  under the [Database Contents License](https://opendatacommons.org/licenses/dbcl/1-0/). Images are not used.
  The app credits OFF on the search, scan and food screens and in Settings → About. Open Macros caches
  products you open on your device; if you redistribute data derived from OFF, ODbL's share-alike terms apply.
- **USDA FoodData Central**: U.S. Department of Agriculture, Agricultural Research Service.
  FoodData Central data is in the public domain (CC0 1.0).

Crowd-sourced nutrition data can be incomplete or wrong. Check the label when it matters.
Calorie targets are estimates from a standard formula, not medical advice.

## Roadmap

- Adaptive TDEE from your intake vs. weight trend, recipes, Health Connect sync.
- Training: a rest-timer notification when the app is in the background, supersets, a plate calculator.

## License

Open Macros' own code is under the [PolyForm Noncommercial License 1.0.0](LICENSE)
(`Required Notice: Copyright (c) 2026 Bradyn Glines`). Free for personal and other noncommercial use.
Third-party components and data keep their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
