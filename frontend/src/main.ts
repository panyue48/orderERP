import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { useAuthStore } from './stores/auth'

// Element Plus 的“服务式 API”样式（MessageBox/Message/Notification/Loading）。
// 这些不是模板组件，unplugin-vue-components 不会自动引入样式，所以这里手动引入。
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia).use(router)

// 初始化登录态（同时避免历史 localStorage token 导致“误自动登录”）。
useAuthStore(pinia).initFromStorage()

app.mount('#app')
