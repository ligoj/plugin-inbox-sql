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

  // Compose dialog
  'notification.new': 'New message',
  'notification.from': 'From',
  'notification.to': 'To',
  'notification.send': 'Send',
  'notification.cancel': 'Cancel',
  'notification.message': 'Message',
  'notification.messageHint': 'Plain text — basic XSS protection runs server-side',
  'notification.toType': 'Target type',
  'notification.toTarget': 'Target',
  'notification.targetHint.company': 'The users within the company',
  'notification.targetHint.group': 'The members of the group',
  'notification.targetHint.project': 'The team leader and the members of any group of the project',
  'notification.targetHint.node': 'The members of any group of any project using the tool',
  'notification.targetHint.user': 'Only one user',
  'notification.audience': 'Will be received by {count} user(s)',
  'notification.targetType.company': 'Company',
  'notification.targetType.group': 'Group',
  'notification.targetType.project': 'Project',
  'notification.targetType.node': 'Tool',
  'notification.targetType.user': 'User',
  'notification.sent': 'Message sent to {target}',
  'notification.sendError': 'Could not send the message — check the target and retry',
  'notification.noUpdateDeletion': 'Sent messages stay until the backend supports it and cannot be modified or deleted.',
}
