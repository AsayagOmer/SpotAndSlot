import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";
import path from "path";
import { componentTagger } from "lovable-tagger";
import { VitePWA } from "vite-plugin-pwa";

// Mobile (end-user) app: served in dev on :8080, built to dist/mobile, and
// wrapped by Capacitor into the Android app (see capacitor.config.ts).
export default defineConfig(({ mode }) => ({
  root: path.resolve(__dirname, "mobile"),
  publicDir: path.resolve(__dirname, "public"),
  build: {
    outDir: path.resolve(__dirname, "dist/mobile"),
    emptyOutDir: true,
  },
  server: {
    host: "::",
    port: 8080,
    // Proxy ML-service calls so the browser reaches it same-origin (no CORS).
    // The native (Capacitor) build calls the ML service directly instead.
    proxy: {
      "/ml": {
        target: "http://localhost:5000",
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/ml/, ""),
      },
    },
  },
  plugins: [
    react(),
    mode === "development" && componentTagger(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico'],
      manifest: {
        name: 'Spot&Slot',
        short_name: 'Spot&Slot',
        description: 'Smart Parking Management System',
        theme_color: '#ffffff',
        display: 'standalone',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png'
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png'
          }
        ]
      }
    })
  ].filter(Boolean),
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
}));
