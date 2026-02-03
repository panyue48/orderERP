import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需自动导入：避免整库打入一个超大的 JS chunk
    Components({
      dts: 'src/components.d.ts',
      resolvers: [ElementPlusResolver({ importStyle: 'css' })]
    })
  ],
  server: {
    proxy: {
      '/api': {
        // Use IPv4 loopback to avoid Windows/Node resolving localhost to IPv6 (::1) and intermittently failing.
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      // Static upload files served by backend (img tags won't carry Authorization headers).
      '/uploads': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          // 手动分包：把第三方依赖拆到稳定的 vendor chunks，降低入口 chunk 体积并提升缓存命中率
          if (id.includes('element-plus')) return 'element-plus'
          if (id.includes('vue-router')) return 'vue-router'
          if (id.includes('pinia')) return 'pinia'
          if (id.includes('axios')) return 'axios'
          return 'vendor'
        }
      }
    }
  }
})
