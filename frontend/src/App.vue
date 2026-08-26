<script setup>
import { computed, onMounted, ref } from 'vue'

const token = ref(localStorage.getItem('assessment_token') || '')
const user = ref(JSON.parse(localStorage.getItem('assessment_user') || 'null'))
const loginForm = ref({ username: 'admin', password: 'ChangeMe123!' })
const loginError = ref('')
const loading = ref(false)
const message = ref('')
const tasks = ref([])
const stats = ref({ total: 0, byStatus: {} })
const filters = ref({ keyword: '', status: '' })
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const detail = ref(null)
const detailTab = ref('overview')
const extendDeadline = ref('')
const showCreate = ref(false)
const createdLink = ref('')
const view = ref('tasks')
const templateLoading = ref(false)
const templateList = ref([])
const templatePositions = ref([])
const showTemplateCreate = ref(false)
const templateForm = ref({ templateName: '', positionId: '', questions: [] })
const templateDraft = ref(null)
const createLoading = ref(false)
const positions = ref([])
const templates = ref([])
const templateVersions = ref([])
const reviewers = ref([])
const createForm = ref({ candidateName: '', candidatePhone: '', candidateEmail: '', candidateSource: '', positionId: '', templateId: '', templateVersionId: '', reviewerUserIds: [], deadline: '' })

const statusLabels = {
  DRAFT: '草稿', SENT: '已发送', OPENED: '已打开', IN_PROGRESS: '答题中',
  SUBMITTED: '待评估', EXPIRED: '已过期', REVOKED: '已撤回', REVIEWING: '评估中',
  REVIEWED: '已评估', ARCHIVED: '已归档'
}
const statusOrder = Object.keys(statusLabels)
const loggedIn = computed(() => Boolean(token.value))
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const canPublish = computed(() => ['ADMIN', 'HR_MANAGER'].includes(user.value?.role))

async function request(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  if (token.value) headers.Authorization = `Bearer ${token.value}`
  if (options.body) headers['Content-Type'] = 'application/json'
  const response = await fetch(path, { ...options, headers })
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(body.message || `请求失败 (${response.status})`)
  return body
}

async function login() {
  loginError.value = ''
  loading.value = true
  try {
    const result = await request('/api/auth/login', { method: 'POST', body: JSON.stringify(loginForm.value) })
    token.value = result.accessToken
    user.value = result.user
    localStorage.setItem('assessment_token', result.accessToken)
    localStorage.setItem('assessment_user', JSON.stringify(result.user))
    await refresh()
  } catch (error) {
    loginError.value = error.message
  } finally {
    loading.value = false
  }
}

function logout() {
  token.value = ''
  user.value = null
  localStorage.removeItem('assessment_token')
  localStorage.removeItem('assessment_user')
}

async function refresh() {
  loading.value = true
  try {
    const query = new URLSearchParams({ page: page.value, pageSize: pageSize.value })
    if (filters.value.status) query.set('status', filters.value.status)
    if (filters.value.keyword.trim()) query.set('keyword', filters.value.keyword.trim())
    const [list, summary] = await Promise.all([
      request(`/api/assessment-tasks?${query}`),
      request('/api/assessment-tasks/statistics')
    ])
    tasks.value = list.items
    total.value = list.total
    stats.value = summary
  } catch (error) {
    message.value = error.message
    if (error.message.includes('未登录')) logout()
  } finally {
    loading.value = false
  }
}

async function loadTemplates() {
  view.value = 'templates'
  templateLoading.value = true
  try {
    const [items, positionData] = await Promise.all([request('/api/assessment-templates'), request('/api/positions')])
    templateList.value = items
    templatePositions.value = positionData
  } catch (error) { message.value = error.message } finally { templateLoading.value = false }
}

function positionName(positionId) {
  return templatePositions.value.find(item => item.id === positionId)?.positionName || `岗位 #${positionId}`
}

function addQuestion() {
  templateForm.value.questions.push({ id: `q${templateForm.value.questions.length + 1}`, type: 'TEXT', title: '', required: true, options: ['', ''] })
}

function removeQuestion(index) { templateForm.value.questions.splice(index, 1) }

function questionNeedsOptions(question) { return ['SINGLE', 'MULTIPLE'].includes(question.type) }

function openTemplateCreate() {
  templateForm.value = { templateName: '', positionId: '', questions: [] }
  addQuestion()
  showTemplateCreate.value = true
}

async function createTemplate() {
  try {
    const questions = templateForm.value.questions.map((question, index) => ({
      id: question.id || `q${index + 1}`,
      type: question.type,
      title: question.title.trim(),
      required: question.required,
      ...(questionNeedsOptions(question) ? { options: question.options.filter(item => item.trim()) } : {})
    }))
    if (!templateForm.value.templateName.trim() || !templateForm.value.positionId || questions.some(item => !item.title)) {
      throw new Error('请填写模板名称、岗位和所有题目标题')
    }
    const result = await request('/api/assessment-templates', { method: 'POST', body: JSON.stringify({ templateName: templateForm.value.templateName.trim(), positionId: Number(templateForm.value.positionId), schemaJson: JSON.stringify({ questions }) }) })
    showTemplateCreate.value = false
    message.value = `模板创建成功，版本 v1 处于草稿状态`
    await loadTemplates()
    templateDraft.value = result
  } catch (error) { message.value = error.message }
}

async function openTemplateVersions(template) {
  try { templateDraft.value = { template, versions: await request(`/api/assessment-templates/${template.id}/versions`) } } catch (error) { message.value = error.message }
}

async function publishTemplate(version) {
  try {
    await request(`/api/assessment-template-versions/${version.id}/publish`, { method: 'POST' })
    message.value = '模板版本已发布'
    templateDraft.value = null
    await loadTemplates()
  } catch (error) { message.value = error.message }
}

async function openCreate() {
  showCreate.value = true
  createForm.value.deadline = defaultDeadline()
  try {
    const [positionData, templateData, reviewerData] = await Promise.all([
      request('/api/positions'), request('/api/assessment-templates'), request('/api/reviewers')
    ])
    positions.value = positionData.filter(item => item.status === 'ACTIVE')
    templates.value = templateData.filter(item => item.status === 'ACTIVE')
    reviewers.value = reviewerData
  } catch (error) { message.value = error.message }
}

async function loadVersions() {
  createForm.value.templateVersionId = ''
  templateVersions.value = []
  if (!createForm.value.templateId) return
  try {
    const versions = await request(`/api/assessment-templates/${createForm.value.templateId}/versions`)
    templateVersions.value = versions.filter(item => item.status === 'PUBLISHED')
  } catch (error) { message.value = error.message }
}

function defaultDeadline() {
  const date = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset())
  return date.toISOString().slice(0, 16)
}

async function createTask() {
  createLoading.value = true
  try {
    const payload = { ...createForm.value, positionId: Number(createForm.value.positionId), templateVersionId: Number(createForm.value.templateVersionId), reviewerUserIds: createForm.value.reviewerUserIds.map(Number), deadline: new Date(createForm.value.deadline).toISOString() }
    const result = await request('/api/assessment-tasks', { method: 'POST', body: JSON.stringify(payload) })
    showCreate.value = false
    createdLink.value = result.assessmentUrl
    message.value = `任务 ${result.taskNo} 创建成功，测评链接已生成`
    await refresh()
    await openDetail(result.id)
  } catch (error) { message.value = error.message } finally { createLoading.value = false }
}

function search() {
  page.value = 1
  refresh()
}

async function openDetail(id) {
  try {
    detail.value = await request(`/api/assessment-tasks/${id}`)
    detailTab.value = 'overview'
    extendDeadline.value = detail.value.deadline.slice(0, 16)
  } catch (error) { message.value = error.message }
}

async function operate(action) {
  if (!detail.value) return
  if (action === 'extend') {
    const deadline = new Date(extendDeadline.value).toISOString()
    await runAction(`/api/assessment-tasks/${detail.value.id}/extend`, { deadline })
  } else {
    await runAction(`/api/assessment-tasks/${detail.value.id}/${action}`)
  }
}

async function runAction(path, body) {
  try {
    await request(path, { method: 'POST', ...(body ? { body: JSON.stringify(body) } : {}) })
    message.value = '操作成功'
    await refresh()
    if (detail.value) detail.value = await request(`/api/assessment-tasks/${detail.value.id}`)
  } catch (error) { message.value = error.message }
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '--'
}

function statusText(value) { return statusLabels[value] || value }
function statusClass(value) { return `status-${String(value).toLowerCase()}` }

async function copyCreatedLink() {
  await navigator.clipboard.writeText(createdLink.value)
  message.value = '测试链接已复制'
}

onMounted(() => { if (loggedIn.value) refresh() })
</script>

<template>
  <main v-if="!loggedIn" class="login-page">
    <section class="login-panel">
      <div class="brand-mark">RA</div>
      <p class="eyebrow">RECRUITMENT ASSESSMENT</p>
      <h1>招聘测评工作台</h1>
      <p class="muted">统一管理候选人测评任务、评估进度和结果。</p>
      <form @submit.prevent="login" class="login-form">
        <label>账号<input v-model="loginForm.username" autocomplete="username" /></label>
        <label>密码<input v-model="loginForm.password" type="password" autocomplete="current-password" /></label>
        <p v-if="loginError" class="error-text">{{ loginError }}</p>
        <button class="primary-button" :disabled="loading">{{ loading ? '登录中...' : '登录工作台' }}</button>
      </form>
    </section>
  </main>

  <main v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark small">RA</span><span>测评工作台</span></div>
      <nav><a class="nav-item" :class="{active: view === 'tasks'}" @click="view = 'tasks'">任务管理 <span>⌘</span></a><a class="nav-item" :class="{active: view === 'templates'}" @click="loadTemplates">模板管理</a><a class="nav-item">评估进度</a><a class="nav-item">系统设置</a></nav>
      <div class="sidebar-foot"><span class="avatar">{{ user?.realName?.slice(0, 1) || '管' }}</span><div><strong>{{ user?.realName }}</strong><small>{{ user?.role }}</small></div><button class="icon-button" title="退出登录" @click="logout">↪</button></div>
    </aside>
    <section class="content">
      <template v-if="view === 'tasks'">
      <header class="topbar"><div><p class="eyebrow">OPERATIONS / TASKS</p><h1>测评任务</h1></div><div class="top-actions"><button class="outline-button" @click="refresh">↻ 刷新数据</button><button class="primary-button compact" @click="openCreate">＋ 创建测评任务</button></div></header>
      <div v-if="message" class="toast" @click="message = ''">{{ message }} <span>×</span></div>
      <section class="stat-grid">
        <article class="stat-card accent"><span>全部任务</span><strong>{{ stats.total }}</strong><small>当前权限范围内</small></article>
        <article v-for="status in ['SENT', 'OPENED', 'SUBMITTED', 'REVIEWED']" :key="status" class="stat-card"><span>{{ statusText(status) }}</span><strong>{{ stats.byStatus[status] || 0 }}</strong><small>{{ status }}</small></article>
      </section>
      </template>
      <template v-else>
        <header class="topbar"><div><p class="eyebrow">CONFIGURATION / TEMPLATES</p><h1>模板管理</h1></div><div class="top-actions"><button class="outline-button" @click="loadTemplates">↻ 刷新数据</button><button class="primary-button compact" @click="openTemplateCreate">＋ 新建模板</button></div></header>
        <section class="table-section"><div class="section-heading"><div><h2>测评模板</h2><span>{{ templateList.length }} 个模板</span></div><span v-if="templateLoading" class="loading">正在加载...</span></div><div class="table-scroll"><table><thead><tr><th>模板名称</th><th>适用岗位</th><th>状态</th><th>模板负责人</th><th>操作</th></tr></thead><tbody><tr v-for="template in templateList" :key="template.id"><td><strong>{{ template.templateName }}</strong></td><td>{{ positionName(template.positionId) }}</td><td><span class="status-pill" :class="template.status === 'ACTIVE' ? 'status-reviewed' : ''">{{ template.status === 'ACTIVE' ? '启用' : '草稿' }}</span></td><td>#{{ template.ownerUserId }}</td><td><button class="detail-button" @click="openTemplateVersions(template)">版本历史 →</button></td></tr><tr v-if="!templateList.length"><td colspan="5" class="empty">暂无模板，请先新建模板</td></tr></tbody></table></div></section>
      </template>
      <section class="toolbar"><div class="search-wrap"><span>⌕</span><input v-model="filters.keyword" @keyup.enter="search" placeholder="搜索任务编号、候选人或联系方式" /></div><select v-model="filters.status" @change="search"><option value="">全部状态</option><option v-for="status in statusOrder" :key="status" :value="status">{{ statusText(status) }}</option></select><button class="primary-button compact" @click="search">查询</button></section>
      <section class="table-section"><div class="section-heading"><div><h2>任务列表</h2><span>{{ total }} 条记录</span></div><span v-if="loading" class="loading">正在加载...</span></div>
        <div class="table-scroll"><table><thead><tr><th>任务编号</th><th>候选人</th><th>应聘岗位</th><th>负责人</th><th>状态</th><th>截止时间</th><th>评估进度</th><th></th></tr></thead><tbody><tr v-for="task in tasks" :key="task.id"><td><button class="link-button" @click="openDetail(task.id)">{{ task.taskNo }}</button></td><td><strong>{{ task.candidateName }}</strong><small>{{ task.candidatePhone || task.candidateEmail || '--' }}</small></td><td>{{ task.positionName }}</td><td>{{ task.hrUserName }}</td><td><span class="status-pill" :class="statusClass(task.status)">{{ statusText(task.status) }}</span></td><td>{{ formatDate(task.deadline) }}</td><td>{{ task.completedAssignmentCount }}/{{ task.assignmentCount }} 人</td><td><button class="detail-button" @click="openDetail(task.id)">详情 →</button></td></tr><tr v-if="!tasks.length"><td colspan="8" class="empty">没有符合条件的任务</td></tr></tbody></table></div>
        <footer class="pagination"><span>第 {{ page }} / {{ totalPages }} 页</span><div><button :disabled="page <= 1" @click="page--; refresh()">上一页</button><button :disabled="page >= totalPages" @click="page++; refresh()">下一页</button></div></footer>
      </section>
    </section>
  </main>

  <div v-if="detail" class="drawer-backdrop" @click.self="detail = null"><aside class="drawer"><header><div><p class="eyebrow">TASK DETAIL</p><h2>{{ detail.taskNo }}</h2></div><button class="close-button" title="关闭详情" @click="detail = null">×</button></header><div class="drawer-summary"><div><span>候选人</span><strong>{{ detail.candidateName }}</strong></div><span class="status-pill" :class="statusClass(detail.status)">{{ statusText(detail.status) }}</span></div><div class="detail-actions"><button v-if="['DRAFT','SENT','OPENED','IN_PROGRESS'].includes(detail.status)" class="outline-button" @click="operate('revoke')">撤回任务</button><button v-if="!['REVOKED','ARCHIVED','REVIEWED'].includes(detail.status)" class="outline-button" @click="operate('extend')">保存延期</button><button v-if="['REVIEWED','REVOKED','EXPIRED'].includes(detail.status)" class="primary-button compact" @click="operate('archive')">归档任务</button></div><label v-if="!['REVOKED','ARCHIVED','REVIEWED'].includes(detail.status)" class="deadline-input">截止时间<input v-model="extendDeadline" type="datetime-local" /></label><div class="tabs"><button :class="{selected: detailTab === 'overview'}" @click="detailTab='overview'">概览</button><button :class="{selected: detailTab === 'review'}" @click="detailTab='review'">评估分配</button><button :class="{selected: detailTab === 'logs'}" @click="detailTab='logs'">操作日志</button></div><div v-if="detailTab === 'overview'" class="detail-content"><dl><dt>候选人联系方式</dt><dd>{{ detail.candidatePhone || '--' }} · {{ detail.candidateEmail || '--' }}</dd><dt>应聘岗位</dt><dd>{{ detail.position.name }}（{{ detail.position.code }}）</dd><dt>模板版本</dt><dd>v{{ detail.templateVersion.versionNo }} · {{ detail.templateVersion.status }}</dd><dt>截止时间</dt><dd>{{ formatDate(detail.deadline) }}</dd></dl><h3>答案（{{ detail.answers.length }}）</h3><div v-for="answer in detail.answers" :key="answer.id" class="answer-row"><strong>{{ answer.questionId }}</strong><code>{{ answer.answerJson }}</code></div><h3>附件（{{ detail.files.length }}）</h3><p v-if="!detail.files.length" class="muted">暂无附件</p><div v-for="file in detail.files" :key="file.id" class="file-row">{{ file.fileName }} <span>{{ file.sizeBytes }} bytes</span></div></div><div v-if="detailTab === 'review'" class="detail-content"><div v-for="assignment in detail.assignments" :key="assignment.id" class="assignment-row"><div><strong>{{ assignment.reviewerUserName }}</strong><small>{{ assignment.status }}</small></div><div class="review-result">{{ assignment.conclusion || '未提交' }} <span v-if="assignment.score">{{ assignment.score }} 分</span></div></div></div><div v-if="detailTab === 'logs'" class="detail-content timeline"><div v-for="log in detail.operationLogs" :key="log.id"><span>{{ formatDate(log.createdAt) }}</span><strong>{{ log.action }}</strong><small>{{ log.fromStatus || '--' }} → {{ log.toStatus || '--' }}</small></div></div></aside></div>
  <div v-if="showCreate" class="modal-backdrop" @click.self="showCreate = false"><section class="create-modal"><header><div><p class="eyebrow">NEW ASSESSMENT TASK</p><h2>创建测评任务</h2></div><button class="close-button" title="关闭创建窗口" @click="showCreate = false">×</button></header><form @submit.prevent="createTask" class="create-form"><div class="form-grid"><label>候选人姓名 *<input v-model="createForm.candidateName" required maxlength="80" placeholder="请输入候选人姓名" /></label><label>手机号<input v-model="createForm.candidatePhone" maxlength="30" placeholder="选填" /></label><label>邮箱<input v-model="createForm.candidateEmail" type="email" maxlength="120" placeholder="选填" /></label><label>候选人来源<input v-model="createForm.candidateSource" maxlength="50" placeholder="例如：招聘网站" /></label><label>应聘岗位 *<select v-model="createForm.positionId" required><option value="">请选择岗位</option><option v-for="position in positions" :key="position.id" :value="position.id">{{ position.positionName }}（{{ position.positionCode }}）</option></select></label><label>测评模板 *<select v-model="createForm.templateId" required @change="loadVersions"><option value="">请选择模板</option><option v-for="template in templates" :key="template.id" :value="template.id">{{ template.templateName }}</option></select></label><label>模板版本 *<select v-model="createForm.templateVersionId" required><option value="">请先选择模板</option><option v-for="version in templateVersions" :key="version.id" :value="version.id">v{{ version.versionNo }} · {{ version.status }}</option></select></label><label>截止时间 *<input v-model="createForm.deadline" type="datetime-local" required /></label></div><fieldset><legend>评估人员 *</legend><label v-for="reviewer in reviewers" :key="reviewer.id" class="reviewer-option"><input v-model="createForm.reviewerUserIds" type="checkbox" :value="reviewer.id" /> <span>{{ reviewer.realName }} <small>{{ reviewer.username }}</small></span></label><p v-if="!reviewers.length" class="muted">暂无可用评估人员</p></fieldset><footer class="modal-footer"><button type="button" class="outline-button" @click="showCreate = false">取消</button><button type="submit" class="primary-button" :disabled="createLoading || !createForm.reviewerUserIds.length">{{ createLoading ? '创建中...' : '创建任务' }}</button></footer></form></section></div>
  <div v-if="createdLink" class="modal-backdrop" @click.self="createdLink = ''"><section class="link-modal"><header><div><p class="eyebrow">ASSESSMENT LINK READY</p><h2>测试链接已生成</h2></div><button class="close-button" title="关闭链接窗口" @click="createdLink = ''">×</button></header><p class="muted">复制下面的链接，在浏览器中打开即可进入候选人测评页面。</p><input class="link-field" :value="createdLink" readonly /><footer class="modal-footer"><button class="outline-button" @click="copyCreatedLink">复制链接</button><a class="primary-button compact" :href="createdLink" target="_blank" rel="noreferrer">打开测试页面</a></footer></section></div>
  <div v-if="showTemplateCreate" class="modal-backdrop" @click.self="showTemplateCreate = false"><section class="create-modal template-editor"><header><div><p class="eyebrow">NEW ASSESSMENT TEMPLATE</p><h2>新建测评模板</h2></div><button class="close-button" @click="showTemplateCreate = false">×</button></header><form @submit.prevent="createTemplate" class="create-form"><div class="form-grid"><label>模板名称 *<input v-model="templateForm.templateName" required placeholder="例如：Java开发工程师测评" /></label><label>适用岗位 *<select v-model="templateForm.positionId" required><option value="">请选择岗位</option><option v-for="position in templatePositions" :key="position.id" :value="position.id">{{ position.positionName }}（{{ position.positionCode }}）</option></select></label></div><div class="question-editor"><div class="section-heading"><div><h2>题目配置</h2><span>{{ templateForm.questions.length }} 道题</span></div><button type="button" class="outline-button" @click="addQuestion">＋ 添加题目</button></div><article v-for="(question, index) in templateForm.questions" :key="question.id" class="question-card"><div class="question-card-head"><strong>题目 {{ index + 1 }}</strong><button type="button" class="icon-button dark" @click="removeQuestion(index)">×</button></div><div class="form-grid"><label>题目标题 *<input v-model="question.title" required placeholder="请输入候选人需要回答的问题" /></label><label>题型<select v-model="question.type"><option value="TEXT">文本题</option><option value="SINGLE">单选题</option><option value="MULTIPLE">多选题</option><option value="FILE">文件上传</option></select></label></div><label class="required-check"><input v-model="question.required" type="checkbox" /> 必填题</label><div v-if="questionNeedsOptions(question)" class="options-grid"><input v-for="(_, optionIndex) in question.options" :key="optionIndex" v-model="question.options[optionIndex]" :placeholder="`选项 ${optionIndex + 1}`" /><button type="button" class="detail-button" @click="question.options.push('')">＋选项</button></div></article></div><footer class="modal-footer"><button type="button" class="outline-button" @click="showTemplateCreate = false">取消</button><button type="submit" class="primary-button">保存草稿模板</button></footer></form></section></div>
  <div v-if="templateDraft?.versions" class="modal-backdrop" @click.self="templateDraft = null"><section class="link-modal version-modal"><header><div><p class="eyebrow">VERSION HISTORY</p><h2>{{ templateDraft.template.templateName }}</h2></div><button class="close-button" @click="templateDraft = null">×</button></header><div v-for="version in templateDraft.versions" :key="version.id" class="version-row"><div><strong>版本 v{{ version.versionNo }}</strong><small>{{ version.status }}</small></div><button v-if="canPublish && ['DRAFT','PENDING'].includes(version.status)" class="primary-button compact" @click="publishTemplate(version)">发布版本</button><span v-else class="status-pill" :class="version.status === 'PUBLISHED' ? 'status-reviewed' : ''">{{ version.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span></div></section></div>
</template>
