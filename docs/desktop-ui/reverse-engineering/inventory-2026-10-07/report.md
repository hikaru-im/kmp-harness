# Codex webview behavior inventory

Static first pass over selected JavaScript and CSS bundles from the local app.asar.
Action snippets are evidence locations; they are not a complete control-flow reconstruction.

## Archive

- Archive: /Applications/ChatGPT.app/Contents/Resources/app.asar
- Bytes: 546868335
- SHA-256: 22252f9b76d73a04d9e23710e358201cc4c29b6f003cfbbe109eeb0784dcf848
- Selected bundles: 81

## Routes

### app-shared-9d148924be0b.js

- /.
- /..
- /accounts/verified_access
- /api/timedtext
- /automations
- /automations/remove
- /automations/save
- /automations/set_status
- /c/:conversationId
- /ca/:conversationId
- /celsius/ws/user
- /dev/null
- /extension/panel/new
- /flora/browser/form-fill
- /g/:gizmoId/c/:conversationId
- /images/ecosystem/apps/appgarden/icon.png
- /index.html
- /legalapi/enabled
- /onboarding/aeon-initialization
- /pages/creation-capabilities
- /ps/mcp
- /settings/account_user_setting
- /settings/announcement_viewed
- /settings/user
- /space
- /space/d/external-gdrive%3Aroot?tab=recent
- /space/files?tab=all
- /space/files?tab=all&category=documents
- /space/files?tab=all&category=presentations
- /space/files?tab=all&category=spreadsheets
- /space/files?tab=favorites
- /space/files?tab=images
- /space/sites
- /voice
- /watch
- /websites/access
- /wham
- /wham/accounts/check
- /wham/tasks/list
- /wham/usage
- /wham/usage/stream

### general-settings-4f1402fc1fbd.js

- /settings/connections/wsl
- /settings/general-settings
- /settings/open-source-licenses

### local-conversation-page-71ad501896fc.js

- /settings/usage

### local-conversation-thread-604c26084272.js

- /new
- /skills

### settings-236615fa5105.js

- /saved-credentials

### settings-34669d7ec95d.js

- /agent/browser_context
- /flora/browser/config
- /flora/browser/cookies
- /flora/browser/passkeys
- /oauth/authorize
- /password-managers/1password
- /password-managers/1password/enrollment

### settings-6f23a1dce02a.js

- /amphora/u18_graduation_unlink_setting_notices
- /amphora/u18_graduation_unlink_setting_notices/dismiss
- /settings/notifications

### settings-c4ac8cca94df.js

- /settings/code-review
- /settings/usage
- /wham/settings/code_review
- /wham/settings/code_review_search

### settings-page-b07b6e77dc14.js

- /settings/cloud-environments?tab=preferences
- /settings/teams
- /settings/teams/:teamId

### settings-tabs-9b030b43e026.js

- /settings/worktrees
- /settings/worktrees?tab=environments

### sidebar-actions-da3a2a85b270.js

- /library

### sidebar-debb6bb9726d.js

- /scheduled
- /settings/teams

## Action evidence

| Kind | Count |
| --- | ---: |
| onClick | 151 |
| onOpen | 95 |
| onSelect | 81 |
| fetch | 72 |
| onChange | 43 |
| onClose | 28 |
| onKeyDown | 14 |
| navigate | 7 |
| onSubmit | 7 |
| window.electron | 4 |
| toggleAria | 1 |
| toggleAriaLabel | 1 |
| toggleBottomPanel | 1 |
| toggleHosts | 1 |
| togglePinned | 1 |
| togglePinnedSummary | 1 |
| toggleProject | 1 |
| toggleThreadSidePanel | 1 |
| toggleTooltip | 1 |

Full snippets are in actions.json; each record includes the bundle and byte offset.

## Data source evidence

| Endpoint or channel | Bundles |
| --- | --- |
| /api/timedtext | app-shared-9d148924be0b.js |
| http://[${e}] | app-shared-9d148924be0b.js |
| http://[${e}]/ | app-shared-9d148924be0b.js |
| http://[${n.value}] | app-shared-9d148924be0b.js |
| http://json-schema.org/draft-04/schema# | app-shared-9d148924be0b.js |
| http://json-schema.org/draft-07/schema# | app-shared-9d148924be0b.js |
| http://www.w3.org/2000/svg | app-shared-9d148924be0b.js, general-settings-4f1402fc1fbd.js, local-conversation-thread-604c26084272.js, projects-index-page-3d127290b4aa.js, settings-page-b07b6e77dc14.js |
| https://${a} | local-conversation-thread-604c26084272.js |
| https://${c.sourceHostname} | local-conversation-thread-604c26084272.js |
| https://${e.slice(8)} | app-shared-9d148924be0b.js |
| https://${e} | settings-34669d7ec95d.js |
| https://${e}${a} | app-shared-9d148924be0b.js |
| https://${i} | settings-c4ac8cca94df.js |
| https://${t} | local-conversation-sources-side-panel-tab-2c3970c24a82.js, settings-34669d7ec95d.js |
| https://ab.chatgpt.com/v1 | app-shared-9d148924be0b.js |
| https://api.oaistatsig.com/v1 | app-shared-9d148924be0b.js |
| https://chatgpt.com/backend-api | app-shared-9d148924be0b.js |
| https://chatgpt.com/codex | app-shared-9d148924be0b.js |
| https://chatgpt.com/space/${t.id}${t.blockId==null? | app-shared-9d148924be0b.js |
| https://cloudflare-dns.com/dns-query | app-shared-9d148924be0b.js |
| https://developers.openai.com/codex/app/worktrees#option-1-working-on-the-worktree | local-conversation-git-actions-290387202be8.js |
| https://developers.openai.com/codex/pricing | settings-c4ac8cca94df.js |
| https://docs.google.com/${e.docType}/d/${e.docId}/export | app-shared-9d148924be0b.js |
| https://docs.google.com/document | app-shared-9d148924be0b.js |
| https://docs.google.com/presentation | app-shared-9d148924be0b.js |
| https://docs.google.com/spreadsheets | app-shared-9d148924be0b.js |
| https://example.com | settings-34669d7ec95d.js |
| https://help.openai.com/articles/12315553-parental-controls-on-chatgpt-faq | settings-6f23a1dce02a.js |
| https://help.openai.com/articles/20001509 | composer-controls-9cde96680264.js |
| https://help.openai.com/en/articles/20001529-dots-privacy-security-and-safety-faqs | composer-controls-9cde96680264.js |
| https://json-schema.org/draft/2020-12/schema | app-shared-9d148924be0b.js |
| https://my.1password.com | settings-34669d7ec95d.js |
| https://my.b5dev.eu | settings-34669d7ec95d.js |
| https://statsigapi.net/v1/sdk_exception | app-shared-9d148924be0b.js |
| https://statsigcdn.openai.com/v1 | app-shared-9d148924be0b.js |

## State vocabulary

| State | Bundles |
| --- | --- |
| collapsed | app-shared-9d148924be0b.js, local-conversation-thread-604c26084272.js, thread-app-shell-chrome-6e13d1129e78.js |
| disconnected | app-shared-9d148924be0b.js, settings-34669d7ec95d.js |
| done | app-shared-9d148924be0b.js, local-conversation-subagents-panel-tab-797735e5898b.js, local-conversation-thread-604c26084272.js, local-conversation-turn-ccc9d0fc20e5.js, settings-6f23a1dce02a.js, thread-app-shell-chrome-6e13d1129e78.js |
| empty | app-shared-9d148924be0b.js, composer-controls-9cde96680264.js, local-conversation-subagents-panel-tab-797735e5898b.js, local-conversation-summary-panel-artifacts-section-27558851a18b.js, local-conversation-thread-604c26084272.js, local-conversation-turn-ccc9d0fc20e5.js, projects-index-page-3d127290b4aa.js, settings-236615fa5105.js, settings-34669d7ec95d.js, settings-6f23a1dce02a.js, settings-analytics-telemetry-c39155a87119.js, settings-c33054dabfee.js, settings-c4ac8cca94df.js, settings-e2668ec6b2b6.js, settings-page-b07b6e77dc14.js, sidebar-d2a4148ed3c4.js, sidebar-debb6bb9726d.js, thread-app-shell-chrome-6e13d1129e78.js |
| error | app-shared-9d148924be0b.js, codex-dark-5fb95d1c65ca.js, codex-light-53d4e35e1869.js, composer-controls-9cde96680264.js, general-settings-4f1402fc1fbd.js, local-conversation-git-actions-290387202be8.js, local-conversation-heartbeat-automation-2de63acf992a.js, local-conversation-page-71ad501896fc.js, local-conversation-summary-panel-artifacts-section-27558851a18b.js, local-conversation-thread-604c26084272.js, local-conversation-thread-turn-entries-f8f6141598d6.js, local-conversation-turn-ccc9d0fc20e5.js, projects-index-page-3d127290b4aa.js, settings-236615fa5105.js, settings-34669d7ec95d.js, settings-6f23a1dce02a.js, settings-a7a4bc183f33.js, settings-analytics-telemetry-c39155a87119.js, settings-c33054dabfee.js, settings-c4ac8cca94df.js, settings-e2668ec6b2b6.js, settings-page-b07b6e77dc14.js, settings-plugin-selection-9fe363c179a8.js, sidebar-archive-suggestion-b86c34a51790.js, sidebar-children-84a9b39dda07.js, sidebar-d2a4148ed3c4.js, sidebar-debb6bb9726d.js, sidebar-onboarding-checklist-task-config-216421cced66.js, task-summary-f14205b468c4.js, thread-app-shell-chrome-6e13d1129e78.js |
| expanded | app-shared-9d148924be0b.js, composer-controls-9cde96680264.js, general-settings-4f1402fc1fbd.js, local-conversation-thread-604c26084272.js, local-conversation-turn-ccc9d0fc20e5.js, projects-index-page-3d127290b4aa.js, settings-34669d7ec95d.js, settings-host-dropdown-c5bec47910b3.js, settings-page-b07b6e77dc14.js, settings-plugin-selection-9fe363c179a8.js, sidebar-d2a4148ed3c4.js, sidebar-debb6bb9726d.js, sidebar-onboarding-checklist.electron-b0a62949e4a7.js, thread-scroll-layout-fc843a7736ae.js |
| failed | app-shared-9d148924be0b.js, composer-controls-9cde96680264.js, general-settings-4f1402fc1fbd.js, local-conversation-git-actions-290387202be8.js, local-conversation-summary-panel-artifacts-section-27558851a18b.js, local-conversation-thread-604c26084272.js, local-conversation-thread-turn-entries-f8f6141598d6.js, local-conversation-turn-ccc9d0fc20e5.js, projects-index-page-3d127290b4aa.js, settings-34669d7ec95d.js, settings-6f23a1dce02a.js, settings-a7a4bc183f33.js, settings-c33054dabfee.js, settings-c4ac8cca94df.js, settings-e2668ec6b2b6.js, sidebar-d2a4148ed3c4.js, sidebar-debb6bb9726d.js, sidebar-fact-table-6e19d07213d8.js, sidebar-onboarding-checklist.electron-b0a62949e4a7.js, sidebar-people-also-ask-93cb893fbbb1.js |
| loading | app-shared-9d148924be0b.js, composer-controls-9cde96680264.js, general-settings-4f1402fc1fbd.js, local-conversation-page-71ad501896fc.js, local-conversation-summary-panel-artifacts-section-27558851a18b.js, local-conversation-thread-604c26084272.js, local-conversation-turn-ccc9d0fc20e5.js, projects-index-page-3d127290b4aa.js, settings-236615fa5105.js, settings-34669d7ec95d.js, settings-6f23a1dce02a.js, settings-a7a4bc183f33.js, settings-analytics-telemetry-c39155a87119.js, settings-c33054dabfee.js, settings-c4ac8cca94df.js, settings-e2668ec6b2b6.js, settings-plugin-selection-9fe363c179a8.js, sidebar-archive-suggestion-b86c34a51790.js, sidebar-children-84a9b39dda07.js, sidebar-d2a4148ed3c4.js, sidebar-debb6bb9726d.js, sidebar-fact-table-6e19d07213d8.js, sidebar-people-also-ask-93cb893fbbb1.js, task-summary-f14205b468c4.js, thread-app-shell-chrome-6e13d1129e78.js |
| offline | app-shared-9d148924be0b.js |
| pending | app-shared-9d148924be0b.js, composer-controls-9cde96680264.js, local-conversation-page-71ad501896fc.js, local-conversation-summary-panel-artifacts-section-27558851a18b.js, local-conversation-thread-604c26084272.js, local-conversation-thread-turn-entries-f8f6141598d6.js, projects-index-page-3d127290b4aa.js, settings-34669d7ec95d.js, settings-6f23a1dce02a.js, settings-analytics-telemetry-c39155a87119.js, sidebar-debb6bb9726d.js, sidebar-fact-table-6e19d07213d8.js, sidebar-onboarding-checklist-task-config-216421cced66.js, sidebar-people-also-ask-93cb893fbbb1.js |
| starting | app-shared-9d148924be0b.js, general-settings-4f1402fc1fbd.js, local-conversation-thread-604c26084272.js, local-conversation-turn-ccc9d0fc20e5.js, sidebar-onboarding-checklist-task-config-216421cced66.js |
| streaming | app-shared-9d148924be0b.js, local-conversation-thread-604c26084272.js, local-conversation-turn-ccc9d0fc20e5.js |
| success | app-shared-9d148924be0b.js, composer-controls-9cde96680264.js, general-settings-4f1402fc1fbd.js, local-conversation-git-actions-290387202be8.js, local-conversation-page-71ad501896fc.js, local-conversation-summary-panel-artifacts-section-27558851a18b.js, local-conversation-thread-604c26084272.js, local-conversation-turn-ccc9d0fc20e5.js, settings-c33054dabfee.js, settings-e2668ec6b2b6.js, settings-plugin-selection-9fe363c179a8.js, sidebar-actions-da3a2a85b270.js |
| unavailable | app-shared-9d148924be0b.js, composer-controls-9cde96680264.js, general-settings-4f1402fc1fbd.js, local-conversation-page-71ad501896fc.js, local-conversation-summary-panel-artifacts-section-27558851a18b.js, local-conversation-thread-604c26084272.js, settings-34669d7ec95d.js, settings-6f23a1dce02a.js, settings-analytics-telemetry-c39155a87119.js, settings-c33054dabfee.js, settings-c4ac8cca94df.js, settings-configs-52e368d80ee8.js, settings-e2668ec6b2b6.js, settings-page-b07b6e77dc14.js, settings-plugin-selection-9fe363c179a8.js, sidebar-d2a4148ed3c4.js, sidebar-debb6bb9726d.js, sidebar-fact-table-6e19d07213d8.js, sidebar-onboarding-checklist-task-config-216421cced66.js, sidebar-people-also-ask-93cb893fbbb1.js, task-summary-f14205b468c4.js |
| working | app-shared-9d148924be0b.js, local-conversation-git-actions-290387202be8.js, local-conversation-subagents-panel-tab-797735e5898b.js, local-conversation-thread-604c26084272.js, local-conversation-turn-ccc9d0fc20e5.js |

## Interpretation

- Routes and message IDs are strong evidence of page and state vocabulary.
- Action snippets show where the bundled UI wires user events; minification means imported module names and nearby code must be read together.
- Endpoint strings are incomplete when URLs are assembled dynamically or hidden behind a request helper.
- The next pass should resolve each action to a normalized UiAction and each data source to a repository method.
