import { defineConfig } from "vitest/config";
import path from "path";

// Unit tests for the shared logic (src/lib, src/hooks). Run with `npm test`.
export default defineConfig({
  test: {
    environment: "happy-dom",
    include: ["src/**/*.test.{ts,tsx}"],
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
