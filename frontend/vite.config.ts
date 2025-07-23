import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ command }) => ({
  // server: {
  //   proxy: {
  //     '/api': 'http://localhost:8123'
  //   }
  // },
  // server: {
  //   host: 'localhost',
  //   // 代理
  //   proxy: {
  //     // 改为你的图片存储 url 前缀
  //     '/yu_picture': {
  //       // 改为你的对象存储域名
  //       target: '',
  //       changeOrigin: true,
  //     }
  //   },
  // },

  plugins: [
    vue(),
    command === 'serve' && vueDevTools(),
  ].filter(Boolean),
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
}))
