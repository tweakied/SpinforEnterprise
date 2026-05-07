# Spin for Enterprise

A spin-the-wheel Android app with an admin dashboard.

## Project Structure

```
SpinforEnterprise/
├── android/          # Android app (Java, Gradle)
├── dashboard/        # Web admin dashboard (static HTML/CSS/JS)
├── backend/          # FastAPI backend API
├── message/          # Consent dialog text (customizable)
└── README.md
```

## Android App

### Features
- Animated splash screen with rainbow Apple logo → "Spin for Enterprise" reveal
- iOS 26-style liquid glass bottom navigation bar (Spin / Watch Ads / Tasks)
- Spin roulette with 3.5% win chance (X icons + 1 apple icon)
- 1 spin per hour, max 3 wins per day
- Wins trigger Telegram bot to send a unique app link
- Collects device info (brand, model, battery) with user consent

### Build
Requires JDK 17 and Android SDK.
```bash
cd android
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

## Web Dashboard

### Features
- Admin login (credentials set via environment variables)
- User analytics: IP, country, city, postal code, device info, GPS (if consented)
- Ban / unban users
- Add app links (one-time claim per link)
- Add tasks
- View conversations

### Setup
The dashboard is a static site that talks to the backend API. Host it on any static host (GitHub Pages, Netlify, etc.) or open `dashboard/index.html` directly.

## Backend API

### Setup
```bash
cd backend
pip install -e .
uvicorn main:app --host 0.0.0.0 --port 8000
```

### Environment Variables
| Variable | Description | Default |
|----------|-------------|---------|
| `ADMIN_USERNAME` | Dashboard login username | `FruckRajeeto` |
| `ADMIN_PASSWORD` | Dashboard login password | `Mothero` |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token from @BotFather | (empty) |
| `TELEGRAM_CHAT_ID` | Telegram chat ID for reward messages | (empty) |

## Consent & Privacy
The app shows a clear consent dialog before collecting any location data. The consent message text is stored in `message/consent.txt` and can be customized. Users can deny the permission and still use the app fully.
