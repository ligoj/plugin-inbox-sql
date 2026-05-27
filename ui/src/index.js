/*
 * Plugin "inbox-sql" — messaging / notifications backed by
 * `/rest/message/*` (feature:inbox:sql in the backend's plugin tree).
 *
 * Feature-level plugin: no subscription model, no tool nodes. The
 * frontend contributes one thing — the app-bar notification bell —
 * via `app.registerHeaderItem(NotificationBell)`. When the plugin
 * isn't installed the bell never renders and the host makes no
 * `/rest/message` polling calls (the previous host-side bell did,
 * causing 401-on-every-poll for installs without inbox-sql).
 *
 * Authored as source — compiled to `/main/inbox-sql/vue/index.js` by
 * Vite. Shared host surface (stores, components) is imported from
 * `@ligoj/host` and kept external at build.
 */
import { useAppStore, useI18nStore } from '@ligoj/host'
import enMessages from './i18n/en.js'
import frMessages from './i18n/fr.js'
import service from './service.js'
import NotificationBell from './components/NotificationBell.vue'

const features = {
  findMy: service.findMy,
  countUnread: service.countUnread,
}

export default {
  id: 'inbox-sql',
  label: 'Inbox',
  // No routes — the bell is the entire UI surface for now. A future
  // full-inbox view (ported from the legacy `inbox.html`) would land
  // here as `/inbox` + a `InboxView.vue`.
  install() {
    const i18n = useI18nStore()
    i18n.merge(enMessages, 'en')
    i18n.merge(frMessages, 'fr')
    // Register the bell as an app-bar item. The host iterates
    // `appStore.headerItems` and mounts each with <component :is>; no
    // host-side `import NotificationBell` needed, so the host stays
    // unaware of this plugin's existence.
    const app = useAppStore()
    app.registerHeaderItem(NotificationBell)
  },
  feature(action, ...args) {
    const fn = features[action]
    if (!fn) throw new Error(`Plugin "inbox-sql" has no feature "${action}"`)
    return fn(...args)
  },
  service,
  meta: { icon: 'mdi-bell-outline', color: 'amber-darken-2' },
}

export { service }
