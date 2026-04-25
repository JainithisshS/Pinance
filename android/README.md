# Android Client

This folder documents the Android side of the Agentic Finance System.

The app is designed to capture bank and UPI notifications, forward the raw text to the backend, and display parsed transaction details plus downstream insights.

## Core Flow

- `NotificationListenerService` captures notification text on-device.
- Retrofit sends the raw message to the backend parse endpoint.
- The backend returns structured transaction data for UI display.
- Optional screens can call finance analysis, news analysis, and synthesis endpoints.

## Backend Integration

- `POST /api/parse_message` parses SMS or notification text into a transaction record.
- `POST /api/analyze_finance` returns spending and risk analysis.
- `POST /api/analyze_news` returns market sentiment analysis.
- `POST /api/synthesize` returns grounded recommendations.

## Implementation Notes

- Use Kotlin + Retrofit + Coroutines.
- Add `INTERNET`, `POST_NOTIFICATIONS`, and `BIND_NOTIFICATION_LISTENER_SERVICE` permissions.
- Point the emulator to `http://10.0.2.2:8000` for local backend testing.

## Status

This folder is documentation-first. The root project README in [../README.md](../README.md) is the main entry point for setup, architecture, and repository layout.
