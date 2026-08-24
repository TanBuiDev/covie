import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: { "@": new URL("./", import.meta.url).pathname },
  },
  plugins: [react()],
  test: {
    environment: "jsdom",
    exclude: ["e2e/**", "node_modules/**", "dist/**", ".next/**"],
    setupFiles: ["./vitest.setup.ts"],
  },
});
