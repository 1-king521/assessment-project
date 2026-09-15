<script setup>
import { computed, onMounted, ref } from 'vue'

const token = ref(localStorage.getItem('assessment_token') || '')
const user = ref(JSON.parse(localStorage.getItem('assessment_user') || 'null'))
const loginForm = ref({ username: '', password: '' })
const loginError = ref('')
const loginMessage = ref('')
const showRegister = ref(false)
const registerError = ref('')
const registerLoading = ref(false)
const dingtalkProfileLoading = ref(false)
const dingtalkProfileMessage = ref('')
const lastDingtalkLookupPhone = ref('')
const registerForm = ref({ username: '', password: '', realName: '', phone: '', requestedRole: 'REVIEWER', departmentName: '', positionName: '', dingtalkDepartmentId: null })
const loading = ref(false)
const message = ref('')
const assignments = ref([])
const selected = ref(null)
const conclusion = ref('')
const reason = ref('')
const submitting = ref(false)
const fileUrls = ref({})
const loggedIn = computed(() => Boolean(token.value && user.value?.role === 'REVIEWER'))
const pendingCount = computed(() => assignments.value.filter(item => ['PENDING', 'IN_PROGRESS'].includes(item.status)).length)
const reviewQuestions = computed(() => {
  if (!selected.value?.schemaJson) return []
  try {
    const schema = typeof selected.value.schemaJson === 'string' ? JSON.parse(selected.value.schemaJson) : selected.value.schemaJson
    return Array.isArray(schema.questions) ? schema.questions : []
  } catch { return [] }
})

async function request(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  const publicAuthEndpoint = ['/api/auth/login', '/api/auth/register', '/api/auth/dingtalk-profile'].includes(path)
  if (token.value && !publicAuthEndpoint) headers.Authorization = `Bearer ${token.value}`
  if (options.body) headers['Content-Type'] = 'application/json'
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
    if (result.user.role !== 'REVIEWER') throw new Error('当前账号不是评估人员账号')
    token.value = result.accessToken
    user.value = result.user
    localStorage.setItem('assessment_token', result.accessToken)
    localStorage.setItem('assessment_user', JSON.stringify(result.user))
    await loadAssignments()
  } catch (error) { loginError.value = error.message } finally { loading.value = false }
}

async function register() {
  registerError.value = ''
  registerLoading.value = true
  try {
    await request('/api/auth/register', { method: 'POST', body: JSON.stringify(registerForm.value) })
    registerForm.value = { username: '', password: '', realName: '', phone: '', requestedRole: 'REVIEWER', departmentName: '', positionName: '', dingtalkDepartmentId: null }
    dingtalkProfileMessage.value = ''
    lastDingtalkLookupPhone.value = ''
    showRegister.value = false
    loginMessage.value = '评估人员账号申请已提交，请等待管理员审核。'
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

function logout() {
  clearFileUrls()
  token.value = ''
  user.value = null
  localStorage.removeItem('assessment_token')
  localStorage.removeItem('assessment_user')
  selected.value = null
}

async function restoreSession() {
  if (!token.value) return

  try {
    const currentUser = await request('/api/auth/me')
    if (currentUser.role !== 'REVIEWER') throw new Error('当前账号不是评估人员账号')
    user.value = currentUser
    localStorage.setItem('assessment_user', JSON.stringify(currentUser))
    await loadAssignments()
  } catch (error) {
    logout()
    loginError.value = error.message || '登录状态已失效，请重新登录'
  }
}

async function loadAssignments() {
  loading.value = true
  try { assignments.value = await request('/api/review-assignments') } catch (error) { message.value = error.message } finally { loading.value = false }
}

async function openAssignment(item) {
  try {
    clearFileUrls()
    selected.value = await request(`/api/review-assignments/${item.assignmentId}`)
    conclusion.value = selected.value.review?.conclusion || ''
    reason.value = selected.value.review?.reason || ''
    await loadReviewFiles()
  } catch (error) { message.value = error.message }
}

async function startAssignment() {
  if (!selected.value) return
  if (!window.confirm('开始评估后，HR 将不能再调整评估人员。确定开始吗？')) return
  try {
    selected.value = await request(`/api/review-assignments/${selected.value.assignmentId}/start`, { method: 'POST' })
    await Promise.all([loadAssignments(), loadReviewFiles()])
  } catch (error) { message.value = error.message }
}

function clearFileUrls() {
  Object.values(fileUrls.value).forEach(url => URL.revokeObjectURL(url))
  fileUrls.value = {}
}

async function loadReviewFiles() {
  clearFileUrls()
  if (!selected.value) return
  const entries = await Promise.all(selected.value.files
    .filter(file => file.uploadStatus === 'COMPLETED')
    .map(async file => {
      const response = await fetch(`/api/review-assignments/${selected.value.assignmentId}/files/${file.id}`, {
        headers: { Authorization: `Bearer ${token.value}` }
      })
      if (!response.ok) {
        const body = await response.json().catch(() => ({}))
        throw new Error(body.message || `附件读取失败 (${response.status})`)
      }
      return [file.id, URL.createObjectURL(await response.blob())]
    }))
  fileUrls.value = Object.fromEntries(entries)
}

function isImageFile(file) { return file.contentType?.startsWith('image/') }
function questionFor(questionId) { return reviewQuestions.value.find(question => question.id === questionId) }
function questionTitle(questionId) { return questionFor(questionId)?.title || `题目 ${questionId}` }
function questionType(value) { return { TEXT: '文本题', SINGLE: '单选题', MULTIPLE: '多选题', FILE: '文件上传', PRACTICAL: '综合实践题' }[value] || value }
function filesForQuestion(questionId) { return selected.value?.files?.filter(file => file.questionId === questionId) || [] }
function isImageMaterial(material) {
  if (material.kind === '参考图片' || material.url?.startsWith('data:image/')) return true
  return /\.(jpe?g|png|gif|webp|bmp|tiff?)(?:$|[?#])/i.test(material.name || material.url || '')
}

async function submitReview() {
  if (!selected.value || !conclusion.value) { message.value = '请选择评估结论'; return }
  if (conclusion.value === 'REJECTED' && !reason.value.trim()) { message.value = '不通过时必须填写原因'; return }
  submitting.value = true
  try {
    await request(`/api/review-assignments/${selected.value.assignmentId}/submit`, { method: 'POST', body: JSON.stringify({ conclusion: conclusion.value, reason: reason.value.trim() || null }) })
    message.value = '评估结果提交成功'
    selected.value = await request(`/api/review-assignments/${selected.value.assignmentId}`)
    await loadAssignments()
  } catch (error) { message.value = error.message } finally { submitting.value = false }
}

function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '--' }
function conclusionText(value) { return { PASS: '通过', REJECTED: '不通过', RESERVED: '保留' }[value] || value || '未提交' }
function statusText(value) { return { PENDING: '待评估', IN_PROGRESS: '评估中', COMPLETED: '已完成', WAITING_CANDIDATE: '等待候选人', CANCELLED: '已取消' }[value] || value }
function parseAnswer(value) { try { const parsed = JSON.parse(value); return Array.isArray(parsed) ? parsed.join('、') : String(parsed) } catch { return value } }

onMounted(restoreSession)
</script>

<template>
  <main v-if="!loggedIn" class="login-page"><section class="login-panel"><div class="brand-mark">RA</div><p class="eyebrow">REVIEW OPERATIONS</p><h1>评估人员工作台</h1><p class="muted">查看候选人测评内容并提交评估结论。</p><form @submit.prevent="login" class="login-form"><label>账号<input v-model="loginForm.username" autocomplete="username" /></label><label>密码<input v-model="loginForm.password" type="password" autocomplete="current-password" /></label><p v-if="loginError" class="error-text">{{ loginError }}</p><p v-if="loginMessage" class="success-text">{{ loginMessage }}</p><button class="primary-button" :disabled="loading">{{ loading ? '登录中...' : '登录工作台' }}</button></form><button type="button" class="outline-button login-register-button" @click="showRegister = true">申请评估人员账号</button></section></main>
  <div v-if="showRegister" class="modal-backdrop"><section class="link-modal register-modal"><header><div><p class="eyebrow">REVIEWER ACCOUNT</p><h2>申请评估人员账号</h2></div><button class="close-button" @click="showRegister = false">×</button></header><p class="muted">注册后需要管理员审核。手机号用于读取钉钉中的部门和岗位。</p><form class="login-form" @submit.prevent="register"><label>姓名<input v-model="registerForm.realName" required maxlength="80" /></label><label>账号<input v-model="registerForm.username" required maxlength="80" autocomplete="username" /></label><label>密码<input v-model="registerForm.password" required minlength="8" type="password" autocomplete="new-password" /></label><label>手机号<input v-model="registerForm.phone" required maxlength="30" inputmode="tel" @blur="lookupDingTalkProfile()" /></label><p v-if="dingtalkProfileLoading || dingtalkProfileMessage" class="profile-message">{{ dingtalkProfileLoading ? '正在识别钉钉信息...' : dingtalkProfileMessage }}</p><label>所在部门<input v-model="registerForm.departmentName" required maxlength="100" placeholder="请确认或补充所在部门" @input="changeRequestedDepartment" /></label><label>所在岗位<input v-model="registerForm.positionName" required maxlength="100" placeholder="请确认或补充所在岗位" /></label><label>申请角色<input value="评估人员" disabled /></label><p v-if="registerError" class="error-text">{{ registerError }}</p><button class="primary-button" :disabled="registerLoading">{{ registerLoading ? '提交中...' : '提交注册申请' }}</button></form></section></div>
  <main v-if="loggedIn" class="reviewer-shell"><aside class="sidebar"><div class="brand"><span class="brand-mark small">RA</span><span>评估工作台</span></div><nav><a class="nav-item active">我的待办 <span>{{ pendingCount }}</span></a></nav><div class="sidebar-foot"><span class="avatar">{{ user?.realName?.slice(0, 1) || '评' }}</span><div><strong>{{ user?.realName }}</strong><small>REVIEWER</small></div><button class="icon-button" title="退出登录" @click="logout">↪</button></div></aside><section class="content"><header class="topbar"><div><p class="eyebrow">REVIEW OPERATIONS / INBOX</p><h1>评估待办</h1></div><div class="top-actions"><button class="outline-button" @click="loadAssignments">↻ 刷新数据</button><span class="reviewer-welcome">{{ user?.realName }}</span></div></header><div v-if="message" class="toast" @click="message = ''">{{ message }} <span>×</span></div><section class="review-stat"><article class="stat-card accent"><span>待处理任务</span><strong>{{ pendingCount }}</strong><small>等待你提交结论</small></article><article class="stat-card"><span>全部分配</span><strong>{{ assignments.length }}</strong><small>我的评估任务</small></article></section><section class="review-layout"><div class="table-section review-list"><div class="section-heading"><div><h2>任务列表</h2><span>{{ assignments.length }} 条记录</span></div><span v-if="loading" class="loading">正在加载...</span></div><div class="review-list-items"><button v-for="item in assignments" :key="item.assignmentId" class="review-list-item" :class="{selected: selected?.assignmentId === item.assignmentId}" @click="openAssignment(item)"><div><strong>{{ item.candidateName }}</strong><small>{{ item.taskNo }} · {{ item.positionName }}</small></div><span class="status-pill" :class="item.status === 'COMPLETED' ? 'status-reviewed' : 'status-submitted'">{{ item.conclusion ? conclusionText(item.conclusion) : statusText(item.status) }}</span></button><p v-if="!assignments.length" class="empty">暂无评估任务</p></div></div><section v-if="selected" class="review-detail"><header class="detail-head"><div><p class="eyebrow">ASSIGNMENT DETAIL</p><h2>{{ selected.taskNo }}</h2><p class="muted">{{ selected.candidateName }} · {{ selected.positionName }}</p></div><span class="status-pill" :class="selected.assignmentStatus === 'COMPLETED' ? 'status-reviewed' : 'status-submitted'">{{ selected.assignmentStatus === 'COMPLETED' ? '已完成' : statusText(selected.assignmentStatus) }}</span></header><div class="review-meta"><span>候选人邮箱：{{ selected.candidateEmail || '--' }}</span><span>任务状态：{{ selected.taskStatus }}</span></div><div class="review-section"><h3>测评题目（{{ reviewQuestions.length }}）</h3><article v-for="(question, questionIndex) in reviewQuestions" :key="question.id || questionIndex" class="review-question"><div class="review-question-head"><strong>题目 {{ questionIndex + 1 }}</strong><small>{{ questionType(question.type) }}</small></div><p>{{ question.title }}</p><div v-if="question.materials?.length" class="review-question-resources"><strong>参考资料</strong><div class="review-resource-grid"><div v-for="(material, materialIndex) in question.materials" :key="materialIndex" class="review-resource"><img v-if="isImageMaterial(material)" :src="material.url" :alt="material.name || '参考图片'" loading="lazy" /><a v-else :href="material.url" :download="material.name || '参考文件'" target="_blank" rel="noopener">{{ material.name || '参考文件' }}<span>下载</span></a></div></div></div><div v-if="filesForQuestion(question.id).length" class="review-question-submissions"><strong>候选人附件</strong><div v-for="file in filesForQuestion(question.id)" :key="file.id" class="review-file" :class="{'review-file-image': isImageFile(file)}"><div v-if="isImageFile(file)" class="review-file-preview"><img v-if="fileUrls[file.id]" :src="fileUrls[file.id]" :alt="file.fileName" /><span v-else>图片加载中...</span></div><div class="review-file-info"><div class="review-file-title"><a v-if="fileUrls[file.id]" :href="fileUrls[file.id]" :download="file.fileName" target="_blank" rel="noopener">{{ file.fileName }}</a><strong v-else>{{ file.fileName }}</strong></div><small>{{ file.contentType }} · {{ file.sizeBytes }} bytes · {{ file.uploadStatus }}</small></div></div></div></article><p v-if="!reviewQuestions.length" class="muted">模板题目读取失败</p></div><div class="review-section"><h3>候选人答案（{{ selected.answers.length }}）</h3><article v-for="answer in selected.answers" :key="answer.questionId" class="review-answer"><strong>{{ questionTitle(answer.questionId) }}</strong><small>{{ answer.questionId }}</small><p>{{ parseAnswer(answer.answerJson) }}</p></article><p v-if="!selected.answers.length" class="muted">暂无答案</p></div><div v-if="selected.review" class="result-box"><strong>已提交结论：{{ conclusionText(selected.review.conclusion) }}</strong><p v-if="selected.review.reason">原因：{{ selected.review.reason }}</p></div><div v-else-if="selected.assignmentStatus === 'IN_PROGRESS'" class="review-form"><h3>提交评估结论</h3><div class="conclusion-grid"><label v-for="item in [{value:'PASS',label:'通过'},{value:'REJECTED',label:'不通过'},{value:'RESERVED',label:'保留'}]" :key="item.value" class="conclusion-option" :class="{chosen: conclusion === item.value}"><input v-model="conclusion" type="radio" :value="item.value" />{{ item.label }}</label></div><label v-if="conclusion === 'REJECTED'">不通过原因<textarea v-model="reason" rows="4" placeholder="请填写具体原因"></textarea></label><button class="primary-button" :disabled="submitting" @click="submitReview">{{ submitting ? '提交中...' : '提交评估结论' }}</button></div><button v-else-if="selected.assignmentStatus === 'PENDING'" class="primary-button" @click="startAssignment">开始评估</button></section><section v-else class="review-empty"><div class="brand-mark">RA</div><h2>选择一个评估任务</h2><p>从左侧列表选择任务，查看候选人答案并提交结论。</p></section></section></section></main>
</template>
