import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";
import path from "path";
import { componentTagger } from "lovable-tagger";

// Desktop admin app: served in dev on :8090, built to dist/admin.
export default defineConfig(({ mode }) => ({
  root: path.resolve(__dirname, "admin"),
  publicDir: path.resolve(__dirname, "public"),
  build: {
    outDir: path.resolve(__dirname, "dist/admin"),
    emptyOutDir: true,
  },
  server: {
    host: "::",
    port: 8090,
  },
  plugins: [react(), mode === "development" && componentTagger()].filter(Boolean),
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
}));
