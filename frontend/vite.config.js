import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const frontendPort = process.env.FRONTEND_PORT 
  ? parseInt(process.env.FRONTEND_PORT, 10) 
  : (process.env.PORT ? parseInt(process.env.PORT, 10) : 5173);

const backendPort = process.env.BACKEND_PORT || process.env.PORT || '19092';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: frontendPort,
    proxy: {
      '/v1': {
        target: `http://127.0.0.1:${backendPort}`,
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
