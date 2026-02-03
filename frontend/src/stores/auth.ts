import { defineStore } from 'pinia'
import { fetchMenus, login as loginApi, type MenuRoute, type LoginResponse } from '../api/auth'
import { clearAuthToken, getPersistLogin, setAuthToken, setPersistLogin } from '../auth/session'
import { getMyPerms } from '../api/user'

type UserInfo = LoginResponse['user']

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: '',
    persistLogin: getPersistLogin(),
    user: (getPersistLogin() && localStorage.getItem('user')
      ? (JSON.parse(localStorage.getItem('user') as string) as UserInfo)
      : null) as UserInfo | null,
    menus: [] as MenuRoute[],
    menusLoaded: false
    ,
    perms: [] as string[],
    permsLoaded: false
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    hasPerm: (state) => (perm: string) => state.perms.includes(perm)
  },
  actions: {
    initFromStorage() {
      // Migration/guard: only auto-login if user explicitly enabled persistLogin.
      if (!this.persistLogin) {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        this.token = ''
        clearAuthToken()
        return
      }

      const token = localStorage.getItem('token') ?? ''
      this.token = token
      setAuthToken(token)
      const userRaw = localStorage.getItem('user')
      this.user = userRaw ? (JSON.parse(userRaw) as UserInfo) : null
    },

    async login(username: string, password: string, options?: { persistLogin?: boolean }) {
      const data = await loginApi({ username, password })
      this.token = data.token
      this.user = data.user
      setAuthToken(data.token)

      const persist = Boolean(options?.persistLogin)
      this.persistLogin = persist
      setPersistLogin(persist)

      if (persist) {
        localStorage.setItem('token', data.token)
        localStorage.setItem('user', JSON.stringify(data.user))
      } else {
        // Ensure a previous persistent session doesn't silently auto-login again.
        localStorage.removeItem('token')
        localStorage.removeItem('user')
      }

      // Load authorities for client-side gating (buttons, etc.)
      await this.loadPerms()
    },
    async loadMenus() {
      const menus = await fetchMenus()
      this.menus = menus
      this.menusLoaded = true
      return menus
    },
    async loadPerms() {
      const perms = await getMyPerms()
      this.perms = perms
      this.permsLoaded = true
      return perms
    },
    logout() {
      this.token = ''
      this.user = null
      this.menus = []
      this.menusLoaded = false
      this.perms = []
      this.permsLoaded = false
      this.persistLogin = false
      setPersistLogin(false)
      clearAuthToken()
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
