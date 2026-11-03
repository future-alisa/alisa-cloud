import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    plugins: [react()],
    server: {
      proxy: {
        "/api": {
          target: env.ALISA_API_TARGET || "http://localhost:8085",
          changeOrigin: true,
        },
      },
    },
    preview: {
      proxy: {
        "/api": {
          target: env.ALISA_API_TARGET || "http://localhost:8085",
          changeOrigin: true,
        },
      },
    },
  };
});
