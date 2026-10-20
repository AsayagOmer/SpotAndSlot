import type { CapacitorConfig } from '@capacitor/cli';

// Android wrapper around the mobile (end-user) app. The WebView loads the
// dist/mobile build; API/ML calls go to the server address configured on the
// login screen.
//
// androidScheme MUST be 'http' here: the app talks to a plain-http backend
// (http://<LAN-IP>:8084). With the default 'https' scheme the WebView serves
// the app from https://localhost — a secure context — and then blocks every
// http:// fetch as "mixed content", so login silently fails on a real device
// even though the phone can reach the server. Serving over http://localhost
// makes those requests same-scheme and allowed; cleartext:true keeps the OS
// network layer from blocking the plain-http connection.
const config: CapacitorConfig = {
  appId: 'il.ac.afeka.spotslot',
  appName: 'Spot&Slot',
  webDir: 'dist/mobile',
  server: {
    androidScheme: 'http',
    cleartext: true,
  },
};

export default config;
