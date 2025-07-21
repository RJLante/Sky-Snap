# Sky-Snap

**Sky‑Snap** 是一款基于 **Spring Boot 2.7 + Redis + Tencent COS** 的开源云图库平台，为个人与团队提供图片的 **上传、管理、协作、分享与智能标注** 能力。  
前端采用 **Vue 3 + Vite + Ant Design Vue**，支持灵活的二次开发与私有化部署。



### 环境要求

| 依赖    | 版本       | 备注                    |
| ------- | ---------- | ----------------------- |
| JDK     | 17+        | 本地或容器              |
| Maven   | 3.8+       | 构建后端                |
| Node.js | ≥ 18       | 构建前端                |
| MySQL   | ≥ 8.0      | 字符集 `utf8mb4`        |
| Redis   | ≥ 6.0      | 开启 `requirepass` 建议 |
| COS     | 任何标椎桶 | 需启用 API 访问         |
