import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// SPA собирается в dist/, откуда Gradle кладёт её в static/ jar-а.
// В dev-режиме (`npm run dev`, :5173) /api проксируется на бэкенд (:8080),
// чтобы session- и CSRF-cookie работали как при одном origin.
export default defineConfig({
  plugins: [react()],
  base: '/',
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
});
