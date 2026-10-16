# Architecture

Single-module Android app (`:app`), Kotlin, Jetpack Compose + Material 3. Package `dev.glinesb.openmacros`.
Small enough that it uses a hand-rolled container instead of a DI framework, and screens read flows
from repositories directly (one ViewModel, for search, where results must survive navigation).

```
domain/         Pure Kotlin, no Android: unit-tested
  Tdee.kt         Sex, ActivityLevel, GoalType, Profile, Tdee (Mifflin-St Jeor, TDEE, target), MacroSplit
  Trend.kt        Trailing moving average + time-aware exponential trend for weight
  Nutrition.kt    Macros, Meal, FoodSource, FoodItem (normalised to one serving), AmountUnit, Portions
  FoodMerge.kt    Cross-source de-duplication (barcode / name+brand) and relevance ranking
  Units.kt        lb/kg, cm/ft-in, g/oz
data/
  db/             Room: FoodEntity, LogEntryEntity, WeightEntity, DAOs, AppDatabase (schema in app/schemas)
  remote/         OkHttp + kotlinx.serialization: OpenFoodFactsApi/Parser, UsdaApi/Parser, Http (User-Agent)
  backup/         BackupCodec (pure JSON/CSV, tested) + BackupManager (SAF I/O, replace-on-import)
  Settings.kt     SettingsStore (SharedPreferences → StateFlow<AppSettings>), SecureKeys (encrypted USDA key)
  Repositories.kt FoodRepository (search/merge/cache/barcode), LogRepository, WeightRepository
photo/
  FoodPhotoEstimator.kt     Interface + result types
  XaiFoodPhotoEstimator.kt  xAI chat completions client (user's key, error mapping)
  PhotoEstimateParser.kt    Pure: request JSON (prompt + schema), response validation (tested)
  PhotoReview.kt            Pure: editable ReviewItem, proportional scaling, database swap (tested)
  ImagePrep.kt              ImageDecoder downscale to 1024 px + JPEG; ImageSizing (tested)
ui/
  Root.kt         Navigation graph + bottom bar (Diary, Weight, Settings)
  today/          Daily diary, calorie ring, macro bars, date navigation
  food/           Search (Recent / Favorites / My foods tabs), food detail, quick add, custom food editor
  scan/           CameraX preview + ML Kit analyzer, manual barcode entry
  photo/          Photo log: capture (CameraX ImageCapture / photo picker) → analyzing → review → log
  weight/         Trend chart, log form, history
  goals/          Onboarding / profile & goal form (live BMR/TDEE/target preview)
  settings/       Targets, macro split, units, USDA key, export/import, about/attribution
  components/     Charts (Canvas ring, bars, weight chart), number field, formatting
  theme/          System light/dark + dynamic color (Android 12+), fixed per-theme macro colours
OpenMacrosApp.kt  Application + AppContainer
MainActivity.kt   enableEdgeToEdge() + Compose root
```

## Data model

- **foods**: anything the user has opened, scanned, favourited or created. Remote hits are cached the first
  time they're opened (`FoodRepository.cache`, unique on `source + sourceId`), which is what "Recent" is
  built from (`lastUsedAt`, `useCount` bumped when logged). Nutrition is stored **per serving** with an
  optional `servingGrams`; when grams are known the UI offers g and oz as well as servings.
- **log_entries**: a diary line for a day (`epochDay`) and meal. Totals are **snapshotted** at log time, so
  editing or deleting a food never rewrites history (deleting a food just clears `foodId`). Quick adds have
  no food.
- **weights**: one per day (unique `epochDay`, replace on conflict), stored in kg.
- **Settings** (profile, macro split, optional calorie override, units) live in SharedPreferences and are
  exposed as a `StateFlow<AppSettings>`. The calorie target is derived, not stored.
- **SecureKeys**: the USDA key in `EncryptedSharedPreferences` (AES-256-GCM, Keystore master key). If the
  keystore fails, saving is disabled rather than falling back to plaintext.

## Food search flow

1. User submits a query (no search-as-you-type, to respect OFF's rate limits).
2. In parallel: Open Food Facts search (`search.openfoodfacts.org`, falling back to legacy `/cgi/search.pl`)
   and USDA `/foods/search` (user key or `DEMO_KEY`); local foods are queried too.
3. Both are parsed into `FoodItem`s normalised to a serving (USDA branded `servingSize`, OFF
   `serving_quantity`, else 100 g). Energy prefers kcal; falls back to Atwater energy (USDA) or kJ ÷ 4.184.
4. `FoodMerge.merge` interleaves sources, drops duplicates (barcodes compared without leading zeros, so
   UPC-A/EAN-13/GTIN-14 match), and stable-sorts by a simple text relevance score. Local foods stay on top.
5. Errors per source are shown inline (e.g. DEMO_KEY rate limit) without hiding the other source's results.

## Barcode flow

CameraX `Preview` + `ImageAnalysis` (keep-only-latest) → ML Kit `BarcodeScanning` restricted to
EAN-13/EAN-8/UPC-A/UPC-E → `FoodRepository.lookupBarcode`: local foods with that barcode first (so custom
foods you created for unknown products are found next time), then OFF `/api/v2/product/{code}`. Not found →
custom-food editor with the barcode filled in.

## Goal math

`Tdee.bmr` = 10·kg + 6.25·cm − 5·age + 5 (male) / − 161 (female). `tdee` = BMR × activity multiplier
(1.2 / 1.375 / 1.55 / 1.725 / 1.9). Target = TDEE ± rate(kg/week) × 7700 / 7, rounded, floored at 1200.
`MacroSplit.grams` converts % of calories to grams at 4/4/9 kcal per g.

## Weight trend

`Trend.exponentialTrend` (α = 0.1 per day) is the chart line. For a gap of g days it uses
α_eff = 1 − (1 − α)^g, so sparse weigh-ins behave like daily ones at the new value. It's computed over all
history and then clipped to the visible range so the range start isn't biased. `trailingMovingAverage`
(calendar 7-day window) feeds the "7-day avg" stat.

## Edge-to-edge / insets

`enableEdgeToEdge()`. The root `Scaffold` has `contentWindowInsets = WindowInsets(0)` and only owns the
bottom `NavigationBar` (which pads for the system nav bar itself). The `NavHost` applies the root padding and
`consumeWindowInsets`, so each screen's own `Scaffold`/`TopAppBar` handles the status bar, and the nav bar
inset only when the bottom bar is hidden. Forms use `imePadding()`.

## Photo logging (optional, user's xAI key)

1. Entry: camera button on the add-food screen. Without a saved xAI key it shows an explainer that links to
   Settings instead of opening the camera.
2. Capture: CameraX `ImageCapture` to a temp file in `cacheDir` (deleted right after decoding), or the system
   photo picker (no storage permission). Optional free-text hint ("cooked in butter").
3. `ImagePrep` decodes with `ImageDecoder` (applies EXIF rotation, drops metadata), scales so the long edge is
   ≤ 1024 px, re-encodes JPEG q85. The bytes are held only in the ViewModel (so "Try again" can resend).
4. `XaiFoodPhotoEstimator` POSTs to `https://api.x.ai/v1/chat/completions`, model `grok-4.7`
   (text+image → text, structured outputs), `reasoning_effort: low`, `max_completion_tokens: 2000`, image as a
   base64 `image_url` data URL with `detail: high`, and `response_format: json_schema` (strict). If the API
   rejects `response_format`, it retries once without it; the system prompt also demands bare JSON. Chat
   completions (stateless) is used rather than Responses so nothing is stored server-side for retrieval.
5. `PhotoEstimateParser` never trusts the reply: it extracts the first balanced JSON object (fences/prose
   tolerated), drops items without a name or with out-of-range numbers (> 3000 g, > 5000 kcal), clamps
   confidence to 0..1 (reading > 1 as a percentage), recomputes missing kcal from macros, caps at 20 items, and
   turns refusals, truncation (`finish_reason: length`) and empty results into `PhotoEstimateException`s.
   HTTP errors map to friendly messages; xAI reports a bad key as 400 "Incorrect API key", handled like 401.
   The key is never put in messages or logs.
6. Review: each `ReviewItem` has editable grams (macros scale proportionally via `withGrams`) and macros
   (`withMacros`), a confidence badge, include/remove, and "Find in food database" (`FoodRepository.search`
   by name → `swappedFor` keeps the estimated grams and uses the database food's nutrition). Logging writes
   ordinary `LogEntryEntity` rows: linked to the food (grams) when swapped, otherwise `foodId = null` with brand
   "Photo estimate".

## Privacy

No account, no analytics, no backend. Network calls go only to Open Food Facts and api.nal.usda.gov, plus
api.x.ai when (and only when) the user runs a photo estimate with their own key.
`allowBackup="false"` plus data-extraction rules exclude everything from cloud backup and device transfer;
use in-app export instead. Backups never contain API keys.
