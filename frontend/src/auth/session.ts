let memoryToken = ''

export function setAuthToken(token: string) {
  memoryToken = token || ''
}

export function clearAuthToken() {
  memoryToken = ''
}

export function getAuthToken() {
  return memoryToken || localStorage.getItem('token') || ''
}

export function getPersistLogin() {
  return localStorage.getItem('persistLogin') === 'true'
}

export function setPersistLogin(enabled: boolean) {
  localStorage.setItem('persistLogin', enabled ? 'true' : 'false')
}

