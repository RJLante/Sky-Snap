# Sky-Snap

**Sky‑Snap** 是一款基于 **Spring Boot 2.7 + Redis + Tencent COS** 的开源云图库平台，为个人与团队提供图片的 **上传、管理、协作、分享与智能标注** 能力。
前端采用 **Vue 3 + Vite + Ant Design Vue**，支持灵活的二次开发与私有化部署。

## 💻 安裝

### Git 安裝

```powershell
git clone -b main https://github.com/RJLante/Sky-Snap
```

## ⚙ 项目简介

**Sky‑Snap** 将图库分为了公共图库与个人私有空间，用户可以在主页访问公共图库，并在公共图库上传图片，目前支持多种图片上传方式：

1. **文件上传**：在图片创建页面点击上传按钮或直接拖动图片上传。
2. **URL 上传**：在图片创建页面输入图片网址（URL）上传。
3. **批量上传（管理员）**：使用 `jsoup` 获取网站页面批量获取并上传相关图片。

![](https://private-user-images.githubusercontent.com/181469792/468794651-031f2977-0fe4-48dc-862c-7a25cf70dc46.png?jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NTMxMjg4NTksIm5iZiI6MTc1MzEyODU1OSwicGF0aCI6Ii8xODE0Njk3OTIvNDY4Nzk0NjUxLTAzMWYyOTc3LTBmZTQtNDhkYy04NjJjLTdhMjVjZjcwZGM0Ni5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUwNzIxJTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MDcyMVQyMDA5MTlaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT04NTcwYzBlYTg0MTA4YWJkZWJiZjVhYTUyNDFmZWQ1ZjFmMGI1NzczZTM5ODZkZjgyZmZjZDU1NmMyZGMyOTlhJlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.ENfzGOjJHgj_-a00o9KXZNvOQkr0zTO50CsfUl4zpEk)

![](https://private-user-images.githubusercontent.com/181469792/468794648-57399dd0-8824-4d7c-93ae-0e85267437c3.png?jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NTMxMjg4NTksIm5iZiI6MTc1MzEyODU1OSwicGF0aCI6Ii8xODE0Njk3OTIvNDY4Nzk0NjQ4LTU3Mzk5ZGQwLTg4MjQtNGQ3Yy05M2FlLTBlODUyNjc0MzdjMy5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUwNzIxJTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MDcyMVQyMDA5MTlaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT0yMjE4NjMzMTU1ZWU3NDM2ZGQ2ZjU2ZjY5OTFiNTIzYjIxYTY2ODc2NGJhYzZlMjhmOWM0YmMxZDJiNGMwYWI2JlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.UgctxfkZIP9TjpOm3Hn33W7noF2zUkaHupXwd-rOGag)

![](https://private-user-images.githubusercontent.com/181469792/468794650-8a12179a-f0a1-41c9-a289-c103025c8f06.png?jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NTMxMjg4NTksIm5iZiI6MTc1MzEyODU1OSwicGF0aCI6Ii8xODE0Njk3OTIvNDY4Nzk0NjUwLThhMTIxNzlhLWYwYTEtNDFjOS1hMjg5LWMxMDMwMjVjOGYwNi5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUwNzIxJTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MDcyMVQyMDA5MTlaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT00ZDU0NzgyZWNjMDg3M2MwYTkyNjFlNTg1YmI4MzgzYzkyOTcwYzZhZjhmOTk2MWEyNDk4OTYzMTU5YWI0MjZhJlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.9zMv-ASR7bWznn40IB3K-iklficBRPoa3SctihFXRUw)

### 功能一览

| 模块                | 亮点功能                                                     |
| ------------------- | ------------------------------------------------------------ |
| **用户与权限**      | 账号注册 / 登录、角色（普通用户 / 管理员）、基于 AOP 的 `@AuthCheck` 权限拦截 |
| **空间（Space）**   | 个人 / 团队空间隔离、空间容量统计                            |
| **图片（Picture）** | ✨ 图片上传（拖拽 / 网址粘贴）<br>✨ 标签 / 关键词搜索<br>✨ 在线预览（自适应分辨率）<br>版本 / 回收站 / 审核 |
| **文件存储**        | 接入腾讯云 COS：原图、缩略图数据分离存储                     |
| **性能优化**        | Redis + Caffeine 双层缓存、接口防刷、懒加载、分页无限滚动    |
| **开放能力**        | 基于 Knife4j 的在线 API 文档（`/doc.html`），支持 OpenAPI 规范 |



![](https://private-user-images.githubusercontent.com/181469792/468794652-444c164c-7746-449c-bb51-ab1e9258cd45.png?jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NTMxMjk0ODIsIm5iZiI6MTc1MzEyOTE4MiwicGF0aCI6Ii8xODE0Njk3OTIvNDY4Nzk0NjUyLTQ0NGMxNjRjLTc3NDYtNDQ5Yy1iYjUxLWFiMWU5MjU4Y2Q0NS5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUwNzIxJTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MDcyMVQyMDE5NDJaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT0yOWQxYTM5Y2IwZGRlOTFhNjYwZmYzNTBjMGY3OTdhMTllODRmMDNiMWQ4ODA4ZGNjMWY2NGZiYzg0YjM3ZDkxJlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.6Gw9VD5O5YspNyKECTvo0JwVrzD19Cn1-MpkY0hvCM4)

![](https://private-user-images.githubusercontent.com/181469792/468794649-ba4c863e-8542-4491-bd7d-a48df4145cb2.png?jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NTMxMjk0ODIsIm5iZiI6MTc1MzEyOTE4MiwicGF0aCI6Ii8xODE0Njk3OTIvNDY4Nzk0NjQ5LWJhNGM4NjNlLTg1NDItNDQ5MS1iZDdkLWE0OGRmNDE0NWNiMi5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUwNzIxJTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MDcyMVQyMDE5NDJaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT00MzMzNmViYWE0Yjk1MzE3MjM5ZGVkMzVlMmNmYzE1MjJmODMyMzNkNDU0MzIzOTQ1NGQwNDgxM2VkYTQyZjg5JlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.W4o4tTFLgzEFmp0cQB7wA9A5KZ857Lr8k4KWg-2eUNA)



### 技术栈

| 层次            | 选型                                                         |
| --------------- | ------------------------------------------------------------ |
| **后端**        | Java 17 · Spring Boot 2.7 · Spring MVC · MyBatis‑Plus · Redis 6 · Caffeine · Knife4j · Lombok |
| **数据库**      | MySQL 8（`sql/create_table.sql` 提供建表脚本）               |
| **对象存储**    | 腾讯云 COS SDK 5.x                                           |
| **前端**        | Vue 3 · Vite 5 · Pinia · Ant Design Vue 4 · Axios            |
| **构建 / 部署** | Maven 3.8 · Docker / Docker Compose                          |

### 

### 环境要求

| 依赖    | 版本       | 备注                    |
| ------- | ---------- | ----------------------- |
| JDK     | 17+        | 本地或容器              |
| Maven   | 3.8+       | 构建后端                |
| Node.js | ≥ 18       | 构建前端                |
| MySQL   | ≥ 8.0      | 字符集 `utf8mb4`        |
| Redis   | ≥ 6.0      | 开启 `requirepass` 建议 |
| COS     | 任何标椎桶 | 需启用 API 访问         |

