import { createApp } from 'vue'
import App from './App.vue'
import CandidateApp from './CandidateApp.vue'
import ReviewerApp from './ReviewerApp.vue'
import './style.css'

const isCandidatePath = /^\/assessment\/[^/]+\/?$/.test(window.location.pathname)
const isReviewerPath = window.location.pathname === '/review' || window.location.pathname.startsWith('/review/')
createApp(isCandidatePath ? CandidateApp : isReviewerPath ? ReviewerApp : App).mount('#app')
