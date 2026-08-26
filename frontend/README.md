# 招聘测评管理端

## 启动

先确保后端已在 `http://localhost:8080` 启动，然后在本目录执行：

```powershell
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。开发服务器会把 `/api` 请求代理到后端 `8080` 端口。

## IDEA 启动

用 IDEA 打开 `frontend` 目录，在内置 Terminal 执行上面的两个命令即可。也可以建立一个 npm Run Configuration：

- package.json：选择当前目录的 `package.json`
- Command：`run`
- Scripts：`dev`

当前页面已实现管理员/HR 登录、任务统计、任务列表筛选分页、详情查看、撤回、延期和归档。

模板管理页面支持：

- 新建模板并配置文本题、单选题、多选题和文件上传题
- 查看模板版本
- HR 创建草稿模板
- HR_MANAGER 或 ADMIN 发布模板版本
