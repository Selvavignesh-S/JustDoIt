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
