import { startDesktop } from './desktop'
import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import './styles/theme.css'

void startDesktop(() => createApp(App).use(router).mount('#app'))
