<script setup>
import { computed, onMounted, ref } from 'vue'

const token = window.location.pathname.split('/').filter(Boolean)[1] || ''
const loading = ref(true)
const error = ref('')
const assessment = ref(null)
const schema = ref({ questions: [] })
const answers = ref({})
const draftVersion = ref(0)
const saving = ref(false)
const savedAt = ref(null)
const uploading = ref({})
const submitting = ref(false)
const submitted = ref(false)
const submitError = ref('')
let saveTimer

const questions = computed(() => schema.value.questions || [])
const completedCount = computed(() => questions.value.filter(isCompleted).length)
const progress = computed(() => questions.value.length ? Math.round(completedCount.value / questions.value.length * 100) : 0)
const closedStatus = computed(() => ['SUBMITTED', 'REVIEWING', 'REVIEWED'].includes(assessment.value?.status))

async function api(path, options = {}) {
  const response = await fetch(path, { credentials: 'include', ...options, headers: { ...(options.body && !(options.body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}), ...(options.headers || {}) } })
  const body = response.status === 204 ? null : await response.json().catch(() => ({}))
  if (!response.ok) {
    const exception = new Error(body?.message || `请求失败 (${response.status})`)
    exception.code = body?.code
    throw exception
  }
  return body
}

async function loadPublic() {
  loading.value = true
  try {
    const data = await api(`/api/public/assessments/${token}`)
    assessment.value = data
    submitted.value = ['SUBMITTED', 'REVIEWING', 'REVIEWED'].includes(data.status)
    if (!submitted.value) { const draft = await api(`/api/public/assessments/${token}/draft`); setAssessment(draft) }
  } catch (exception) { error.value = exception.message } finally { loading.value = false }
}

function setAssessment(data, preserveLocalAnswers = false) {
  assessment.value = data
  try { schema.value = JSON.parse(data.schemaJson) } catch { schema.value = { questions: [] }; error.value = '测评模板格式错误' }
  const restored = {}
  data.answers?.forEach(item => {
    try { restored[item.questionId] = JSON.parse(item.answerJson) } catch { restored[item.questionId] = item.answerJson }
    draftVersion.value = Math.max(draftVersion.value, item.draftVersion || 0)
  })
  answers.value = preserveLocalAnswers ? { ...restored, ...answers.value } : restored
}

function answerChanged() {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(saveDraft, 900)
}

async function saveDraft() {
  if (closedStatus.value || saving.value) return true
  saving.value = true
  try {
    const payload = questions.value.filter(question => question.type !== 'FILE' && hasAnswer(question.id)).map(question => ({ questionId: question.id, answerJson: JSON.stringify(answers.value[question.id]) }))
    const result = await api(`/api/public/assessments/${token}/draft`, { method: 'PUT', body: JSON.stringify({ draftVersion: draftVersion.value, answers: payload }) })
    draftVersion.value = result.draftVersion
    savedAt.value = result.savedAt
    assessment.value.status = result.status
    return true
  } catch (exception) {
    error.value = exception.message
    return false
  } finally { saving.value = false }
}

async function uploadFile(question, event) {
  const file = event.target.files?.[0]
  if (!file) return
  uploading.value[question.id] = true
  error.value = ''
  try {
    const presign = await api(`/api/public/assessments/${token}/files/presign`, { method: 'POST', body: JSON.stringify({ questionId: question.id, fileName: file.name, contentType: file.type || 'application/octet-stream', sizeBytes: file.size }) })
    const form = new FormData()
    form.append('file', file)
    await api(presign.uploadUrl, { method: 'PUT', body: form })
    const data = await api(`/api/public/assessments/${token}/draft`)
    setAssessment(data)
  } catch (exception) {
    error.value = exception.message
  } finally { uploading.value[question.id] = false; event.target.value = '' }
}

async function deleteFile(fileId) {
  try {
    await api(`/api/public/assessments/${token}/files/${fileId}`, { method: 'DELETE' })
    assessment.value.files = assessment.value.files.filter(item => item.id !== fileId)
  } catch (exception) {
    error.value = exception.message
  }
}

async function submitAssessment() {
  submitError.value = ''
  const missing = questions.value.filter(question => question.required && !isCompleted(question))
  if (missing.length) { submitError.value = `请完成必填题：${missing.map(item => item.title).join('、')}`; return }
  submitting.value = true
  try {
    const saved = await saveDraft()
    if (!saved) {
      submitError.value = error.value || '草稿保存失败，请重试'
      return
    }
    const result = await api(`/api/public/assessments/${token}/submit`, { method: 'POST', body: JSON.stringify({ idempotencyKey: crypto.randomUUID(), confirm: 'SUBMIT' }) })
    assessment.value.status = result.status
    submitted.value = true
  } catch (exception) {
    submitError.value = exception.message
  } finally { submitting.value = false }
}

function filesFor(questionId) { return assessment.value?.files?.filter(item => item.questionId === questionId && item.uploadStatus !== 'DELETED') || [] }
function isImageMaterial(material) {
  if (material.kind === '参考图片' || material.url?.startsWith('data:image/')) return true
  return /\.(jpe?g|png|gif|webp|bmp|tiff?)(?:$|[?#])/i.test(material.name || material.url || '')
}
function hasAnswer(questionId) { const value = answers.value[questionId]; return Array.isArray(value) ? value.length > 0 : value !== undefined && value !== null && String(value).trim() !== '' }
function isCompleted(question) { return question.type === 'FILE' ? filesFor(question.id).some(file => file.uploadStatus === 'COMPLETED') : hasAnswer(question.id) }
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '--' }
function scrollToQuestion(id) { document.getElementById(`question-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }

onMounted(loadPublic)
</script>

<template>
  <main class="candidate-page">
    <div v-if="loading" class="candidate-state"><div class="candidate-logo">RA</div><h1>正在加载测评</h1><p>请稍候...</p></div>
    <div v-else-if="error && !assessment" class="candidate-state"><div class="candidate-logo danger">!</div><h1>无法打开测评</h1><p>{{ error }}</p></div>
    <div v-else-if="submitted || closedStatus" class="candidate-state"><div class="candidate-logo success">✓</div><h1>测评已提交</h1><p>{{ assessment?.candidateName }}，你的答案已成功提交。</p><dl class="result-meta"><dt>任务编号</dt><dd>{{ assessment?.taskNo }}</dd><dt>应聘岗位</dt><dd>{{ assessment?.positionName }}</dd></dl></div>
    <template v-else-if="assessment">
      <header class="candidate-header"><div class="candidate-brand"><span class="candidate-logo compact">RA</span><div><strong>招聘测评</strong><small>{{ assessment.positionName }}</small></div></div><div class="candidate-deadline"><span>截止时间</span><strong>{{ formatDate(assessment.deadline) }}</strong></div></header>
      <div class="assessment-layout"><aside class="question-nav"><p class="eyebrow">ASSESSMENT</p><h2>{{ assessment.candidateName }}</h2><div class="progress-track"><span :style="{width: `${progress}%`}"></span></div><small>已完成 {{ completedCount }} / {{ questions.length }}</small><nav><button v-for="(question, index) in questions" :key="question.id" :class="{done: isCompleted(question)}" @click="scrollToQuestion(question.id)"><span>{{ index + 1 }}</span>{{ question.title }}</button></nav></aside><section class="assessment-main"><div class="assessment-intro"><p class="eyebrow">{{ assessment.taskNo }}</p><h1>{{ assessment.positionName }}测评</h1><p>请按实际情况完成以下内容。作答期间会自动保存草稿。</p></div><div v-if="error" class="candidate-alert" @click="error = ''">{{ error }} <span>×</span></div><article v-for="(question, index) in questions" :id="`question-${question.id}`" :key="question.id" class="candidate-question"><header><span>0{{ index + 1 }}</span><div><h2>{{ question.title }} <em v-if="question.required">必填</em></h2><small>{{ question.type }}</small></div></header><textarea v-if="question.type === 'TEXT'" v-model="answers[question.id]" rows="6" placeholder="请输入你的回答" @input="answerChanged" /><div v-else-if="question.type === 'SINGLE'" class="choice-list"><label v-for="option in question.options" :key="option"><input v-model="answers[question.id]" type="radio" :value="option" @change="answerChanged" /><span>{{ option }}</span></label></div><div v-else-if="question.type === 'MULTIPLE'" class="choice-list"><label v-for="option in question.options" :key="option"><input v-model="answers[question.id]" type="checkbox" :value="option" @change="answerChanged" /><span>{{ option }}</span></label></div><div v-if="question.materials?.length" class="reference-materials"><strong>参考资料</strong><div v-for="(material, materialIndex) in question.materials" :key="materialIndex" class="reference-material"><img v-if="isImageMaterial(material)" :src="material.url" :alt="material.name || '参考图片'" loading="lazy" /><a v-else :href="material.url" :download="material.name || '参考文件'" target="_blank" rel="noopener">{{ material.name || '参考文件' }}<span>下载</span></a></div></div><div v-if="question.type === 'FILE'" class="candidate-upload"><label><input type="file" accept=".jpg,.jpeg,.png,.gif,.pdf,.psd,.blend,.fbx,.obj,.zip,.rar" @change="uploadFile(question, $event)" /><span>{{ uploading[question.id] ? '上传中...' : '选择文件上传' }}</span></label><div v-for="file in filesFor(question.id)" :key="file.id" class="candidate-file"><strong>{{ file.fileName }}</strong><small>{{ Math.ceil(file.sizeBytes / 1024) }} KB · {{ file.uploadStatus }}</small><button title="删除附件" @click="deleteFile(file.id)">×</button></div></div></article><footer class="submit-bar"><div><strong>{{ saving ? '正在保存...' : savedAt ? `已保存 ${formatDate(savedAt)}` : '草稿尚未保存' }}</strong><small>提交后将不能继续修改</small></div><button class="primary-button" :disabled="submitting" @click="submitAssessment">{{ submitting ? '提交中...' : '提交测评' }}</button></footer><p v-if="submitError" class="submit-error">{{ submitError }}</p></section></div>
    </template>
  </main>
</template>
