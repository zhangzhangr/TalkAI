import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// request interceptor: attach JWT token
request.interceptors.request.use(
  (config) => {
    const auth = useAuthStore()
    if (auth.token) {
      config.headers.Authorization = `Bearer ${auth.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// response interceptor: handle 401
request.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data.code !== 200) {
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data
  },
  (error) => {
    if (error.response?.status === 401) {
      const auth = useAuthStore()
      auth.logout()
      router.replace('/login')
    }
    return Promise.reject(error)
  }
)

export default request
