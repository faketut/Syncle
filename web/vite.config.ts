import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true,
  },
  build: {
    // Split vendor chunks so the main app bundle stays small. LiveKit's
    // SDK alone is ~400 KB minified; isolating it lets the browser cache
    // it across deploys when only app code changes.
    rollupOptions: {
      output: {
        manualChunks: {
          livekit: ["livekit-client"],
          react: ["react", "react-dom"],
          zustand: ["zustand"],
        },
      },
    },
    chunkSizeWarningLimit: 600,
  },
});
