# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```powershell
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Lint
./gradlew lint

# Clean
./gradlew clean
```

Build targets SDK 34, min SDK 24 (Android 7.0), Java 11, Kotlin 2.2.10, AGP 9.2.0. Dependencies are centralized in `gradle/libs.versions.toml`.

## Backend Setup (VPS)

Node.js + Express API in `backend/`. Single-command deploy:

```bash
bash backend/setup.sh
```

This installs Node 20, PM2, sets up `.env` (auto-generates `JWT_SECRET`), creates upload dirs, and starts the server on port 3000 (or `$PORT`).

Key `.env` variables (see `backend/.env.example`):
- `JWT_SECRET` — generated automatically by setup.sh
- `ADMIN_PASS` — default admin password, change immediately
- `ADMIN_KEY` — used for registering drivers via API (keep secret)

Database: SQLite via `better-sqlite3`, file at `./vi.db`. Auto-migrated on startup by `database.js`.

Dashboard: `https://<host>/dashboard` — Bootstrap 5 SPA, login with admin credentials.

## Architecture

### Android

Single-module Android app (`com.example.vehicleinspection`) for Polish commercial transport drivers. No MVVM — each screen is a self-contained Activity with direct `findViewById`. No DI.

#### Activity Responsibility Map

| Activity | Purpose |
|---|---|
| `LoginActivity` | Username + password login; stores JWT via `ApiConfig.saveLogin()` |
| `MenuActivity` | Feature dashboard; launches all other activities |
| `MainActivity` | Pre-departure inspection: vehicle selection, checklists (ON vs LNG), 5-photo capture, offline submission |
| `OrdersActivity` | Fetches active route events from API; falls back to `SharedPreferences` cache offline |
| `RoadCardActivity` | E-road card: odometer/fuel readings and per-point times (load/unload) |
| `ReceiptsActivity` | Photo + description for receipt documents (for invoicing) |
| `ExpensesActivity` | Fuel, tolls, parking, carwash — amount, description, optional photo |
| `ContactsActivity` | Directory: dispatchers, office, IT, technical service — fetched from API, cached offline |
| `CardsActivity` | Reads `assets/cards.json`, displays payment card PINs |

#### Authentication

`ApiConfig.kt` is the central auth helper:
- `API_BASE = "https://telematics.ithowtozone.com"`
- `saveLogin(ctx, token, driverName, driverId)` — stores JWT + driver info in `vi_prefs` SharedPreferences
- `authHeader(ctx)` — returns `"Bearer <token>"` for all API calls
- `clearAuth(ctx)` — logout

All activities use `ApiConfig.authHeader(this)` as the `Authorization` header. No hardcoded API keys anywhere.

#### Offline-First Data Flow

`OfflineQueue` writes payloads + photos to `context.filesDir/vi_queue/<uuid>/` (each submission: `payload.json`, `meta.json`, `.jpg` files). `UploadWorker` (WorkManager, 15-min periodic, network-constrained, exponential backoff) drains the queue by POSTing to the correct endpoint based on `type` field in payload:
- `type = "expense"` → `POST /api/expenses`
- anything else → `POST /api/inspections`

Worker reads JWT from SharedPreferences — fails (retries) if not logged in.

#### Static Data Assets

- `assets/pojazdy.csv` — 60+ vehicles; columns: registration, brand, fuel (`ON`/`LNG`). Fuel type drives checklist branching in `MainActivity` and vehicle spinner in `ExpensesActivity`.
- `assets/cards.json` — payment cards data for `CardsActivity`.

#### SharedPreferences (`vi_prefs`)

| Key | Value |
|---|---|
| `auth_token` | JWT Bearer token |
| `driver_name` | Driver's display name |
| `driver_id` | Driver's database ID |
| `contacts_cache` | JSON array of contacts (offline fallback) |
| `orders_cache` | Last fetched route events (offline fallback) |

### Backend API Routes

All routes except `/api/auth/login` require `Authorization: Bearer <token>` header.

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/login` | `{username, password}` → `{token, driver_name, driver_id, role}` |
| POST | `/api/auth/register` | Requires `X-Admin-Key` header or admin JWT |
| GET | `/api/inspections` | All inspections (dashboard) |
| POST | `/api/inspections` | Multipart: `payload` + up to 6 photo fields |
| GET | `/api/road-cards` | All road cards (dashboard) |
| POST | `/api/road-cards` | JSON body with odometer, fuel, points array |
| GET | `/api/expenses` | All expenses (dashboard) |
| POST | `/api/expenses` | Multipart: `payload` + optional `photo` |
| GET | `/api/contacts` | Contact directory |
| POST/PUT/DELETE | `/api/contacts` | Contact CRUD (admin) |
| GET | `/api/vehicles` | Vehicle list |
| GET/POST/DELETE | `/api/drivers` | Driver management (admin only) |

Photos stored under `backend/uploads/`, served at `/uploads/<filename>`.

### Dashboard Tabs

Login at `/dashboard` → 6 tabs: Inspekcje, Karty drogowe, Wydatki, Kontakty, Pojazdy, Kierowcy.
