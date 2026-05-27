# plugin-inbox-sql UI

Vue sources for the Ligoj "inbox-sql" feature plugin. Provides the
app-bar notification bell + polling against
`/rest/message/(my|count)` for the logged-in user. When the plugin
isn't installed, the bell doesn't render and the host makes no
periodic `/rest/message` calls.

Built with Vite in library mode; the output bundle lands under the
Java module's webjars classpath so the host serves it at
`/main/inbox-sql/vue/index.js`.

## Layout

```
ui/
├── package.json
├── vite.config.js            # library build → ../src/main/resources/.../webjars/inbox-sql/vue/
├── index.html                # standalone dev entry
└── src/
    ├── index.js              # plugin contract entry (default export)
    ├── service.js            # /rest/message wrappers
    ├── components/
    │   └── NotificationBell.vue
    └── i18n/{en,fr}.js
```

## Integration

`install()` calls `app.registerHeaderItem(NotificationBell)` so the
host's `AppLayout` mounts the bell next to the user menu. No host-side
import of the component — the host only knows the abstract idea of
"header items contributed by plugins."

## Commands

```sh
npm install
npm run dev        # standalone dev server on :5177; proxies REST to :8080
npm run build      # writes ../src/main/resources/META-INF/resources/webjars/inbox-sql/vue/index.js
```
