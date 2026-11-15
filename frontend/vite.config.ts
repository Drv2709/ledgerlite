import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the browser only ever talks to http://localhost:5173.
// /api and /health are proxied to the Spring Boot backend, so the HttpOnly auth cookie and the
// XSRF-TOKEN cookie are same-origin: no CORS configuration and no SameSite problems.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/health': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
