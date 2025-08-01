import { useStorage } from '@vueuse/core'
import { defineStore } from 'pinia'
import { getLoginUserUsingGet } from '@/api/userController.ts'

/**
 * 存储登录用户信息的状态
 */
export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = useStorage<API.LoginUserVO>('loginUser', {
    userName: '未登录',
  })
  // 标记是否已获取过登录用户

  /**
   * 远程获取登录用户信息
   */
  async function fetchLoginUser() {
    const res = await getLoginUserUsingGet()
    if (res.data.code === 0 && res.data.data) {
      loginUser.value = res.data.data
    } else {
      // 若未登录或获取失败，重置登录状态，避免读取过期的本地缓存信息
      loginUser.value = { userName: '未登录' } as API.LoginUserVO
    }
    // 测试用户登录，3 秒后自动登录
    // setTimeout(() => {
    //   loginUser.value = { userName: '测试用户', id: 1 }
    // }, 3000)
  }

  /**
   * 设置登录用户
   * @param newLoginUser
   */
  function setLoginUser(newLoginUser: any) {
    loginUser.value = newLoginUser
  }

  // 返回
  return { loginUser, fetchLoginUser, setLoginUser }
})
