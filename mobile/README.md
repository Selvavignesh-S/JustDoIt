# JustDoIt mobile app

React Native (Expo SDK 58, TypeScript, Expo Router). Talks to the live backend at
`https://justdoit-im8a.onrender.com` by default.

## Run it on your Android phone
1. Install **Expo Go** from the Play Store.
2. On this computer:
   ```bash
   cd mobile
   npm install        # first time only
   npx expo start
   ```
3. Scan the QR code in the terminal with Expo Go. Phone and computer must be on the same Wi-Fi;
   if they aren't, use `npx expo start --tunnel`.

The first request after the server has been idle can take about a minute ("Waking up the server").

## Point it at a local backend
Create `mobile/.env.local` (gitignored) with `EXPO_PUBLIC_API_URL=http://<this computer's LAN IP>:8080`,
then restart `npx expo start`. Delete the file to go back to the live server.

## Checks
```bash
npm run typecheck
npm run lint
npm test
```

## Reminder notifications
The server pushes reminders (same rules as the Tasks tab: overdue, Due tomorrow, High, Medium due within
3 days) through Expo's push service, once each time a task becomes more urgent. **Expo Go on Android can't
receive them**: use the installed app (APK) below. Settings → "Reminder notifications" shows the state.

## Build an installable APK (EAS)
One-time setup, done by you (it needs your own accounts):
1. Expo account: `npx eas-cli@latest login`, then in `mobile/`: `npx eas-cli@latest init`
   (adds the project id to `app.json`; commit that change).
2. Firebase (delivers Android pushes): at console.firebase.google.com create a project, add an
   **Android app** with package name `com.justdoit.app`, download `google-services.json` into `mobile/` and
   commit it (it holds only public identifiers; EAS builds only see committed files).
3. Firebase → Project settings → Service accounts → **Generate new private key**. Upload it to EAS with
   `npx eas-cli@latest credentials` → Android → production → Google Service Account →
   "Manage your Google Service Account Key for Push Notifications (FCM V1)" → "Set up a Google Service
   Account Key for Push Notifications (FCM V1)" → "Upload a new service account key".
   Then delete the key file from your computer: EAS keeps it.

Each build:
```bash
npx eas-cli@latest build --platform android --profile preview
```
EAS prints a link and QR code to the APK; open it on the phone and install (allow "install unknown apps").
