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
        // 使用 IPv4 回环地址，避免 Windows/Node 把 localhost 解析为 IPv6（::1）导致偶发连接失败。
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      // 后端提供的静态上传文件（img 标签不会携带 Authorization header，所以单独走静态资源路径）。
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
