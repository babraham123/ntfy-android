# Testing

## Manual testing steps

* Upgrade from old version
* Subscribe to topic
  * With instant delivery
  * With other server
  * Deep link subscribe
  * Enrollment link (`ntfy://<host>/<topic>?user=<u>&code=<c>`)
    * No user saved: signs in, user shows "Signed in with access token" in settings, token appears in the account's token list on the server, messages arrive
    * Same link again (code now used, user saved): subscribes only
    * Fresh code for a used one, no user saved: "invalid, already used or expired" error, no subscription
    * Link with `secure=false`: refused, no request sent
    * Different user already saved: refused, saved user unchanged
    * Custom `Authorization` header set for the server: refused
    * Server without enrollment support (404): "server does not support" error
    * Token user in settings: password hidden, cannot be edited, can be deleted
    * Backup and restore keeps the token user working
* Sending messages
  * Tags/emojis
  * Titles
  * Priority, in particular that high/max priority messages vibrate
* Main view
  * Multi-delete 
  * Toggle global mute
* Detail view
  * Toggle per-topic mute
  * Toggle instant delivery
  * Send message while in detail view (should show notification)
  * Check if notifications get canceled when sending message
  * Clear notifications
  * Send test notification
