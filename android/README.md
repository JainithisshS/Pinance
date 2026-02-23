# Android Client – Agentic Finance System

This folder documents the design of the Android app that talks to the FastAPI backend.

## Goals

- Read bank/UPI notifications on the device.
- Send the raw notification text to the backend `/api/parse_message` endpoint.
- Optionally display parsed transaction info and insights (categories, spending summary).

## High-Level Architecture

- **UI Layer (MainActivity)**
  - Simple screen where user can paste an SMS and tap a button.
  - Calls backend `/api/parse_message` and shows amount, merchant, and category.

- **Networking Layer (Retrofit + Coroutines)**
  - `FinanceApi` interface with:
    - `POST /api/parse_message` → maps to `ParseMessageRequestDto` and `TransactionDto`.
  - `ApiClient` singleton with `Retrofit` configured to point to:
    - `http://10.0.2.2:8000` when running on emulator (backend on the same PC).

- **Notification Listener (NotificationListenerService)**
  - Listens to system notifications.
  - Extracts `title` and `text` from bank/UPI notifications.
  - Concatenates into one `raw_message` and calls the same `/api/parse_message` endpoint in the background.

## How It Connects to the Backend

Backend endpoints used by the Android app:

- `POST /api/parse_message`
  - Input: `{ "raw_message": "INR 500.00 spent at Swiggy on your card" }`.
  - Output: structured transaction with `amount`, `merchant`, `category`, and `id`.

In a full app, additional screens can call:

- `POST /api/analyze_finance` – show monthly spending summary and risk level.
- `POST /api/analyze_news` – show sentiment about a stock/sector.
- `POST /api/synthesize` – show combined recommendation.

## Steps to Implement in Android Studio (Summary)

1. **Create Project**
   - New Android Studio project → Empty Activity → Kotlin.

2. **Add Permissions**
   - In `AndroidManifest.xml`:
     - `INTERNET` for backend calls.
     - `POST_NOTIFICATIONS` for Android 13+.
     - `BIND_NOTIFICATION_LISTENER_SERVICE` for notification access.

3. **Add Retrofit + Coroutines Dependencies**
   - In `app/build.gradle` add `retrofit`, `converter-gson`, and `kotlinx-coroutines-android`.

4. **Implement Networking Layer**
   - Create DTOs: `ParseMessageRequestDto`, `TransactionDto`.
   - Create `FinanceApi` and `ApiClient`.

5. **Implement MainActivity**
   - Layout: EditText (SMS input), Button (send), TextView (result).
   - Use `lifecycleScope.launch { ... }` to call `ApiClient.api.parseMessage(...)`.

6. **Implement NotificationListenerService (Optional but Recommended)**
   - Implement `NotificationListener` that extends `NotificationListenerService`.
   - Override `onNotificationPosted` to build `raw_message` and call backend.
   - Register the service in `AndroidManifest.xml` and enable notification access in system settings.

> Note: The actual Kotlin source files should be created inside an Android Studio project. This folder exists to document the Android part of the system for your Software Engineering submission and to show how it integrates with the backend.
