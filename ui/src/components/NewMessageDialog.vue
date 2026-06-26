<template>
  <v-dialog :model-value="modelValue" max-width="560" @update:model-value="$emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="d-flex align-center ga-2">
        <v-icon>mdi-email-plus-outline</v-icon>
        <span>{{ t('notification.new') }}</span>
      </v-card-title>
      <v-card-text>
        <v-alert type="info" variant="tonal" density="compact" class="mb-4">
          {{ t('notification.noUpdateDeletion') }}
        </v-alert>

        <v-form ref="formRef" @submit.prevent="submit">
          <!-- Target type. Defaulting to `group` matches the legacy
               popover; it's the most common destination. `item-props`
               adds the type-specific MDI icon to each list item via
               Vuetify's native prepend slot — keeps the picker scannable
               without the custom `#item` template that was rendering
               blank because `v-bind="props"` and an explicit `:title`
               were stepping on each other. The closed-select chip uses
               a `#selection` slot which composes cleanly. -->
          <v-select v-model="form.targetType" :items="TYPE_OPTIONS" item-title="label" item-value="value" :item-props="typeItemProps" :label="t('notification.toType')" :hint="targetHint"
            persistent-hint variant="outlined" density="compact" class="mb-3" @update:model-value="onTypeChange">
            <template #selection="{ item }">
              <span v-if="item" class="d-inline-flex align-center ga-2">
                <v-icon :color="TYPE_COLORS[item.raw?.value]" size="small">{{ TYPE_ICONS[item.raw?.value] }}</v-icon>
                {{ item.title }}
              </span>
            </template>
          </v-select>

          <!-- Target autocomplete. Re-fetches on every keystroke
               (debounced via the @update:search handler). The backend
               already returns only what the caller can see, so the
               dropdown content doubles as the visibility filter. No
               per-item icons here — the type icon next to the field
               above already signals the category, and the entity name
               alone (or display name for users) is enough to pick. -->
          <LigojAutocomplete v-model="form.target" v-model:search="targetQuery" :items="targetItems" :item-title="targetLabel" :item-value="getTargetId" :label="t('notification.toTarget')"
            :loading="loadingTargets" :no-filter="true" :hide-no-data="loadingTargets" :rules="REQUIRED" variant="outlined" density="compact" class="mb-1" @update:search="searchTargetsDebounced"
            @update:model-value="refreshAudience" />

          <!-- Audience preview. Empty until the user picks a target. -->
          <div class="mb-3 ml-1" style="min-height: 1.5rem">
            <v-chip v-if="form.target && audience !== null" size="small" variant="tonal" color="primary" prepend-icon="mdi-account-group">
              {{ t('notification.audience', { count: audience }) }}
            </v-chip>
          </div>

          <v-textarea v-model="form.value" :label="t('notification.message')" :hint="t('notification.messageHint')" :rules="REQUIRED" rows="5" variant="outlined" density="compact" persistent-hint />
        </v-form>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" :disabled="sending" @click="$emit('update:modelValue', false)">{{ t('notification.cancel') }}</v-btn>
        <v-btn color="primary" variant="elevated" prepend-icon="mdi-send" :loading="sending" :disabled="!form.target || !form.value.trim()" @click="submit">
          {{ t('notification.send') }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { LigojAutocomplete, useErrorStore, useI18nStore } from '@ligoj/host'
import service from '../service.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue', 'sent'])

const { t } = useI18nStore()
const errorStore = useErrorStore()

const formRef = ref(null)
const sending = ref(false)
const form = reactive({ targetType: 'group', target: null, value: '' })

const TYPE_OPTIONS = computed(() => [
  { value: 'company', label: t('notification.targetType.company') },
  { value: 'group',   label: t('notification.targetType.group') },
  { value: 'project', label: t('notification.targetType.project') },
  { value: 'node',    label: t('notification.targetType.node') },
  { value: 'user',    label: t('notification.targetType.user') },
])

// Type-keyed icon / colour maps. Kept in lockstep with the legacy
// `targetTypeClass` mapping plus the host's sidebar-nav iconography
// for consistency (e.g. `mdi-domain` is the same icon the sidebar uses
// for the "Companies" nav row, so users build a one-to-one mental map).
const TYPE_ICONS = {
  company: 'mdi-domain',
  group:   'mdi-account-group-outline',
  project: 'mdi-folder-outline',
  node:    'mdi-hammer-wrench',
  user:    'mdi-account',
}
const TYPE_COLORS = {
  company: 'red-darken-1',
  group:   'blue-darken-1',
  project: 'green-darken-1',
  node:    'cyan-darken-1',
  user:    'orange-darken-1',
}

/**
 * Per-item v-select props. Vuetify's `item-props` callback returns
 * extra attrs that get spread on the rendered v-list-item — using it
 * for the prepend icon avoids the custom `#item` template (which
 * conflicted with `v-bind="props"` and rendered blank rows).
 */
function typeItemProps(item) {
  return {
    prependIcon: TYPE_ICONS[item.value],
  }
}

const targetHint = computed(() => t(`notification.targetHint.${form.targetType}`))

// Hoisted to avoid the Vuetify 4 "Maximum recursive updates" trap that
// fires when a v-form re-validates against a fresh `[rule]` literal
// every render (host's REWRITE_VUEJS.md notes this on every form).
const REQUIRED = [(v) => (v !== null && v !== undefined && v !== '') || t('common.required')]

const targetItems = ref([])
const targetQuery = ref('')
const targetIdField = ref('id')
const loadingTargets = ref(false)
const audience = ref(null)

let searchTimer = null

function searchTargetsDebounced(query) {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => searchTargets(query), 200)
}

async function searchTargets(query) {
  loadingTargets.value = true
  try {
    const { data, idField } = await service.searchTargets(form.targetType, query)
    targetItems.value = data
    targetIdField.value = idField
  } finally {
    loadingTargets.value = false
  }
}

/**
 * Item-title formatter. For `user` targets, build the full display
 * name (`firstName lastName`) so the dropdown is human-pickable —
 * picking by raw uid is unfriendly. Other entity types fall back to
 * `name → label → pkey → id`. The wire value (sent to the backend
 * via `getTargetId`) is unaffected — that still goes through
 * `targetIdField.value` so projects keep using `pkey`, users keep
 * using `id`, etc.
 */
function targetLabel(item) {
  if (!item || typeof item !== 'object') return String(item || '')
  if (form.targetType === 'user') {
    if (item.firstName && item.lastName) return `${item.firstName} ${item.lastName}`
    if (item.firstName) return item.firstName
    if (item.lastName) return item.lastName
    return item.id || ''
  }
  return item.name || item.label || item.pkey || item.id || ''
}
function getTargetId(item) {
  if (!item || typeof item !== 'object') return item
  return item[targetIdField.value] || item.id || item.name || ''
}

async function refreshAudience() {
  if (!form.target) {
    audience.value = null
    return
  }
  // `user` target type has no meaningful audience preview (always 1) —
  // skip the round-trip to keep the network panel quiet.
  if (form.targetType === 'user') {
    audience.value = 1
    return
  }
  audience.value = await service.audience(form.targetType, form.target)
}

function onTypeChange() {
  // Reset target + audience when type changes; the previous selection
  // is meaningless under the new endpoint and would 404 on submit.
  form.target = null
  audience.value = null
  targetItems.value = []
  targetQuery.value = ''
  // Pre-populate with an empty-query fetch so the dropdown isn't empty
  // when the user opens it.
  searchTargets('')
}

async function submit() {
  const { valid } = await formRef.value.validate()
  if (!valid) return
  sending.value = true
  try {
    const result = await service.create({
      value: form.value,
      targetType: form.targetType,
      target: form.target,
    })
    // `api.post` returns `false` on backend validation rejection (toast
    // already raised by the host's error layer).
    if (result === false) {
      errorStore.push?.('error', t('notification.sendError'))
      return
    }
    errorStore.success(t('notification.sent', { target: form.target }))
    emit('sent', { id: result, ...form })
    emit('update:modelValue', false)
    // Reset for the next compose.
    form.value = ''
    form.target = null
    audience.value = null
  } finally {
    sending.value = false
  }
}

// Pre-load the initial target list once the dialog opens, mirroring
// the legacy popover's "show then load" sequencing — a closed dialog
// doesn't pay the round-trip.
watch(() => props.modelValue, (open) => {
  if (open && targetItems.value.length === 0) searchTargets('')
})
</script>
