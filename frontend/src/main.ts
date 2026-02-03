import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { useAuthStore } from './stores/auth'

// Element Plus styles for "service" APIs (MessageBox/Message/Notification/Loading).
// These are not auto-included by unplugin-vue-components because they are not template components.
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia).use(router)

// Initialize auth state (and prevent accidental auto-login from legacy localStorage token).
useAuthStore(pinia).initFromStorage()

app.mount('#app')
