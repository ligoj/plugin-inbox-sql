// Plugin-local translations merged into the host i18n store at install
// time. The host used to ship these `notification.*` keys directly — they
// moved here when the bell migrated into the plugin so an install without
// inbox-sql doesn't carry strings for a feature it can't show.
export default {
  'notification.title': 'Notifications',
  'notification.empty': 'No notifications',
  'notification.markAllRead': 'Mark all read',
  'notification.justNow': 'Just now',
  'notification.minutesAgo': '{n} min ago',
  'notification.hoursAgo': '{n}h ago',
  'notification.daysAgo': '{n}d ago',
}
