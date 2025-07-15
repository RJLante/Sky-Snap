<template>
  <div id="homePage">
    <!-- 搜索框 -->
    <div class="search-bar">
      <a-input-search
        v-model:value="searchParams.searchText"
        placeholder="搜索"
        enter-button
        size="large"
        @search="doSearch"
      />
    </div>
    <!-- 分类和标签筛选 -->
    <a-tabs v-model:activeKey="selectedCategory" @change="doSearch">
      <a-tab-pane key="all" tab="全部" />
      <a-tab-pane v-for="category in categoryList" :key="category" :tab="category" />
    </a-tabs>
    <div class="tag-bar">
      <a-space :size="[0, 8]" wrap>
        <a-checkable-tag
          v-for="(tag, index) in tagList"
          :key="tag"
          v-model:checked="selectedTagList[index]"
          @change="doSearch"
        >
          {{ tag }}
        </a-checkable-tag>
      </a-space>
    </div>
    <!-- 图片列表 -->
    <a-list :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4, xl: 6, xxl: 6 }" :data-source="dataList"
            :pagination="pagination"
            :loading="loading">
      <template #renderItem="{ item: picture }">
        <a-list-item style="padding: 0">
          <!-- 单张图片 -->
          <a-card hoverable @click="doClickPicture(picture)">
            <template #cover>
              <img :alt="picture.name" :src="picture.url" style="height: 180px; object-fit: cover" />
            </template>
            <a-card-meta :title="picture.name">
              <template #description>
                <a-flex>
                  <a-tag v-if="picture.category" color="green">
                    {{ picture.category }}
                  </a-tag>
                  <a-tag v-for="tag in picture.tags" :key="tag">
                    {{ tag }}
                  </a-tag>
                </a-flex>
              </template>
            </a-card-meta>
          </a-card>
        </a-list-item>
      </template>
    </a-list>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { listPictureTagCategoryUsingGet, listPictureVoByPageUsingPost } from '@/api/pictureController.ts'
import { useRouter } from 'vue-router'

// 定义数据
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(true)
const searchParams = reactive<API.UserQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend'
})

const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const params = {
    ...searchParams,
    tags: [] as string[],
  }
  if (selectedCategory.value !== 'all') {
    params.category = selectedCategory.value
  }
  selectedTagList.value.forEach((useTag, index) => {
    if (useTag) {
      params.tags.push(tagList.value[index])
    }
  })
  const res = await listPictureVoByPageUsingPost(params)
  if (res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
  loading.value = false
}


// 加载时获取数据，请求一次
onMounted(() => {
  fetchData()
})


// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.current,
    pageSize: searchParams.pageSize,
    total: total.value,
    onChange: (page: number, pageSize: number) => {
      searchParams.current = page
      searchParams.pageSize = pageSize
      fetchData()
    }
  }
})

// 搜索
const doSearch = () => {
  searchParams.current = 1
  fetchData()
}

const categoryList = ref<string[]>([])
const selectedCategory = ref<string>('all')
const tagList = ref<string[]>([])
const selectedTagList = ref<string[]>([])

const router = useRouter()

// 跳转至图片详情页
const doClickPicture = (picture: API.PictureVO) => {
  router.push({
    path: `/picture/${picture.id}`
  })
}

/**
 * 获取标签和分类选项
 */
const getTagCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    tagList.value = res.data.data.tagList ?? []
    categoryList.value = res.data.data.categoryList ?? []
  } else {
    message.error('获取标签分类列表失败' + res.data.message)
  }
}

onMounted(() => {
  getTagCategoryOptions()
})

</script>

<style scoped>
#homePage {
  margin-bottom: 16px;
}

#homePage .search-bar {
  max-width: 60%;
  margin: 0 auto 10px;
}

#homePage .tag-bar {
  margin-bottom: 16px;
}
</style>
