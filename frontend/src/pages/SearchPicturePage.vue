<template>
  <div id="searchPicturePage">
    <h2 style="margin-bottom: 16px">以图搜图</h2>
    <h3 style="margin-bottom: 16px">原图</h3>
    <!-- 单张图片 -->
    <a-card hoverable style="width: 240px">
      <template #cover>
        <img
          style="height: 180px; object-fit: cover"
          :alt="picture.name"
          :src="picture.thumbnailUrl ?? picture.url"
          loading="lazy"
        />
      </template>
    </a-card>
    <h3 style="margin: 16px 0">识图结果</h3>
    <!-- 图片结果列表 -->
    <a-list
      :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4, xl: 5, xxl: 6 }"
      :data-source="dataList"
      :loading="loading"
    >
      <template #renderItem="{ item: picture }">
        <a-list-item style="padding: 0">
          <a :href="picture.fromUrl" target="_blank">
            <!-- 单张图片 -->
            <a-card hoverable>
              <template #cover>
                <img
                  style="height: 180px; object-fit: cover"
                  :alt="picture.name"
                  :src="picture.thumbUrl"
                  loading="lazy"
                />
              </template>
            </a-card>
          </a>
        </a-list-item>
      </template>
    </a-list>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getPictureVoByIdUsingGet, searchPictureByPictureUsingPost } from '@/api/pictureController.ts'
import { useRoute } from 'vue-router'

const route = useRoute()

const picture = ref<API.PictureVO>({})
const pictureId = computed(() => {
  return route.query?.pictureId
})
const loading = ref<boolean>(true)

// 获取图片详情
const fetchPictureDetail = async () => {
  try {
    const res = await getPictureVoByIdUsingGet({
      id: pictureId.value
    })
    if (res.data.code === 0 && res.data.data) {
      picture.value = res.data.data
    } else {
      message.error('获取图片详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取图片详情失败：' + e.message)
  }
}

// 以图搜图结果
const dataList = ref<API.ImageSearchResult[]>([])

// 获取搜图结果
const fetchResultData = async () => {
  try {
    loading.value = true
    const res = await searchPictureByPictureUsingPost({
      pictureId: pictureId.value
    })
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data ?? []
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取数据失败：' + e.message)
  }
  loading.value = false
}

onMounted(() => {
  fetchPictureDetail()
  fetchResultData()
})

</script>

<style scoped>
#searchPicturePage {
  margin-bottom: 16px;
}

</style>
