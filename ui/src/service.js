import { useApi } from '@ligoj/host'

/**
 * REST endpoint used to search candidate targets per `targetType`. The
 * backend already filters results by what the calling user can see, so
 * an autocomplete here doesn't need any extra permissions plumbing.
 * Targets that the user can't see don't come back — they can't be picked
 * — which matches the "send to a population depending on visible
 * resources" contract from the plugin's docstring.
 *
 * `idField` is the property to use as the wire-id (sent to
 * `POST /rest/message`). Projects use `pkey`; everything else uses
 * `id` / `name` interchangeably.
 */
const TARGET_ENDPOINTS = {
  group:   { url: 'rest/service/id/group',   idField: 'name' },
  company: { url: 'rest/service/id/company', idField: 'name' },
  user:    { url: 'rest/service/id/user',    idField: 'id' },
  project: { url: 'rest/project',            idField: 'pkey' },
  node:    { url: 'rest/node',               idField: 'id' },
}

/**
 * Thin wrappers around the `/rest/message/*` endpoints exposed by the
 * `feature:inbox:sql` backend. The bell + compose dialog use these
 * directly — keeping them in service.js means the same logic can be
 * reused from a future Inbox full-page view without re-coding fetches.
 */
const service = {
  /**
   * Fetch the current user's messages (paginated). Backend updates the
   * read cursor on every call to `/message/my`, so the next `count`
   * naturally drops for the messages just observed — no explicit
   * "mark as read" round-trip needed.
   *
   * @param {object} options
   * @param {number} [options.rows=20]
   * @param {number} [options.page=1]
   * @param {object} [options.silent] forwarded to `useApi.get` so a 401
   *   (e.g. session lost mid-poll) doesn't fire a toast on every tick.
   */
  async findMy({ rows = 20, page = 1, silent = true } = {}) {
    const api = useApi()
    return api.get(`rest/message/my?rows=${rows}&page=${page}&sidx=id&sord=desc`,
      silent ? { silent: true } : undefined)
  },

  /**
   * Unread-message count for the current user. Cheap enough to poll
   * separately from `findMy` when only the badge needs refreshing.
   */
  async countUnread({ silent = true } = {}) {
    const api = useApi()
    return api.get('rest/message/count', silent ? { silent: true } : undefined)
  },

  /**
   * Persist a new message. `targetType` is one of
   * `company|group|project|node|user`. The backend re-runs the visibility
   * + XSS check and returns the new id (or a `ValidationJsonException`
   * payload on failure that `useApi` surfaces as an error toast).
   */
  async create({ value, targetType, target }) {
    const api = useApi()
    return api.post('rest/message', { value, targetType, target })
  },

  /**
   * Audience preview: how many users will actually receive a message
   * sent to `(targetType, target)`. Quick GET; runs on every change to
   * the target input so the user sees the count update live.
   */
  async audience(targetType, target) {
    if (!target) return 0
    const api = useApi()
    return api.get(
      `rest/message/audience/${encodeURIComponent(targetType)}/${encodeURIComponent(target)}`,
      { silent: true },
    )
  },

  /**
   * Search candidate targets of a given type, used by the compose
   * dialog's autocomplete. Backend already filters by user visibility
   * via the standard `search[value]=...&rows=&page=&sidx=&sord=` shape
   * other host views use (UserEditDialog, GroupMembersView, …).
   * Returns the list under `data` with `id` / `name` / `pkey` depending
   * on the entity — callers pick the matching field via `idField` from
   * `TARGET_ENDPOINTS`.
   */
  async searchTargets(targetType, query = '') {
    const endpoint = TARGET_ENDPOINTS[targetType]
    if (!endpoint) return { data: [], idField: 'id' }
    const api = useApi()
    const qp = `search[value]=${encodeURIComponent(query || '')}&rows=20&page=1&sidx=name&sord=asc`
    const resp = await api.get(`${endpoint.url}?${qp}`, { silent: true })
    return { data: resp?.data || [], idField: endpoint.idField }
  },
}

export default service
export { TARGET_ENDPOINTS }
