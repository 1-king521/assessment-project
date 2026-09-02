# 招聘测评平台后端

第一阶段后端基线，包含：

- Java 21、Kotlin、Spring Boot
- PostgreSQL + Flyway 业务数据持久化
- JWT 登录认证和角色权限
- 初始化管理员
- 创建测评任务、创建评估分配、生成安全链接
- 标记测评链接已发送
- 操作日志和乐观锁

## 本地启动

先修改 `src/main/resources/application-local.yml` 中的 PostgreSQL 用户名和密码。
本地配置默认启用，不需要在 IDEA 的 Run Configuration 中填写环境变量。

先启动基础服务：

```powershell
docker compose up -d
```

再启动应用：

```powershell
gradle bootRun
```

默认管理员仅用于本地开发：

- 用户名：`admin`
- 密码：`ChangeMe123!`

部署前必须通过 `INITIAL_ADMIN_PASSWORD`、`JWT_SECRET`、数据库环境变量覆盖默认值。

健康检查：`GET http://localhost:8080/actuator/health`

## 核心接口

登录：

```http
POST /api/auth/login
Content-Type: application/json

{"username":"admin","password":"ChangeMe123!"}
```

创建测评任务：

```http
POST /api/assessment-tasks
Authorization: Bearer <token>
Content-Type: application/json

{
  "candidateName": "张三",
  "candidatePhone": "13800000000",
  "positionId": 1,
  "templateVersionId": 1,
  "reviewerUserIds": [2],
  "deadline": "2026-09-01T12:00:00Z"
}
```

创建成功后任务为 `DRAFT`；调用 `POST /api/assessment-tasks/{id}/send` 后变为 `SENT`。
原始候选人 Token 只在创建响应的链接中返回一次，数据库仅保存 SHA-256 哈希。

## 候选人公开测评接口

当前阶段候选人使用创建任务返回的链接中的 Token 访问：

- `GET /api/public/assessments/{token}`：打开测评并返回模板、答案和附件
- `GET /api/public/assessments/{token}/draft`：读取当前草稿
- `PUT /api/public/assessments/{token}/draft`：保存答案草稿
- `POST /api/public/assessments/{token}/submit`：校验必填题并提交测评
- `POST /api/public/assessments/{token}/files/presign`：创建上传记录
- `PUT /api/public/assessments/{token}/files/{fileId}/content`：本地开发上传文件内容
- `POST /api/public/assessments/{token}/files/{fileId}/complete`：确认文件校验值
- `DELETE /api/public/assessments/{token}/files/{fileId}`：删除未提交附件

候选人接口仅校验测评链接 Token，无需额外身份验证或 Cookie。任何获得有效链接的浏览器都可以读取草稿、保存答案、操作附件并提交；任务过期、撤回、归档、提交或重新生成链接后，原链接不可继续使用。

评估人员在候选人提交后由 HR 分配。分配前可调整人员；评估人员明确开始评估后，分配被锁定。

管理端使用 `POST /api/assessment-tasks/{id}/reviewers` 分配评估人员；任务创建时不再提交评估人员列表。

文件上传本地测试：

1. 调用 `presign`：

```json
{
  "questionId": "q_file",
  "fileName": "demo.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 1234
}
```

2. 使用返回的 `uploadUrl` 发起 `PUT`，Body 类型选择 `form-data`，字段名为 `file`，类型选择文件。
3. 上传成功后可以直接使用返回的 SHA-256 校验值调用 `complete`；当前本地上传接口已在写盘时完成状态更新，`complete` 仍会再次校验文件内容。
4. 文件保存在 `app.candidate.storage-path`，本地默认是 `backend/data/uploads`。

保存草稿示例：

```http
PUT /api/public/assessments/<token>/draft
Content-Type: application/json

{
  "draftVersion": 0,
  "answers": [
    {"questionId": "q1", "answerJson": "\"我理解事务是保证一组操作一致性的机制\""}
  ]
}
```

提交示例：

```http
POST /api/public/assessments/<token>/submit
Content-Type: application/json

{"idempotencyKey":"submit-20260825-001","confirm":"SUBMIT"}
```

候选人公开接口使用测评链接中的 Token 校验访问权限，不创建额外的候选人会话。

## 评估人员接口

评估人员登录后使用自己的 JWT：

- `GET /api/review-assignments`：查询本人被分配的测评
- `GET /api/review-assignments/{id}`：查看候选人答案和附件元数据
- `POST /api/review-assignments/{id}/start`：开始评估
- `POST /api/review-assignments/{id}/submit`：提交评估结果

提交示例：

```json
{
  "conclusion": "PASS",
  "score": 86.5,
  "reason": ""
}
```

不通过时 `conclusion` 使用 `REJECTED`，并且 `reason` 必填。只有被分配的评估人员可以访问对应任务；所有未取消的评估分配完成后，任务才会变为 `REVIEWED`。

## HR 管理端接口

HR、HR_MANAGER 和 ADMIN 可以访问任务管理接口。普通 HR 只能看到自己创建并负责的任务；HR_MANAGER 和 ADMIN 可以查看全部任务。

- `GET /api/assessment-tasks?page=1&pageSize=20&status=SUBMITTED&keyword=张三`：分页查询任务，`status` 和 `keyword` 可选
- `GET /api/assessment-tasks/statistics`：返回当前数据权限范围内各任务状态数量
- `GET /api/assessment-tasks/{id}`：查看任务详情、答案、附件、评估分配、评估结果和操作日志
- `POST /api/assessment-tasks/{id}/revoke`：撤回尚未提交的任务
- `POST /api/assessment-tasks/{id}/regenerate-link`：重新生成候选人测试链接，旧链接立即失效
- `POST /api/assessment-tasks/{id}/extend`：延期任务
- `POST /api/assessment-tasks/{id}/archive`：归档已评估、已撤回或已过期任务

延期请求示例：

```json
{"deadline":"2026-09-15T12:00:00Z"}
```
