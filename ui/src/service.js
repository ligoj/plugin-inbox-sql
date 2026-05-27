import { useApi } from '@ligoj/host'

/**
 * Thin wrappers around the `/rest/message/*` endpoints exposed by the
 * `feature:inbox:sql` backend. The bell component uses these directly
 * — keeping them in service.js means the same logic can be reused from
 * a future Inbox view or another plugin without re-coding the fetches.
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
}

export default service
