<template>
  <v-menu v-model="open" :close-on-content-click="false" max-width="400" offset="4">
    <template #activator="{ props }">
      <v-btn v-bind="props" icon size="small">
        <v-badge :content="unreadCount" :model-value="unreadCount > 0" color="error" floating>
          <v-icon>mdi-bell-outline</v-icon>
        </v-badge>
      </v-btn>
    </template>

    <v-card min-width="380">
      <v-card-title class="d-flex align-center py-2 ga-1">
        <span class="text-subtitle-1 font-weight-medium">{{ t('notification.title') }}</span>
        <v-spacer />
        <!-- Icon-only secondary action. The Vuetify <v-tooltip> child
             pattern (`activator="parent"`) gives the proper themed
             tooltip — the browser's native `title=` attribute looked
             out of place against the rest of the popover chrome. -->
        <v-btn v-if="notifications.length" icon size="small" variant="text" @click="markAllRead">
          <v-icon size="x-small">mdi-check-all</v-icon>
          <v-tooltip activator="parent" location="bottom">{{ t('notification.markAllRead') }}</v-tooltip>
        </v-btn>
        <!-- Primary action sits on the far right of the popover header. -->
        <v-btn icon size="small" variant="text" @click="openCompose">
          <v-icon size="x-small">mdi-email-plus-outline</v-icon>
          <v-tooltip activator="parent" location="bottom">{{ t('notification.new') }}</v-tooltip>
        </v-btn>
      </v-card-title>
      <v-divider />
      <v-list v-if="notifications.length" density="compact" class="pa-0" max-height="360" style="overflow-y: auto">
        <v-list-item v-for="n in notifications" :key="n.id" :class="{ 'bg-blue-lighten-5': !n.read }" @click="markRead(n)">
          <template #prepend>
            <v-icon :color="n.iconColor || 'grey'" size="small" class="mr-3">{{ n.icon }}</v-icon>
          </template>
          <v-list-item-title class="text-body-2" :class="{ 'font-weight-medium': !n.read }">
            {{ n.message }}
          </v-list-item-title>
          <v-list-item-subtitle class="text-caption">{{ formatTime(n.timestamp) }}</v-list-item-subtitle>
        </v-list-item>
      </v-list>
      <v-card-text v-else class="text-center text-medium-emphasis py-6">
        {{ t('notification.empty') }}
      </v-card-text>
    </v-card>
  </v-menu>

  <!-- Compose dialog. Mounted at the bell's root so its overlay sits
       above the popover; opening it closes the popover via the click
       handler below before the dialog activates. -->
  <NewMessageDialog v-model="composeOpen" @sent="onSent" />
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18nStore } from '@ligoj/host'
import service from '../service.js'
import NewMessageDialog from './NewMessageDialog.vue'

const { t } = useI18nStore()

const open = ref(false)
const composeOpen = ref(false)
const notifications = ref([])
let pollTimer = null

const unreadCount = computed(() => notifications.value.filter(n => !n.read).length)

function formatTime(ts) {
  if (!ts) return ''
  const diff = Date.now() - ts
  if (diff < 60000) return t('notification.justNow')
  if (diff < 3600000) return t('notification.minutesAgo', { n: Math.floor(diff / 60000) })
  if (diff < 86400000) return t('notification.hoursAgo', { n: Math.floor(diff / 3600000) })
  return t('notification.daysAgo', { n: Math.floor(diff / 86400000) })
}

/**
 * Click-to-mark-read is a UI-only optimistic flip. The backend marks
 * messages as read implicitly when `/message/my` is fetched (the
 * "read cursor" in MessageReadRepository advances to max-seen id), so
 * the next poll already reflects the new state. No round-trip needed.
 */
function markRead(n) {
  n.read = true
}

function markAllRead() {
  notifications.value.forEach(n => { n.read = true })
}

function openCompose() {
  // Close the popover before opening the dialog so the two overlays
  // don't stack — Vuetify can leave the popover focused-trap active
  // when both are open at once, swallowing the dialog's keyboard
  // navigation (ESC, Tab).
  open.value = false
  composeOpen.value = true
}

async function loadNotifications() {
  const data = await service.findMy()
  if (!data || data.code) return
  notifications.value = (data.data || []).map(m => ({
    id: m.id,
    // `MessageVo.value` carries the text; older payloads used `message`.
    message: m.value || m.message || '',
    icon: 'mdi-bell',
    iconColor: 'primary',
    // `MessageResource.findAllProvider` flips `unread=true` for any
    // id past the saved cursor — invert to drive the styling here.
    read: !m.unread,
    timestamp: m.createdDate || m.created || Date.now(),
  }))
}

function onSent() {
  // Refresh immediately so the sender sees their own message land in
  // the bell (also implicitly advances the read cursor server-side).
  loadNotifications()
}

onMounted(() => {
  loadNotifications()
  // Poll every 60s. The interval is intentionally long — the backend
  // pushes nothing, and this is a low-priority surface (an unread
  // badge), so anything tighter is wasted XHR. A future WebSocket /
  // SSE upgrade can replace this without touching the UI shape.
  pollTimer = setInterval(loadNotifications, 60000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>
