<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'

const token = ref(localStorage.getItem('assessment_token') || '')
const user = ref(JSON.parse(localStorage.getItem('assessment_user') || 'null'))
const loginForm = ref({ username: '', password: '' })
const loginError = ref('')
const showRegister = ref(false)
const registerError = ref('')
const registerMessage = ref('')
const loginMessage = ref('')
const registerLoading = ref(false)
const dingtalkProfileLoading = ref(false)
const dingtalkProfileMessage = ref('')
const lastDingtalkLookupPhone = ref('')
const registerForm = ref({ username: '', password: '', realName: '', phone: '', requestedRole: 'HR', departmentName: '', positionName: '', dingtalkDepartmentId: null })
const pendingUsers = ref([])
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
const createdLinkInput = ref(null)
const view = ref('tasks')
const templateLoading = ref(false)
const templateSaving = ref(false)
const templateList = ref([])
const templatePositions = ref([])
const showTemplateCreate = ref(false)
const templateForm = ref({ templateName: '', positionId: '', questions: [] })
const editingTemplateVersion = ref(null)
const templateDraft = ref(null)
const templatePreview = ref(null)
const materialPreview = ref(null)
const createLoading = ref(false)
const positions = ref([])
const templates = ref([])
const templateVersions = ref([])
const reviewers = ref([])
const selectedDepartment = ref('')
const detailReviewerIds = ref([])
const reviewTimeoutValue = ref(1)
const reviewTimeoutUnit = ref('DAYS')
const assigningReviewers = ref(false)
const showReviewerPicker = ref(false)
const reviewerSearch = ref('')
const createForm = ref({ candidateName: '', candidatePhone: '', candidateEmail: '', candidateSource: '', positionId: '', templateId: '', templateVersionId: '', deadline: '' })
const records = ref([])
const recordLoading = ref(false)
const recordPage = ref(1)
const recordPageSize = ref(10)
const recordTotal = ref(0)
const recordPositions = ref([])
const recordFilters = ref({ keyword: '', positionId: '', conclusion: '' })
const recordDetail = ref(null)
const recordFileUrls = ref({})
const detailFileUrls = ref({})
const taskRecordData = ref(null)
const deepLinkTaskId = (() => {
  const value = new URLSearchParams(window.location.search).get('taskId')
  return /^\d+$/.test(value || '') ? Number(value) : null
})()

const statusLabels = {
  DRAFT: '草稿', SENT: '已发送', OPENED: '已打开', IN_PROGRESS: '答题中',
  SUBMITTED: '待评估', EXPIRED: '已过期', REVOKED: '已撤回', REVIEWING: '评估中',
  REVIEWED: '已评估', ARCHIVED: '已归档'
}
const operationActionLabels = {
  TASK_CREATED: '创建任务',
  TASK_SENT: '发放任务',
  ASSESSMENT_LINK_REGENERATED: '重新生成测评链接',
  LINK_ACCESSED: '打开测评链接',
  FILE_UPLOADED: '上传附件',
  FILE_DELETED: '删除附件',
  DRAFT_SAVED: '保存答题草稿',
  TASK_SUBMITTED: '提交测评',
  REVIEWER_ADDED: '添加评估人员',
  REVIEWER_REMOVED: '移除评估人员',
  REVIEWERS_ASSIGNED: '分配评估人员',
  REVIEW_STARTED: '开始评估',
  REVIEW_SUBMITTED: '提交评估结果',
  TASK_EXTENDED: '延长截止时间',
  TASK_REVOKED: '撤回任务',
  TASK_ARCHIVED: '归档任务',
  TASK_AUTO_ARCHIVED: '系统自动归档',
  REVIEW_OVERDUE_REMINDER_SENT: '发送评估超时提醒'
}
const statusOrder = Object.keys(statusLabels)
const loggedIn = computed(() => Boolean(token.value))
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const recordTotalPages = computed(() => Math.max(1, Math.ceil(recordTotal.value / recordPageSize.value)))
const canPublish = computed(() => ['ADMIN', 'HR_MANAGER'].includes(user.value?.role))
const canManageUsers = computed(() => user.value?.role === 'ADMIN')
const availableTemplatePositions = computed(() => templatePositions.value.filter(
  position => !templateList.value.some(template => template.positionId === position.id)
))
const createTemplates = computed(() => {
  if (!createForm.value.positionId) return []
  return templates.value.filter(template => String(template.positionId) === String(createForm.value.positionId))
})
const recordQuestions = computed(() => {
  if (!recordDetail.value?.templateVersion?.schemaJson) return []
  try {
    const schema = typeof recordDetail.value.templateVersion.schemaJson === 'string'
      ? JSON.parse(recordDetail.value.templateVersion.schemaJson)
      : recordDetail.value.templateVersion.schemaJson
    return Array.isArray(schema.questions) ? schema.questions : []
  } catch { return [] }
})
const detailQuestions = computed(() => {
  if (!detail.value?.templateVersion?.schemaJson) return []
  try {
    const schema = typeof detail.value.templateVersion.schemaJson === 'string'
      ? JSON.parse(detail.value.templateVersion.schemaJson)
      : detail.value.templateVersion.schemaJson
    return Array.isArray(schema.questions) ? schema.questions : []
  } catch { return [] }
})
const selectedReviewers = computed(() => reviewers.value.filter(item => detailReviewerIds.value.includes(item.id)))
const filteredReviewers = computed(() => {
  let list = reviewers.value
  if (selectedDepartment.value) {
    list = list.filter(item => item.departmentName === selectedDepartment.value)
  }
  const keyword = reviewerSearch.value.trim().toLowerCase()
  if (!keyword) return list
  return list.filter(item => `${item.realName} ${item.username}`.toLowerCase().includes(keyword))
})
const allFilteredReviewersSelected = computed(() => filteredReviewers.value.length > 0 && filteredReviewers.value.every(item => detailReviewerIds.value.includes(item.id)))
const availableDepartments = computed(() => {
  const departments = new Set(reviewers.value.map(item => item.departmentName).filter(Boolean))
  return Array.from(departments).sort()
})
const reviewTimeoutHours = computed(() => {
  const value = Number(reviewTimeoutValue.value)
  return reviewTimeoutUnit.value === 'DAYS' ? value * 24 : value
})
const reviewTimeoutValid = computed(() => Number.isInteger(Number(reviewTimeoutValue.value)) && reviewTimeoutHours.value >= 1 && reviewTimeoutHours.value <= 720)
const estimatedReviewDueAt = computed(() => {
  if (!reviewTimeoutValid.value) return null
  return new Date(Date.now() + reviewTimeoutHours.value * 60 * 60 * 1000)
})
const templatePreviewIssues = computed(() => {
  const questions = templatePreview.value?.questions || []
  if (!questions.length) return ['模板中没有配置题目']
  const issues = []
  const ids = new Set()
  questions.forEach((question, index) => {
    const label = `第 ${index + 1} 题`
    if (!String(question.title || '').trim()) issues.push(`${label}缺少题目标题`)
    if (!question.id) issues.push(`${label}缺少题目 ID`)
    else if (ids.has(question.id)) issues.push(`${label}的题目 ID“${question.id}”与其他题目重复`)
    else ids.add(question.id)
    if (!['TEXT', 'SINGLE', 'MULTIPLE', 'FILE', 'PRACTICAL'].includes(question.type)) issues.push(`${label}的题型不受支持`)
    if (['SINGLE', 'MULTIPLE'].includes(question.type)) {
      const options = question.options.filter(option => String(option).trim())
      if (options.length < 2) issues.push(`${label}至少需要两个有效选项`)
    }
  })
  return issues
})

async function request(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  if (token.value) headers.Authorization = `Bearer ${token.value}`
  if (options.body && !(options.body instanceof FormData)) headers['Content-Type'] = 'application/json'
  const response = await fetch(path, { ...options, headers })
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(body.message || `请求失败 (${response.status})`)
  return body
}

async function login() {
  loginError.value = ''
  loginMessage.value = ''
  loading.value = true
  try {
    const result = await request('/api/auth/login', { method: 'POST', body: JSON.stringify(loginForm.value) })
    token.value = result.accessToken
    user.value = result.user
    localStorage.setItem('assessment_token', result.accessToken)
    localStorage.setItem('assessment_user', JSON.stringify(result.user))
    await refresh()
    await openDeepLinkedTask()
  } catch (error) {
    loginError.value = error.message
  } finally {
    loading.value = false
  }
}

async function register() {
  registerError.value = ''
  registerMessage.value = ''
  registerLoading.value = true
  try {
    const result = await request('/api/auth/register', { method: 'POST', body: JSON.stringify(registerForm.value) })
    registerMessage.value = result.dingtalkMatchStatus === 'MATCHED'
      ? '注册申请已提交，钉钉账号已匹配，请等待管理员审核。'
      : '注册申请已提交，请等待管理员审核；钉钉账号将在匹配成功后用于接收通知。'
    registerForm.value = { username: '', password: '', realName: '', phone: '', requestedRole: 'HR', departmentName: '', positionName: '', dingtalkDepartmentId: null }
    dingtalkProfileMessage.value = ''
    lastDingtalkLookupPhone.value = ''
    loginMessage.value = registerMessage.value
    showRegister.value = false
  } catch (error) {
    registerError.value = error.message
  } finally {
    registerLoading.value = false
  }
}

async function lookupDingTalkProfile(force = false) {
  if (dingtalkProfileLoading.value) return
  const phone = registerForm.value.phone.trim()
  if (!phone || (!force && phone === lastDingtalkLookupPhone.value)) return
  dingtalkProfileLoading.value = true
  dingtalkProfileMessage.value = ''
  try {
    const result = await request('/api/auth/dingtalk-profile', {
      method: 'POST',
      body: JSON.stringify({ phone })
    })
    lastDingtalkLookupPhone.value = phone
    dingtalkProfileMessage.value = result.message || (result.matched ? '已读取钉钉信息' : '未匹配到钉钉信息')
    if (result.matched) {
      if (result.name && !registerForm.value.realName.trim()) registerForm.value.realName = result.name
      if (result.departmentName) registerForm.value.departmentName = result.departmentName
      if (result.positionName) registerForm.value.positionName = result.positionName
      registerForm.value.dingtalkDepartmentId = result.departmentId || null
    }
  } catch (error) {
    dingtalkProfileMessage.value = error.message
  } finally {
    dingtalkProfileLoading.value = false
  }
}

function changeRequestedDepartment() {
  registerForm.value.dingtalkDepartmentId = null
}

async function loadPendingUsers() {
  view.value = 'users'
  try { pendingUsers.value = await request('/api/users/pending') } catch (error) { message.value = error.message }
}

async function approvePendingUser(item) {
  try {
    await request(`/api/users/${item.id}/approve`, { method: 'POST' })
    message.value = `${item.realName} 已通过审核`
    await loadPendingUsers()
  } catch (error) { message.value = error.message }
}

async function rematchDingtalk(item) {
  try {
    const result = await request(`/api/users/${item.id}/dingtalk-match`, { method: 'POST' })
    message.value = result.dingtalkMatchStatus === 'MATCHED' ? '钉钉账号匹配成功' : '暂未匹配到钉钉账号'
    await loadPendingUsers()
  } catch (error) { message.value = error.message }
}

function logout() {
  closeRecord()
  token.value = ''
  user.value = null
  localStorage.removeItem('assessment_token')
  localStorage.removeItem('assessment_user')
}

async function restoreSession() {
  if (!token.value) return

  try {
    user.value = await request('/api/auth/me')
    localStorage.setItem('assessment_user', JSON.stringify(user.value))
    await refresh()
    await openDeepLinkedTask()
  } catch (error) {
    logout()
    loginError.value = '登录状态已失效，请重新登录'
  }
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

async function loadRecords() {
  view.value = 'records'
  recordLoading.value = true
  try {
    const query = new URLSearchParams({ page: recordPage.value, pageSize: recordPageSize.value })
    const currentFilters = recordFilters.value
    if (currentFilters.keyword.trim()) query.set('keyword', currentFilters.keyword.trim())
    if (currentFilters.positionId) query.set('positionId', currentFilters.positionId)
    if (currentFilters.conclusion) query.set('conclusion', currentFilters.conclusion)
    const requests = [request(`/api/assessment-records?${query}`)]
    if (!recordPositions.value.length) requests.push(request('/api/positions'))
    const [result, positionData] = await Promise.all(requests)
    records.value = result.items
    recordTotal.value = result.total
    if (positionData) recordPositions.value = positionData
  } catch (error) {
    message.value = error.message
  } finally {
    recordLoading.value = false
  }
}

function searchRecords() {
  recordPage.value = 1
  loadRecords()
}

function clearRecordFileUrls() {
  Object.values(recordFileUrls.value).forEach(url => URL.revokeObjectURL(url))
  recordFileUrls.value = {}
}

function closeRecord() {
  clearRecordFileUrls()
  recordDetail.value = null
}

async function openTaskRecord() {
  if (!taskRecordData.value) return
  recordDetail.value = taskRecordData.value
  await loadRecordFiles()
}

async function ensureDetailRecordTab() {
  await nextTick()
  const tabs = document.querySelector('.drawer:not(.record-drawer) .tabs')
  if (!tabs || !taskRecordData.value || tabs.querySelector('.detail-record-tab')) return
  tabs.parentElement.querySelectorAll('.detail-content > h3, .detail-content > .answer-row, .detail-content > .file-row').forEach(element => { element.style.display = 'none' })
  const tab = document.createElement('button')
  tab.type = 'button'
  tab.className = 'detail-record-tab'
  tab.textContent = '测评记录'
  tab.addEventListener('click', openTaskRecord)
  tabs.insertBefore(tab, tabs.children[1] || null)
}

function clearDetailFileUrls() {
  Object.values(detailFileUrls.value).forEach(url => URL.revokeObjectURL(url))
  detailFileUrls.value = {}
}

function closeDetail() {
  clearDetailFileUrls()
  detail.value = null
}

async function openRecord(id) {
  closeRecord()
  try {
    recordDetail.value = await request(`/api/assessment-records/${id}`)
    await loadRecordFiles()
  } catch (error) {
    message.value = error.message
  }
}

async function loadRecordFiles() {
  clearRecordFileUrls()
  if (!recordDetail.value) return
  const completedFiles = recordDetail.value.files.filter(file => file.uploadStatus === 'COMPLETED')
  const entries = await Promise.all(completedFiles.map(async file => {
    const response = await fetch(`/api/assessment-records/${recordDetail.value.id}/files/${file.id}`, {
      headers: { Authorization: `Bearer ${token.value}` }
    })
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      throw new Error(body.message || `附件读取失败 (${response.status})`)
    }
    return [file.id, URL.createObjectURL(await response.blob())]
  }))
  recordFileUrls.value = Object.fromEntries(entries)
}

function recordAnswer(questionId) {
  return recordDetail.value?.answers?.find(answer => answer.questionId === questionId)
}

function detailAnswer(questionId) {
  return detail.value?.answers?.find(answer => answer.questionId === questionId)
}

function detailFilesForQuestion(questionId) {
  return detail.value?.files?.filter(file => file.questionId === questionId && file.uploadStatus === 'COMPLETED') || []
}

function recordFilesForQuestion(questionId) {
  return recordDetail.value?.files?.filter(file => file.questionId === questionId && file.uploadStatus === 'COMPLETED') || []
}

function formatAnswer(value) {
  if (!value) return '未作答'
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) return parsed.join('、') || '未作答'
    if (parsed && typeof parsed === 'object') return JSON.stringify(parsed, null, 2)
    return String(parsed || '未作答')
  } catch { return value }
}

function questionType(value) {
  return { TEXT: '文本题', SINGLE: '单选题', MULTIPLE: '多选题', FILE: '文件上传', PRACTICAL: '综合实践题' }[value] || value
}

function conclusionText(value) {
  return { PASS: '通过', REJECTED: '不通过', RESERVED: '保留', ABANDONED: '放弃测试' }[value] || value || '未提交'
}

function conclusionClass(value) {
  return { PASS: 'conclusion-pass', REJECTED: 'conclusion-rejected', RESERVED: 'conclusion-reserved', ABANDONED: 'conclusion-abandoned' }[value] || ''
}

function isImageFile(file) { return file.contentType?.startsWith('image/') }
function isImageMaterial(material) {
  if (material.kind === '参考图片' || material.url?.startsWith('data:image/')) return true
  return /\.(jpe?g|png|gif|webp|bmp|tiff?)(?:$|[?#])/i.test(material.name || material.url || '')
}

function formatFileSize(bytes) {
  if (!Number.isFinite(bytes)) return '--'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function positionName(positionId) {
  const position = templatePositions.value.find(item => item.id === positionId)
  return position ? formatPositionName(position) : `岗位 #${positionId}`
}

async function openDeepLinkedTask() {
  if (deepLinkTaskId == null) return
  await openDetail(deepLinkTaskId)
}

function materialExtension(material) {
  const source = String(material?.name || material?.url || '').split(/[?#]/)[0]
  return source.includes('.') ? source.substring(source.lastIndexOf('.') + 1).toLowerCase() : ''
}

function materialPreviewKind(material) {
  const extension = materialExtension(material)
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(extension) || material?.url?.startsWith('data:image/')) return 'image'
  if (extension === 'pdf' || material?.url?.startsWith('data:application/pdf')) return 'pdf'
  if (['txt', 'md', 'csv'].includes(extension) || material?.url?.startsWith('data:text/')) return 'text'
  if (['mp3', 'wav', 'm4a'].includes(extension)) return 'audio'
  if (['mp4', 'webm'].includes(extension)) return 'video'
  return 'download'
}

function materialTypeText(material) {
  return {
    image: '图片', pdf: 'PDF', text: '文本', audio: '音频', video: '视频', download: '文件',
  }[materialPreviewKind(material)]
}

async function openMaterialPreview(material) {
  const kind = materialPreviewKind(material)
  materialPreview.value = { material, kind, content: '', loading: kind === 'text', error: '' }
  if (kind !== 'text') return
  try {
    const response = await fetch(material.url)
    if (!response.ok) throw new Error(`文件读取失败 (${response.status})`)
    const declaredSize = Number(response.headers.get('content-length'))
    if (declaredSize > 1024 * 1024) throw new Error('文本文件超过 1 MB，请下载后查看')
    const content = await response.text()
    if (content.length > 1024 * 1024) throw new Error('文本文件超过 1 MB，请下载后查看')
    if (materialPreview.value?.material === material) materialPreview.value.content = content
  } catch (error) {
    if (materialPreview.value?.material === material) materialPreview.value.error = error.message
  } finally {
    if (materialPreview.value?.material === material) materialPreview.value.loading = false
  }
}

function closeTemplatePreview() {
  materialPreview.value = null
  templatePreview.value = null
}

function formatPositionName(position) {
  return [position.departmentName, position.positionName].filter(Boolean).join(' - ')
}

function addQuestion() {
  templateForm.value.questions.push({ id: `q${templateForm.value.questions.length + 1}`, type: 'TEXT', title: '', options: ['', ''], materials: [], submission: { files: false, fileTypes: '' } })
}

function removeQuestion(index) { templateForm.value.questions.splice(index, 1) }

function questionNeedsOptions(question) { return ['SINGLE', 'MULTIPLE'].includes(question.type) }
function questionNeedsFiles(question) { return question.type === 'FILE' }
function addMaterial(question) {
  question.materials.push({ name: '', url: '', kind: '参考文件' })
  setTimeout(() => document.querySelectorAll('.material-row input[type="file"]').forEach(input => input.removeAttribute('accept')))
}
function removeMaterial(question, index) { question.materials.splice(index, 1) }
async function uploadMaterial(file) {
  const form = new FormData()
  form.append('file', file, file.name)
  return request('/api/assessment-materials', { method: 'POST', body: form })
}

async function uploadLegacyMaterial(material) {
  if (!material.url?.startsWith('data:')) return material
  const blob = await fetch(material.url).then(response => response.blob())
  const file = new File([blob], material.name || 'material', { type: blob.type })
  return uploadMaterial(file)
}

async function handleMaterialFile(question, event, material) {
  const file = event.target.files?.[0]
  if (!file) return
  material.name = file.name
  material.kind = file.type.startsWith('image/') ? '参考图片' : '参考文件'
  material.uploading = true
  material.uploadPromise = uploadMaterial(file)
  try {
    Object.assign(material, await material.uploadPromise)
  } catch (error) {
    material.url = ''
    message.value = error.message
  } finally {
    material.uploading = false
    material.uploadPromise = null
  }
}

function openTemplateCreate() {
  if (!availableTemplatePositions.value.length) {
    message.value = '所有岗位都已有模板，请在模板列表中使用“替换模板”'
    return
  }
  editingTemplateVersion.value = null
  templateForm.value = { templateName: '', positionId: '', questions: [] }
  addQuestion()
  showTemplateCreate.value = true
  disableNativeTemplateValidation()
}

function disableNativeTemplateValidation() {
  setTimeout(() => document.querySelector('.template-editor form')?.setAttribute('novalidate', ''))
}

async function createTemplate() {
  if (templateSaving.value) return
  const invalidQuestion = templateForm.value.questions.findIndex(question => !question.title?.trim())
  if (!templateForm.value.templateName.trim() || !templateForm.value.positionId || invalidQuestion >= 0) {
    message.value = invalidQuestion >= 0
      ? `请填写第 ${invalidQuestion + 1} 题的题目标题`
      : '请填写模板名称和适用岗位'
    return
  }
  templateSaving.value = true
  message.value = '正在保存草稿模板...'
  try {
    const materialLists = templateForm.value.questions.flatMap(question => question.materials || [])
    await Promise.all(materialLists.map(async material => {
      if (material.uploadPromise) await material.uploadPromise
      if (material.url?.startsWith('data:')) Object.assign(material, await uploadLegacyMaterial(material))
    }))
    const questions = templateForm.value.questions.map((question, index) => ({
      id: question.id || `q${index + 1}`,
      type: question.type,
      title: question.title.trim(),
      required: question.required,
      ...(questionNeedsOptions(question) ? { options: question.options.filter(item => item.trim()) } : {}),
      ...(questionNeedsFiles(question) ? { submission: { ...question.submission } } : {}),
      ...(question.materials?.length ? { materials: question.materials
        .filter(material => material.name && material.url)
        .map(material => ({ name: material.name, url: material.url, kind: material.kind })) } : {}),
    }))
    const body = JSON.stringify({ questions })
    const result = editingTemplateVersion.value
      ? await request(`/api/assessment-templates/${editingTemplateVersion.value.templateId}/versions`, { method: 'POST', body: JSON.stringify({ schemaJson: body }) })
      : await request('/api/assessment-templates', { method: 'POST', body: JSON.stringify({ templateName: templateForm.value.templateName.trim(), positionId: Number(templateForm.value.positionId), schemaJson: body }) })
    showTemplateCreate.value = false
    message.value = editingTemplateVersion.value ? `已生成 v${result.versionNo} 草稿版本，请发布后生效` : '模板创建成功，版本 v1 处于草稿状态'
    editingTemplateVersion.value = null
    await loadTemplates()
    templateDraft.value = result
  } catch (error) {
    message.value = error.message
  } finally {
    templateSaving.value = false
  }
}

function editTemplateVersion(version) {
  try {
    const schema = typeof version.schemaJson === 'string' ? JSON.parse(version.schemaJson) : (version.schemaJson || {})
    templateForm.value = {
      templateName: templateDraft.value.template.templateName,
      positionId: String(templateDraft.value.template.positionId),
      questions: (schema.questions || []).map((question, index) => ({ id: question.id || `q${index + 1}`, type: question.type || 'TEXT', title: question.title || '', required: question.required !== false, options: question.options || ['', ''], materials: question.materials || [], submission: question.submission || { files: false, fileTypes: '' } }))
    }
    editingTemplateVersion.value = version
    templatePreview.value = null
    templateDraft.value = null
    showTemplateCreate.value = true
    disableNativeTemplateValidation()
  } catch (error) { message.value = '模板内容格式无效' }
}

async function openTemplateVersions(template) {
  try { templateDraft.value = { template, versions: await request(`/api/assessment-templates/${template.id}/versions`) } } catch (error) { message.value = error.message }
}

async function replaceTemplate(template) {
  try {
    const versions = await request(`/api/assessment-templates/${template.id}/versions`)
    const latest = [...versions].sort((left, right) => right.versionNo - left.versionNo)[0]
    if (!latest) throw new Error('模板没有可替换的历史版本')
    templateDraft.value = { template, versions }
    editTemplateVersion(latest)
  } catch (error) { message.value = error.message }
}

function previewTemplateVersion(version) {
  try {
    const schema = typeof version.schemaJson === 'string' ? JSON.parse(version.schemaJson) : (version.schemaJson || {})
    if (!schema || typeof schema !== 'object' || !Array.isArray(schema.questions)) throw new Error('INVALID_SCHEMA')
    const questions = schema.questions.map(question => {
      const normalized = question && typeof question === 'object' && !Array.isArray(question) ? question : {}
      return {
        ...normalized,
        options: Array.isArray(normalized.options) ? normalized.options : [],
        materials: Array.isArray(normalized.materials)
          ? normalized.materials.map(material => material && typeof material === 'object' ? material : {})
          : [],
      }
    })
    templatePreview.value = { template: templateDraft.value?.template, version, questions }
  } catch (error) { message.value = '模板内容格式无效' }
}

async function publishTemplate(version) {
  try {
    await request(`/api/assessment-template-versions/${version.id}/publish`, { method: 'POST' })
    message.value = '模板版本已发布'
    await loadTemplates()
    await refreshTemplateVersions()
  } catch (error) { message.value = error.message }
}

async function refreshTemplateVersions() {
  if (!templateDraft.value?.template) return
  const templateId = templateDraft.value.template.id
  const template = templateList.value.find(item => item.id === templateId) || templateDraft.value.template
  templateDraft.value = { template, versions: await request(`/api/assessment-templates/${templateId}/versions`) }
}

async function deleteTemplateVersion(version) {
  if (!window.confirm(`确定删除模板版本 v${version.versionNo}？删除后无法恢复。`)) return
  try {
    await request(`/api/assessment-template-versions/${version.id}`, { method: 'DELETE' })
    if (templatePreview.value?.version.id === version.id) templatePreview.value = null
    message.value = `模板版本 v${version.versionNo} 已删除`
    await refreshTemplateVersions()
  } catch (error) { message.value = error.message }
}

async function openCreate() {
  showCreate.value = true
  createForm.value.deadline = defaultDeadline()
  try {
    const [positionData, templateData] = await Promise.all([
      request('/api/positions'), request('/api/assessment-templates')
    ])
    positions.value = positionData.filter(item => item.status === 'ACTIVE')
    templates.value = templateData.filter(item => item.status === 'ACTIVE')
  } catch (error) { message.value = error.message }
}

function handlePositionChange() {
  createForm.value.templateId = ''
  createForm.value.templateVersionId = ''
  templateVersions.value = []
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
    const payload = { ...createForm.value, positionId: Number(createForm.value.positionId), templateVersionId: Number(createForm.value.templateVersionId), deadline: new Date(createForm.value.deadline).toISOString() }
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
  closeDetail()
  closeRecord()
  try {
    const result = await request(`/api/assessment-tasks/${id}`)
    taskRecordData.value = ['SUBMITTED', 'REVIEWING', 'REVIEWED', 'ARCHIVED'].includes(result.status) ? result : null
    detail.value = taskRecordData.value
      ? { ...result, templateVersion: { ...result.templateVersion, schemaJson: '{}' }, answers: [], files: [] }
      : result
    if (!taskRecordData.value) message.value = '候选人尚未提交测评，完整题目、答案和附件将在提交后显示'
    detailTab.value = 'overview'
    extendDeadline.value = detail.value.deadline.slice(0, 16)
    detailReviewerIds.value = detail.value.assignments.filter(item => item.status !== 'CANCELLED').map(item => item.reviewerUserId)
    reviewTimeoutValue.value = 1
    reviewTimeoutUnit.value = 'DAYS'
    if (detail.value.status === 'SUBMITTED') reviewers.value = await request('/api/reviewers')
    await ensureDetailRecordTab()
  } catch (error) { message.value = error.message }
}

async function loadDetailFiles() {
  clearDetailFileUrls()
  if (!detail.value) return
  const completedFiles = detail.value.files.filter(file => file.uploadStatus === 'COMPLETED')
  const entries = await Promise.all(completedFiles.map(async file => {
    const response = await fetch(`/api/assessment-tasks/${detail.value.id}/files/${file.id}`, {
      headers: { Authorization: `Bearer ${token.value}` }
    })
    if (!response.ok) throw new Error(`附件读取失败 (${response.status})`)
    return [file.id, URL.createObjectURL(await response.blob())]
  }))
  detailFileUrls.value = Object.fromEntries(entries)
}

async function assignReviewers() {
  if (!detail.value || !detailReviewerIds.value.length || assigningReviewers.value) return
  if (!reviewTimeoutValid.value) {
    message.value = reviewTimeoutUnit.value === 'DAYS' ? '评估时限请输入 1 至 30 天的整数' : '评估时限请输入 1 至 720 小时的整数'
    return
  }
  assigningReviewers.value = true
  try {
    const updated = await request(`/api/assessment-tasks/${detail.value.id}/reviewers`, { method: 'POST', body: JSON.stringify({ reviewerUserIds: detailReviewerIds.value.map(Number), reviewTimeoutHours: reviewTimeoutHours.value }) })
    taskRecordData.value = updated
    detail.value = taskRecordData.value
      ? { ...updated, templateVersion: { ...updated.templateVersion, schemaJson: '{}' }, answers: [], files: [] }
      : updated
    message.value = '评估人员分配成功'
    await refresh()
  } catch (error) { message.value = error.message } finally { assigningReviewers.value = false }
}

function toggleAllFilteredReviewers() {
  const ids = new Set(detailReviewerIds.value)
  if (allFilteredReviewersSelected.value) filteredReviewers.value.forEach(item => ids.delete(item.id))
  else filteredReviewers.value.forEach(item => ids.add(item.id))
  detailReviewerIds.value = [...ids]
}

function openReviewerPicker() {
  reviewerSearch.value = ''
  selectedDepartment.value = ''
  showReviewerPicker.value = true
}

async function operate(action) {
  if (!detail.value) return
  if (action === 'extend') {
    const deadline = new Date(extendDeadline.value).toISOString()
    await runAction(`/api/assessment-tasks/${detail.value.id}/extend`, { deadline })
  } else if (action === 'send') {
    await runAction(`/api/assessment-tasks/${detail.value.id}/send`)
  } else if (action === 'regenerate-link') {
    await regenerateLink()
  } else {
    await runAction(`/api/assessment-tasks/${detail.value.id}/${action}`)
  }
}

async function regenerateLink() {
  if (!detail.value) return
  try {
    const result = await request(`/api/assessment-tasks/${detail.value.id}/regenerate-link`, { method: 'POST' })
    createdLink.value = result.assessmentUrl
    message.value = '新测试链接已生成，旧链接已失效'
    detail.value = await request(`/api/assessment-tasks/${detail.value.id}`)
  } catch (error) { message.value = error.message }
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

function isReviewOverdue(assignment) {
  return assignment.reviewDueAt && !['COMPLETED', 'CANCELLED'].includes(assignment.status) && new Date(assignment.reviewDueAt) < new Date()
}

function statusText(value) { return statusLabels[value] || value }
function operationActionText(value) { return operationActionLabels[value] || value }
function statusClass(value) { return `status-${String(value).toLowerCase()}` }
function versionStatusText(value) { return { DRAFT: '草稿', PENDING: '待审核', PUBLISHED: '已发布', ARCHIVED: '历史版本' }[value] || value }

async function copyCreatedLink() {
  const assessmentUrl = createdLink.value
  let copied = false

  if (window.isSecureContext && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(assessmentUrl)
      copied = true
    } catch (_) {
      copied = false
    }
  }

  if (!copied && createdLinkInput.value) {
    createdLinkInput.value.focus()
    createdLinkInput.value.select()
    createdLinkInput.value.setSelectionRange(0, assessmentUrl.length)
    try {
      copied = document.execCommand('copy')
    } catch (_) {
      copied = false
    }
  }

  if (copied) {
    createdLink.value = ''
    message.value = '测试链接已复制'
  } else {
    message.value = '浏览器未允许自动复制，链接已选中，请按 Ctrl+C'
  }
}

onMounted(restoreSession)
onUnmounted(() => { clearRecordFileUrls(); clearDetailFileUrls() })
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
        <p v-if="loginMessage" class="success-text">{{ loginMessage }}</p>
        <button class="primary-button" :disabled="loading">{{ loading ? '登录中...' : '登录工作台' }}</button>
      </form>
      <button type="button" class="outline-button login-register-button" @click="showRegister = true">注册</button>
    </section>
  </main>

  <div v-if="showRegister" class="modal-backdrop">
    <section class="link-modal register-modal">
      <header><div><p class="eyebrow">INTERNAL ACCOUNT</p><h2>申请内部账号</h2></div><button class="close-button" @click="showRegister = false">×</button></header>
      <p class="muted">注册后需要管理员审核。手机号用于匹配钉钉账号，暂不进行短信验证。</p>
      <form class="login-form" @submit.prevent="register">
        <label>姓名<input v-model="registerForm.realName" required maxlength="80" /></label>
        <label>账号<input v-model="registerForm.username" required maxlength="80" autocomplete="username" /></label>
        <label>密码<input v-model="registerForm.password" required minlength="8" type="password" autocomplete="new-password" /></label>
        <label>手机号<input v-model="registerForm.phone" required maxlength="30" inputmode="tel" @blur="lookupDingTalkProfile()" /></label>
        <p v-if="dingtalkProfileLoading || dingtalkProfileMessage" class="profile-message">{{ dingtalkProfileLoading ? '正在识别钉钉信息...' : dingtalkProfileMessage }}</p>
        <label>所在部门<input v-model="registerForm.departmentName" required maxlength="100" placeholder="请确认或补充所在部门" @input="changeRequestedDepartment" /></label>
        <label>所在岗位<input v-model="registerForm.positionName" required maxlength="100" placeholder="请确认或补充所在岗位" /></label>
        <label>申请角色<select v-model="registerForm.requestedRole"><option value="HR">HR</option><option value="REVIEWER">评估人员</option></select></label>
        <p v-if="registerError" class="error-text">{{ registerError }}</p>
        <p v-if="registerMessage" class="success-text">{{ registerMessage }}</p>
        <button class="primary-button" :disabled="registerLoading">{{ registerLoading ? '提交中...' : '提交注册申请' }}</button>
      </form>
    </section>
  </div>

  <main v-if="loggedIn" class="app-shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark small">RA</span><span>测评工作台</span></div>
      <nav><a class="nav-item" :class="{active: view === 'tasks'}" @click="view = 'tasks'">任务管理 <span>⌘</span></a><a class="nav-item" :class="{active: view === 'records'}" @click="loadRecords">测评记录</a><a class="nav-item" :class="{active: view === 'templates'}" @click="loadTemplates">模板管理</a><a v-if="canManageUsers" class="nav-item" :class="{active: view === 'users'}" @click="loadPendingUsers">账号审核 <span v-if="pendingUsers.length">{{ pendingUsers.length }}</span></a></nav>
      <div class="sidebar-foot"><span class="avatar">{{ user?.realName?.slice(0, 1) || '管' }}</span><div><strong>{{ user?.realName }}</strong><small>{{ user?.role }}</small></div><button class="icon-button" title="退出登录" @click="logout">↪</button></div>
    </aside>
    <section class="content">
      <div v-if="message" class="toast" @click="message = ''">{{ message }} <span>×</span></div>
      <template v-if="view === 'tasks'">
        <header class="topbar"><div><p class="eyebrow">OPERATIONS / TASKS</p><h1>测评任务</h1></div><div class="top-actions"><button class="outline-button" @click="refresh">↻ 刷新数据</button><button class="primary-button compact" @click="openCreate">＋ 创建测评任务</button></div></header>
        <section class="stat-grid">
          <article class="stat-card accent"><span>全部任务</span><strong>{{ stats.total }}</strong><small>当前权限范围内</small></article>
          <article v-for="status in ['SENT', 'OPENED', 'SUBMITTED', 'REVIEWED']" :key="status" class="stat-card"><span>{{ statusText(status) }}</span><strong>{{ stats.byStatus[status] || 0 }}</strong><small>{{ status }}</small></article>
        </section>
        <section class="toolbar"><div class="search-wrap"><span>⌕</span><input v-model="filters.keyword" @keyup.enter="search" placeholder="搜索任务编号、候选人或联系方式" /></div><select v-model="filters.status" @change="search"><option value="">全部状态</option><option v-for="status in statusOrder" :key="status" :value="status">{{ statusText(status) }}</option></select><button class="primary-button compact" @click="search">查询</button></section>
        <section class="table-section"><div class="section-heading"><div><h2>任务列表</h2><span>{{ total }} 条记录</span></div><span v-if="loading" class="loading">正在加载...</span></div>
          <div class="table-scroll"><table class="task-table"><thead><tr><th>任务编号</th><th>发放时间</th><th>候选人</th><th>应聘岗位</th><th>负责人</th><th>状态</th><th>截止时间</th><th>评估进度</th><th></th></tr></thead><tbody><tr v-for="task in tasks" :key="task.id"><td><button class="link-button" @click="openDetail(task.id)">{{ task.taskNo }}</button></td><td>{{ formatDate(task.sentAt) }}</td><td><strong>{{ task.candidateName }}</strong><small>{{ task.candidatePhone || task.candidateEmail || '--' }}</small></td><td>{{ task.positionName }}</td><td>{{ task.hrUserName }}</td><td><span class="status-pill" :class="statusClass(task.status)">{{ statusText(task.status) }}</span></td><td>{{ formatDate(task.deadline) }}</td><td>{{ task.completedAssignmentCount }}/{{ task.assignmentCount }} 人</td><td><button class="detail-button" @click="openDetail(task.id)">详情 →</button></td></tr><tr v-if="!tasks.length"><td colspan="9" class="empty">没有符合条件的任务</td></tr></tbody></table></div>
          <footer class="pagination"><span>第 {{ page }} / {{ totalPages }} 页</span><div><button :disabled="page <= 1" @click="page--; refresh()">上一页</button><button :disabled="page >= totalPages" @click="page++; refresh()">下一页</button></div></footer>
        </section>
      </template>
      <template v-else-if="view === 'records'">
        <header class="topbar"><div><p class="eyebrow">HISTORY / ASSESSMENT RECORDS</p><h1>测评记录</h1></div><div class="top-actions"><button class="outline-button" @click="loadRecords">↻ 刷新数据</button></div></header>
        <section class="record-toolbar">
          <div class="search-wrap"><span>⌕</span><input v-model="recordFilters.keyword" @keyup.enter="searchRecords" placeholder="搜索任务编号、候选人或联系方式" /></div>
          <select v-model="recordFilters.positionId"><option value="">全部岗位</option><option v-for="position in recordPositions" :key="position.id" :value="position.id">{{ position.positionName }}</option></select>
          <select v-model="recordFilters.conclusion"><option value="">全部结论</option><option value="PASS">通过</option><option value="REJECTED">不通过</option><option value="RESERVED">保留</option><option value="ABANDONED">放弃测试</option></select>
          <button class="primary-button compact" @click="searchRecords">查询</button>
        </section>
        <section class="table-section"><div class="section-heading"><div><h2>候选人历史测评</h2><span>{{ recordTotal }} 条记录</span></div><span v-if="recordLoading" class="loading">正在加载...</span></div>
          <div class="table-scroll"><table><thead><tr><th>候选人</th><th>岗位</th><th>任务编号</th><th>模板版本</th><th>评估结论</th><th>结论时间</th><th>归档时间</th><th>状态</th><th></th></tr></thead><tbody><tr v-for="record in records" :key="record.id"><td><strong>{{ record.candidateName }}</strong><small>{{ record.candidatePhone || record.candidateEmail || '--' }}</small></td><td>{{ record.positionName }}</td><td>{{ record.taskNo }}</td><td>v{{ record.templateVersionNo }}</td><td><div class="record-conclusions"><span v-for="(conclusion, index) in record.conclusions" :key="`${conclusion}-${index}`" class="conclusion-pill" :class="conclusionClass(conclusion)">{{ conclusionText(conclusion) }}</span><span v-if="!record.conclusions.length" class="muted">无评估结论</span></div></td><td>{{ formatDate(record.concludedAt) }}</td><td>{{ formatDate(record.archivedAt) }}</td><td><span class="status-pill" :class="statusClass(record.status)">{{ statusText(record.status) }}</span></td><td><button class="detail-button" @click="openRecord(record.id)">查看记录 →</button></td></tr><tr v-if="!records.length"><td colspan="9" class="empty">暂无符合条件的归档记录</td></tr></tbody></table></div>
          <footer class="pagination"><span>第 {{ recordPage }} / {{ recordTotalPages }} 页</span><div><button :disabled="recordPage <= 1" @click="recordPage--; loadRecords()">上一页</button><button :disabled="recordPage >= recordTotalPages" @click="recordPage++; loadRecords()">下一页</button></div></footer>
        </section>
      </template>
      <template v-else-if="view === 'templates'">
        <header class="topbar"><div><p class="eyebrow">CONFIGURATION / TEMPLATES</p><h1>模板管理</h1></div><div class="top-actions"><button class="outline-button" @click="loadTemplates">↻ 刷新数据</button><button class="primary-button compact" @click="openTemplateCreate">＋ 新建模板</button></div></header>
        <section class="table-section"><div class="section-heading"><div><h2>测评模板</h2><span>{{ templateList.length }} 个模板</span></div><span v-if="templateLoading" class="loading">正在加载...</span></div><div class="table-scroll"><table><thead><tr><th>模板名称</th><th>适用岗位</th><th>状态</th><th>模板负责人</th><th>操作</th></tr></thead><tbody><tr v-for="template in templateList" :key="template.id"><td><strong>{{ template.templateName }}</strong></td><td>{{ positionName(template.positionId) }}</td><td><span class="status-pill" :class="template.status === 'ACTIVE' ? 'status-reviewed' : ''">{{ template.status === 'ACTIVE' ? '启用' : '草稿' }}</span></td><td>#{{ template.ownerUserId }}</td><td><div class="version-actions"><button class="outline-button" @click="replaceTemplate(template)">替换模板</button><button class="detail-button" @click="openTemplateVersions(template)">版本历史 →</button></div></td></tr><tr v-if="!templateList.length"><td colspan="5" class="empty">暂无模板，请先新建模板</td></tr></tbody></table></div></section>
      </template>
      <template v-else-if="view === 'users'">
        <header class="topbar"><div><p class="eyebrow">ACCESS / REGISTRATION</p><h1>账号审核</h1></div><div class="top-actions"><button class="outline-button" @click="loadPendingUsers">↻ 刷新数据</button></div></header>
        <section class="table-section"><div class="section-heading"><div><h2>待审核注册申请</h2><span>{{ pendingUsers.length }} 条记录</span></div></div><div class="table-scroll"><table><thead><tr><th>申请人</th><th>账号</th><th>手机号</th><th>所在部门</th><th>所在岗位</th><th>申请角色</th><th>钉钉匹配</th><th>申请时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in pendingUsers" :key="item.id"><td><strong>{{ item.realName }}</strong></td><td>{{ item.username }}</td><td>{{ item.phone || '--' }}</td><td>{{ item.departmentName || '--' }}</td><td>{{ item.positionName || '--' }}</td><td>{{ item.requestedRole === 'REVIEWER' ? '评估人员' : 'HR' }}</td><td><span class="status-pill" :class="item.dingtalkMatchStatus === 'MATCHED' ? 'status-reviewed' : ''">{{ item.dingtalkName || item.dingtalkMatchStatus }}</span></td><td>{{ formatDate(item.createdAt) }}</td><td><div class="version-actions"><button class="detail-button" @click="rematchDingtalk(item)">重新匹配</button><button class="primary-button compact" @click="approvePendingUser(item)">通过审核</button></div></td></tr><tr v-if="!pendingUsers.length"><td colspan="9" class="empty">暂无待审核账号</td></tr></tbody></table></div></section>
      </template>
    </section>
  </main>

  <div v-if="detail" class="drawer-backdrop" @click.self="detail = null"><aside class="drawer"><header><div><p class="eyebrow">TASK DETAIL</p><h2>{{ detail.taskNo }}</h2></div><button class="close-button" title="关闭详情" @click="detail = null">×</button></header><div class="drawer-summary"><div><span>候选人</span><strong>{{ detail.candidateName }}</strong></div><span class="status-pill" :class="statusClass(detail.status)">{{ statusText(detail.status) }}</span></div><div class="detail-actions"><button v-if="detail.status === 'DRAFT'" class="primary-button compact" @click="operate('send')">发送任务</button><button v-if="['DRAFT','SENT','OPENED','IN_PROGRESS'].includes(detail.status)" class="outline-button" @click="operate('revoke')">撤回任务</button><button v-if="['DRAFT','SENT','OPENED','IN_PROGRESS'].includes(detail.status)" class="outline-button" @click="operate('regenerate-link')">重新生成链接</button><button v-if="!['REVOKED','ARCHIVED','REVIEWED'].includes(detail.status)" class="outline-button" @click="operate('extend')">保存延期</button><button v-if="['REVIEWED','REVOKED','EXPIRED'].includes(detail.status)" class="primary-button compact" @click="operate('archive')">归档任务</button></div><label v-if="!['REVOKED','ARCHIVED','REVIEWED'].includes(detail.status)" class="deadline-input">截止时间<input v-model="extendDeadline" type="datetime-local" /></label><div class="tabs"><button :class="{selected: detailTab === 'overview'}" @click="detailTab='overview'">概览</button><button :class="{selected: detailTab === 'review'}" @click="detailTab='review'">评估分配</button><button :class="{selected: detailTab === 'logs'}" @click="detailTab='logs'">操作日志</button></div><div v-if="detailTab === 'overview'" class="detail-content"><dl><dt>候选人联系方式</dt><dd>{{ detail.candidatePhone || '--' }} · {{ detail.candidateEmail || '--' }}</dd><dt>应聘岗位</dt><dd>{{ detail.position.name }}（{{ detail.position.code }}）</dd><dt>模板版本</dt><dd>v{{ detail.templateVersion.versionNo }} · {{ detail.templateVersion.status }}</dd><dt>截止时间</dt><dd>{{ formatDate(detail.deadline) }}</dd></dl><h3>答案（{{ detail.answers.length }}）</h3><div v-for="answer in detail.answers" :key="answer.id" class="answer-row"><strong>{{ answer.questionId }}</strong><code>{{ answer.answerJson }}</code></div><h3>附件（{{ detail.files.length }}）</h3><p v-if="!detail.files.length" class="muted">暂无附件</p><div v-for="file in detail.files" :key="file.id" class="file-row">{{ file.fileName }} <span>{{ file.sizeBytes }} bytes</span></div></div><div v-if="detailTab === 'review'" class="detail-content"><div v-if="detail.status === 'SUBMITTED' && detail.assignments.every(item => item.status === 'PENDING')" class="assignment-editor"><p class="muted">候选人已提交，请确认测评内容后选择评估人员。</p><div class="selected-reviewers"><span v-if="!selectedReviewers.length" class="muted">尚未选择评估人员</span><span v-for="reviewer in selectedReviewers" :key="reviewer.id" class="reviewer-chip">{{ reviewer.realName }}<button type="button" title="移除" @click="detailReviewerIds = detailReviewerIds.filter(id => id !== reviewer.id)">×</button></span></div><button type="button" class="outline-button" @click="openReviewerPicker">选择评估人员（{{ detailReviewerIds.length }}）</button><label class="review-timeout-field">评估时限<div class="review-timeout-control"><input v-model.number="reviewTimeoutValue" type="number" min="1" :max="reviewTimeoutUnit === 'DAYS' ? 30 : 720" step="1" inputmode="numeric" /><select v-model="reviewTimeoutUnit"><option value="DAYS">天</option><option value="HOURS">小时</option></select></div><small>预计截止：{{ formatDate(estimatedReviewDueAt) }}</small></label><button class="primary-button" :disabled="assigningReviewers || !detailReviewerIds.length || !reviewTimeoutValid" @click="assignReviewers">{{ assigningReviewers ? '保存中...' : '分配评估人员' }}</button></div><p v-if="!detail.assignments.length" class="muted">尚未分配评估人员</p><div v-for="assignment in detail.assignments" :key="assignment.id" class="assignment-row"><div><strong>{{ assignment.reviewerUserName }}</strong><small>{{ assignment.status }} · 截止 {{ formatDate(assignment.reviewDueAt) }}</small></div><div class="review-result"><span v-if="isReviewOverdue(assignment)" class="overdue-text">已超时</span>{{ assignment.conclusion || '未提交' }} <span v-if="assignment.score">{{ assignment.score }} 分</span></div></div></div><div v-if="detailTab === 'logs'" class="detail-content timeline"><div v-for="log in detail.operationLogs" :key="log.id"><span>{{ formatDate(log.createdAt) }}</span><strong>{{ operationActionText(log.action) }}</strong><small>{{ log.fromStatus ? statusText(log.fromStatus) : '--' }} → {{ log.toStatus ? statusText(log.toStatus) : '--' }}</small></div></div></aside></div>
  <div v-if="showReviewerPicker" class="modal-backdrop reviewer-picker-backdrop" @click.self="showReviewerPicker = false"><section class="reviewer-picker"><header><div><p class="eyebrow">REVIEWER SELECTION</p><h2>选择评估人员</h2></div><button class="close-button" title="关闭选择窗口" @click="showReviewerPicker = false">×</button></header><div class="reviewer-picker-toolbar"><label>筛选部门<select v-model="selectedDepartment"><option value="">全部部门</option><option v-for="department in availableDepartments" :key="department" :value="department">{{ department }}</option></select></label><label>搜索评估人员<input v-model="reviewerSearch" placeholder="搜索姓名或账号" /></label><label class="reviewer-picker-action">批量操作<button type="button" class="detail-button" @click="toggleAllFilteredReviewers">{{ allFilteredReviewersSelected ? '取消全选' : '全选当前结果' }}</button></label></div><div class="reviewer-picker-list"><label v-for="reviewer in filteredReviewers" :key="reviewer.id" class="reviewer-option"><input v-model="detailReviewerIds" type="checkbox" :value="reviewer.id" /> <span>{{ reviewer.username }} {{ reviewer.realName }}</span></label><p v-if="!filteredReviewers.length" class="empty">没有匹配的评估人员</p></div><footer class="modal-footer"><span class="muted">已选择 {{ detailReviewerIds.length }} 人</span><button type="button" class="outline-button" @click="showReviewerPicker = false">完成选择</button></footer></section></div>
  <div v-if="recordDetail" class="drawer-backdrop record-backdrop" @click.self="closeRecord"><aside class="drawer record-drawer"><header><div><p class="eyebrow">ASSESSMENT RECORD</p><h2>{{ recordDetail.candidateName }}的测评记录</h2><p class="muted">{{ recordDetail.taskNo }} · {{ recordDetail.position.name }}</p></div><button class="close-button" title="关闭记录" @click="closeRecord">×</button></header><section class="record-summary"><div><span>模板版本</span><strong>v{{ recordDetail.templateVersion.versionNo }}</strong></div><div><span>提交时间</span><strong>{{ formatDate(recordDetail.submittedAt) }}</strong></div><div><span>结论时间</span><strong>{{ formatDate(recordDetail.finalConclusionAt || recordDetail.reviewedAt) }}</strong></div><div><span>记录状态</span><strong>{{ statusText(recordDetail.status) }}</strong></div></section><section class="record-section"><div class="record-section-heading"><h3>测评内容</h3><span>{{ recordQuestions.length }} 道题</span></div><article v-for="(question, questionIndex) in recordQuestions" :key="question.id || questionIndex" class="record-question"><div class="review-question-head"><strong>题目 {{ questionIndex + 1 }}</strong><small>{{ questionType(question.type) }}</small></div><p class="record-question-title">{{ question.title }}</p><div v-if="question.materials?.length" class="review-question-resources"><strong>参考资料</strong><div class="review-resource-grid"><div v-for="(material, materialIndex) in question.materials" :key="materialIndex" class="review-resource"><img v-if="isImageMaterial(material)" :src="material.url" :alt="material.name || '参考图片'" loading="lazy" /><a v-else :href="material.url" :download="material.name || '参考文件'" target="_blank" rel="noopener">{{ material.name || '参考文件' }}<span>下载</span></a></div></div></div><div class="record-answer"><strong>候选人答案</strong><p>{{ formatAnswer(recordAnswer(question.id)?.answerJson) }}</p></div><div v-if="recordFilesForQuestion(question.id).length" class="review-question-submissions"><strong>候选人附件</strong><div v-for="file in recordFilesForQuestion(question.id)" :key="file.id" class="review-file"><div v-if="isImageFile(file)" class="review-file-preview"><img v-if="recordFileUrls[file.id]" :src="recordFileUrls[file.id]" :alt="file.fileName" /><span v-else>图片加载中...</span></div><div class="review-file-info"><div class="review-file-title"><a v-if="recordFileUrls[file.id]" :href="recordFileUrls[file.id]" :download="file.fileName" target="_blank" rel="noopener">{{ file.fileName }}</a><strong v-else>{{ file.fileName }}</strong></div><small>{{ file.contentType }} · {{ formatFileSize(file.sizeBytes) }}</small></div></div></div></article><p v-if="!recordQuestions.length" class="empty">历史模板题目读取失败</p></section><section class="record-section"><div class="record-section-heading"><h3>评估结果</h3><span>{{ recordDetail.finalConclusion ? '系统自动处理' : `${recordDetail.assignments.length} 位评估人员` }}</span></div><article v-if="recordDetail.finalConclusion" class="record-review"><div><strong>系统自动处理</strong><small>{{ formatDate(recordDetail.finalConclusionAt) }}</small></div><span class="conclusion-pill" :class="conclusionClass(recordDetail.finalConclusion)">{{ conclusionText(recordDetail.finalConclusion) }}</span><p>{{ recordDetail.finalConclusionReason || '截止时间内未提交测评' }}</p></article><article v-for="assignment in recordDetail.assignments" :key="assignment.id" class="record-review"><div><strong>{{ assignment.reviewerUserName }}</strong><small>{{ formatDate(assignment.reviewSubmittedAt || assignment.completedAt) }}</small></div><span class="conclusion-pill" :class="conclusionClass(assignment.conclusion)">{{ conclusionText(assignment.conclusion) }}</span><p v-if="assignment.reason">{{ assignment.reason }}</p><p v-else class="muted">未填写评估说明</p></article></section><section class="record-section"><div class="record-section-heading"><h3>操作时间线</h3><span>{{ recordDetail.operationLogs.length }} 条记录</span></div><div class="detail-content timeline record-timeline"><div v-for="log in recordDetail.operationLogs" :key="log.id"><span>{{ formatDate(log.createdAt) }}</span><strong>{{ operationActionText(log.action) }}</strong><small>{{ log.fromStatus ? statusText(log.fromStatus) : '--' }} → {{ log.toStatus ? statusText(log.toStatus) : '--' }}</small></div></div></section></aside></div>
  <div v-if="showCreate" class="modal-backdrop" @click.self="showCreate = false">
    <section class="create-modal">
      <header><div><p class="eyebrow">NEW ASSESSMENT TASK</p><h2>创建测评任务</h2></div><button class="close-button" title="关闭创建窗口" @click="showCreate = false">×</button></header>
      <form @submit.prevent="createTask" class="create-form">
        <div class="form-grid">
          <label>候选人姓名 *<input v-model="createForm.candidateName" required maxlength="80" placeholder="请输入候选人姓名" /></label>
          <label>手机号 *<input v-model="createForm.candidatePhone" required maxlength="30" pattern="\+?[0-9]{6,30}" placeholder="请输入手机号" /></label>
          <label>邮箱<input v-model="createForm.candidateEmail" type="email" maxlength="120" placeholder="选填" /></label>
          <label>候选人来源<input v-model="createForm.candidateSource" maxlength="50" placeholder="例如：招聘网站" /></label>
          <label>应聘岗位 *<select v-model="createForm.positionId" required @change="handlePositionChange"><option value="">请选择岗位</option><option v-for="position in positions" :key="position.id" :value="position.id">{{ formatPositionName(position) }}</option></select></label>
          <label>测评模板 *<select v-model="createForm.templateId" required :disabled="!createForm.positionId || !createTemplates.length" @change="loadVersions"><option value="">{{ createForm.positionId ? (createTemplates.length ? '请选择模板' : '该岗位暂无可用模板') : '请先选择岗位' }}</option><option v-for="template in createTemplates" :key="template.id" :value="template.id">{{ template.templateName }}</option></select></label>
          <label>模板版本 *<select v-model="createForm.templateVersionId" required><option value="">请先选择模板</option><option v-for="version in templateVersions" :key="version.id" :value="version.id">v{{ version.versionNo }} · {{ version.status }}</option></select></label>
          <label>截止时间 *<input v-model="createForm.deadline" type="datetime-local" required /></label>
        </div>
        <footer class="modal-footer"><button type="button" class="outline-button" @click="showCreate = false">取消</button><button type="submit" class="primary-button" :disabled="createLoading">{{ createLoading ? '创建中...' : '创建任务' }}</button></footer>
      </form>
    </section>
  </div>
  <div v-if="createdLink" class="modal-backdrop" @click.self="createdLink = ''"><section class="link-modal"><header><div><p class="eyebrow">ASSESSMENT LINK READY</p><h2>测试链接已生成</h2></div><button class="close-button" title="关闭链接窗口" @click="createdLink = ''">×</button></header><p class="muted">复制下面的链接，在浏览器中打开即可进入候选人测评页面。</p><input ref="createdLinkInput" class="link-field" :value="createdLink" readonly /><footer class="modal-footer"><button type="button" class="outline-button" @click="copyCreatedLink">复制链接</button></footer></section></div>
  <div v-if="showTemplateCreate" class="modal-backdrop" @click.self="showTemplateCreate = false"><section class="create-modal template-editor"><header><div><p class="eyebrow">NEW ASSESSMENT TEMPLATE</p><h2>{{ editingTemplateVersion ? '替换测评模板' : '新建测评模板' }}</h2><p class="muted">用同一套结构配置问卷、文本题和实践任务。</p></div><button class="close-button" @click="showTemplateCreate = false">×</button></header><form @submit.prevent="createTemplate" class="create-form"><div class="form-grid"><label>模板名称 *<input v-model="templateForm.templateName" :disabled="Boolean(editingTemplateVersion)" required placeholder="例如：Java开发工程师测评" /></label><label>适用岗位 *<select v-model="templateForm.positionId" :disabled="Boolean(editingTemplateVersion)" required><option value="">请选择岗位</option><option v-for="position in (editingTemplateVersion ? templatePositions : availableTemplatePositions)" :key="position.id" :value="position.id">{{ formatPositionName(position) }}</option></select></label><label>预计用时（分钟）<input v-model="templateForm.durationMinutes" type="number" min="1" /></label></div><div class="question-editor"><div class="section-heading"><div><h2>题目配置</h2><span>{{ templateForm.questions.length }} 道题</span></div><button type="button" class="outline-button" @click="addQuestion">＋ 添加题目</button></div><article v-for="(question, index) in templateForm.questions" :key="question.id" class="question-card"><div class="question-card-head"><strong>题目 {{ index + 1 }}</strong><button type="button" class="icon-button dark" @click="removeQuestion(index)">×</button></div><div class="form-grid"><label>题目标题 *<textarea v-model="question.title" required rows="3" placeholder="请输入候选人需要回答的问题"></textarea></label><label>题型<select v-model="question.type"><option value="TEXT">文本题</option><option value="SINGLE">单选题</option><option value="MULTIPLE">多选题</option><option value="FILE">文件上传</option><option value="PRACTICAL">综合实践题</option></select></label></div><label class="required-check"><input v-model="question.required" type="checkbox" /> 必填题</label><div v-if="questionNeedsOptions(question)" class="options-grid"><input v-for="(_, optionIndex) in question.options" :key="optionIndex" v-model="question.options[optionIndex]" :placeholder="`选项 ${optionIndex + 1}`" /><button type="button" class="detail-button" @click="question.options.push('')">＋选项</button></div><div class="material-box"><div class="material-head"><strong>候选人参考资料</strong><button type="button" class="detail-button" @click="addMaterial(question)">＋ 添加文件</button></div><div v-for="(material, materialIndex) in question.materials" :key="materialIndex" class="material-row"><span class="material-name">{{ material.name || '未选择文件' }}</span><input type="file" accept="image/*,.pdf,.doc,.docx,.zip,.rar,.blend,.ma,.max" @change="handleMaterialFile(question, $event, material)" /><button type="button" class="icon-button dark" title="删除文件" @click="removeMaterial(question, materialIndex)">×</button></div><p v-if="!question.materials.length" class="muted">上传后候选人可在答题时查看。</p></div><div v-if="questionNeedsFiles(question)" class="advanced-box"><strong>提交设置</strong><label class="required-check"><input v-model="question.submission.files" type="checkbox" /> 要求候选人上传文件</label><input v-model="question.submission.fileTypes" placeholder="允许格式，例如 .png, .blend, .pdf" /></div></article></div><footer class="modal-footer"><button type="button" class="outline-button" @click="showTemplateCreate = false">取消</button><button type="submit" class="primary-button" :disabled="templateSaving">{{ templateSaving ? '保存中...' : editingTemplateVersion ? '保存为新版本' : '保存草稿模板' }}</button></footer></form></section></div>
  <div v-if="templateDraft?.versions" class="modal-backdrop" @click.self="templateDraft = null"><section class="link-modal version-modal"><header><div><p class="eyebrow">VERSION HISTORY</p><h2>{{ templateDraft.template.templateName }}</h2></div><button class="close-button" @click="templateDraft = null">×</button></header><div v-for="version in templateDraft.versions" :key="version.id" class="version-row"><div><strong>版本 v{{ version.versionNo }}</strong><small>{{ versionStatusText(version.status) }}</small></div><div class="version-actions"><button type="button" class="detail-button" @click="previewTemplateVersion(version)">查看</button><button type="button" class="outline-button" @click="editTemplateVersion(version)">选择编辑</button><button v-if="canPublish && ['DRAFT','PENDING','ARCHIVED'].includes(version.status)" class="primary-button compact" @click="publishTemplate(version)">发布</button><button v-if="canPublish && version.status !== 'PUBLISHED'" type="button" class="outline-button danger-button" @click="deleteTemplateVersion(version)">删除</button><span v-if="version.status === 'PUBLISHED'" class="status-pill status-reviewed">当前发布</span></div></div></section></div>
  <div v-if="templatePreview" class="modal-backdrop template-preview-backdrop" @click.self="closeTemplatePreview">
    <section class="link-modal template-preview">
      <header>
        <div><p class="eyebrow">TEMPLATE PREVIEW</p><h2>{{ templatePreview.template?.templateName || '模板内容' }}</h2><p class="template-preview-meta">版本 v{{ templatePreview.version.versionNo }} · {{ versionStatusText(templatePreview.version.status) }} · {{ templatePreview.questions.length }} 道题</p></div>
        <button class="close-button" title="关闭模板预览" @click="closeTemplatePreview">×</button>
      </header>
      <div v-if="templatePreviewIssues.length" class="template-preview-warning"><strong>发现 {{ templatePreviewIssues.length }} 项配置问题</strong><ul><li v-for="issue in templatePreviewIssues" :key="issue">{{ issue }}</li></ul></div>
      <div v-else class="template-preview-valid">模板结构检查通过</div>
      <article v-for="(question, index) in templatePreview.questions" :key="question.id || index" class="preview-question">
        <div class="preview-question-head"><strong>题目 {{ index + 1 }}</strong><span>{{ questionType(question.type) }}<em v-if="question.required !== false">必填</em></span></div>
        <p>{{ question.title || '未填写题目标题' }}</p>
        <ul v-if="questionNeedsOptions(question)"><li v-for="(option, optionIndex) in question.options || []" :key="optionIndex">{{ option || '空选项' }}</li></ul>
        <div v-if="question.type === 'FILE'" class="preview-submission"><strong>文件提交要求</strong><span>{{ question.submission?.files === false ? '未要求上传文件' : '需要上传文件' }}</span><span v-if="question.submission?.fileTypes">允许格式：{{ question.submission.fileTypes }}</span></div>
        <div v-if="question.materials?.length" class="preview-materials">
          <strong>参考资料</strong>
          <div v-for="(material, materialIndex) in question.materials" :key="materialIndex" class="preview-material-row">
            <div><span class="preview-file-name">{{ material.name || '未命名文件' }}</span><small>{{ materialTypeText(material) }}</small></div>
            <button v-if="materialPreviewKind(material) !== 'download'" type="button" class="detail-button" @click="openMaterialPreview(material)">预览</button>
            <a v-else class="outline-button preview-download" :href="material.url" :download="material.name || '参考资料'">下载查看</a>
          </div>
        </div>
      </article>
      <p v-if="!templatePreview.questions.length" class="empty preview-empty">暂无题目内容</p>
      <footer class="modal-footer"><button type="button" class="outline-button" @click="closeTemplatePreview">关闭</button><button type="button" class="primary-button compact" @click="editTemplateVersion(templatePreview.version)">选择编辑</button></footer>
    </section>
  </div>
  <div v-if="materialPreview" class="modal-backdrop material-preview-backdrop" @click.self="materialPreview = null">
    <section class="material-preview-modal">
      <header><div><p class="eyebrow">FILE PREVIEW</p><h2>{{ materialPreview.material.name || '参考资料' }}</h2></div><button class="close-button" title="关闭文件预览" @click="materialPreview = null">×</button></header>
      <div class="material-preview-body">
        <p v-if="materialPreview.loading" class="material-preview-state">正在读取文件...</p>
        <p v-else-if="materialPreview.error" class="material-preview-state error-text">{{ materialPreview.error }}</p>
        <img v-else-if="materialPreview.kind === 'image'" :src="materialPreview.material.url" :alt="materialPreview.material.name || '参考图片'" @error="materialPreview.error = '图片加载失败，请下载后查看'" />
        <object v-else-if="materialPreview.kind === 'pdf'" :data="materialPreview.material.url" type="application/pdf"><p class="material-preview-state">浏览器无法显示 PDF，请下载后查看。</p></object>
        <pre v-else-if="materialPreview.kind === 'text'">{{ materialPreview.content }}</pre>
        <audio v-else-if="materialPreview.kind === 'audio'" :src="materialPreview.material.url" controls @error="materialPreview.error = '音频加载失败，请下载后查看'"></audio>
        <video v-else-if="materialPreview.kind === 'video'" :src="materialPreview.material.url" controls @error="materialPreview.error = '视频加载失败，请下载后查看'"></video>
      </div>
      <footer class="modal-footer"><a class="outline-button preview-download" :href="materialPreview.material.url" :download="materialPreview.material.name || '参考资料'">下载文件</a><button type="button" class="primary-button compact" @click="materialPreview = null">关闭</button></footer>
    </section>
  </div>
</template>
