# BIGsta Native Android Studio

BIGsta is a native Android application built with Kotlin, Jetpack Compose (Material Design 3), Room, WorkManager, DataStore, and OkHttp, connected directly to the BIGsta Supabase (`amber-dog`) backend.

## Implemented Native Modules
- **Authentication & Role Verification**: Email/password sign-in, registration, password reset, and server-verified administrator role gating via protected `user_roles`.
- **Dashboard & Requests**: Token balance overview, weekly offers carousel, live backend connectivity monitor, and searchable NIDA / Driving Licence / NHIF / NRC Zambia request tracking.
- **ID Card Studio & Government Forms**: Front/back independent template side selection (`document_category_id`, `template_card_id`, `side`), Photo Picker (`PickVisualMedia`) uploads, PNG export, and system share integration.
- **PDF & Image Studio**: Native `PdfRenderer` document viewer, `PrintManager` PDF printing, local downloads history in Room, and on-device image crop, rotate, JPEG compression, and watermark export.
- **Tokens & Billing**: Live token packages, mobile money Lipa Namba instructions, and server-awaited `payment_requests` submissions (never locally crediting tokens before server verification).
- **Transaction SMS Intake (Play-Compliant)**: Explicit user consent and Android Share Sheet (`ACTION_SEND` `text/plain`) intake for M-Pesa, Tigo Pesa, Airtel Money, HaloPesa, NMB, and CRDB confirmations, with strict OTP/PIN filtering, Room duplicate blocking, and WorkManager background sync.
- **Notifications & Admin Console**: Categorized Android notification channels with Room deduplication and a role-verified Administrator Console.

## Environment Secrets Setup
Configure the following keys in the **Secrets panel in AI Studio** (or `.env` locally via Secrets Gradle Plugin; never hardcode in source):
- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`
- `SUPABASE_PROJECT_ID`
- `BIGSTA_API_BASE_URL` (optional Express/API host for `/api/*` routes)

## Build & Test Commands
- Debug APK: `gradle :app:assembleDebug`
- Unit & Robolectric Tests: `gradle :app:testDebugUnitTest`
- Release App Bundle: `gradle :app:bundleRelease`
