<template>
  <div id="userInfoPage">
    <a-form name="userInfo" layout="vertical" :model="form" @finish="handleSubmit">
      <a-form-item name="userName" label="用户名">
        <a-input v-model:value="form.userName" placeholder="请输入用户名" allow-clear />
      </a-form-item>
      <a-form-item name="userProfile" label="简介">
        <a-textarea
          v-model:value="form.userProfile"
          placeholder="请输入简介"
          :auto-size="{ minRows: 2, maxRows: 5 }"
          allow-clear
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">提交</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUserVoByIdUsingGet, editUserUsingPost } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const route = useRoute()

const form = reactive<API.UserUpdateRequest>({})

const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser

const router = useRouter()

const handleSubmit = async () => {
  const id = Number(loginUser.id)
  const res = await editUserUsingPost({ id, ...form })
  if (res.data.code === 0 && res.data.data) {
    await loginUserStore.fetchLoginUser()
    message.success('更新成功')
    // 跳转到主页
    router.push({
      path: `/`
    })
  } else {
    message.error('更新失败' + res.data.message)
  }
}

const fetchData = async () => {
  const id = Number(route.params.id)
  if (!id) return
  const res = await getUserVoByIdUsingGet({ id })
  if (res.data.code === 0 && res.data.data) {
    Object.assign(form, res.data.data)
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#userInfoPage {
  max-width: 720px;
  margin: 0 auto;
}
</style>
