/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';

export default defineConfig(({ mode }) => ({
  plugins: [
    angular(),
  ],
  test: {
    globals: true,
    setupFiles: ['src/test-setup.ts'],
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
    reporters: ['default'],
    server: {
      deps: {
        inline: ['@analogjs/vite-plugin-angular', '@angular/core/testing', '@angular/platform-browser-dynamic/testing']
      }
    }
  },
  define: {
    'import.meta.vitest': mode !== 'production',
  },
}));
