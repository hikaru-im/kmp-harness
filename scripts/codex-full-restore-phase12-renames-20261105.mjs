#!/usr/bin/env node
/**
 * Phase 12 (remaining settings pages) semantic rename specs.
 *
 * Writes one renames/<slice>.json per bounded slice built by
 * codex-full-restore-phase12-slices-20261105.mjs. Every declAnchor is exact
 * baseline text; the semantic tool renames only the identifier binding inside
 * that anchor and later proves inverse byte equality.
 *
 * Names are derived from the message ids the member actually renders
 * (settings.chatGpt.*, settings.import.*, settings.hooks.*, ...) or from the
 * props/data it reads. Members whose only evidence is a two-character module
 * slot (`var a, o;`) are deliberately not renamed: their binding carries no
 * semantic claim that the message-id evidence could support.
 *
 * Usage:
 *   node scripts/codex-full-restore-phase12-renames-20261105.mjs [--out DIR]
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const phaseDir = path.join(
  root,
  "references/codex-26.1027.61615-24141/full-restore/12-semantic",
);
const outIndex = process.argv.indexOf("--out");
const outDir =
  outIndex >= 0 ? path.resolve(process.argv[outIndex + 1]) : path.join(phaseDir, "renames");

const specs = {
  "notifications-settings": {
    source: "notifications-settings-f978129b87c5.js",
    renames: [
      ["K", "getAgentSettingsMessages", "Builds the agent-settings message map (`settings.agent.permissionsMode.*`, `settings.ide.defaultOpenTarget.label`, `settings.openIn.integratedTerminalShell.label`)."],
      ["ct", "ChatGptNotificationsSection", "Renders the ChatGPT notifications group; opens the web settings with `slug: notifications` under `settings.notifications.chatGptGroupTitle`."],
      ["lt", "DesktopNotificationsSection", "Desktop notification preferences with per-platform errors (`notifications.windowsSettings.openError`, `notifications.systemSettings.openError`, `notifications.preference.saveError`)."],
      ["pt", "getNotificationsMessages", "Notification message map, including `notifications.dot.label` badge copy."],
      ["He", "NotificationSoundSection", "Sound picker listing `notifications.sound.classic`/`basso`/`glass`/`ping`/`pop`/`purr` with a preview toggle."],
    ],
  },
  "import-settings": {
    source: "import-settings-b00895178720.js",
    renames: [
      ["$n", "ImportSettingsPage", "Import page container (`settings.import.subtitle.v2`) with history list, view-more and loading/error/retry states."],
      ["fn", "ImportSettingsSection", "Import run section (`settings.agent.importSettings.sectionTitle.v2`) with detecting/importing/loading labels and `settings.import.error.label`."],
      ["Un", "ImportAutosyncDialog", "Autosync customize dialog (`settings.import.autosync.customizeDescription`) over item-type checkboxes with cancel/save."],
      ["Jn", "ImportAutosyncSection", "Autosync summary row (`settings.import.autosync.allCategoriesSummary`, `noneSelected`, `chatsOnly`, `selectedCount`)."],
      ["Tn", "ImportHistoryRunningRow", "Running-import history row (`settings.import.history.running`, `settings.import.history.runningBadge`)."],
      ["wn", "ImportHistoryEntryRow", "Expandable import-history entry row driven by `entry.kind === \"running\"` and `mcpServerStatuses`."],
    ],
  },
  "security-settings": {
    source: "security-settings-e577a3e884c6.js",
    renames: [
      ["wo", "ChatGptSecuritySettingsPage", "Page gate: renders the empty state when no account id is available, otherwise the account security sections."],
      ["To", "SecuritySessionsSection", "Sessions and login-connection section (`settings.chatGpt.security.sessions`, `activeSessions`, `sessions.description`, `loginConnections`)."],
      ["co", "ActiveSessionsDialog", "Device/session detail dialog (`settings.chatGpt.security.activeSessions`, `sessions.devicesDescription`, `sessions.devicesDescriptionWithSignedOut`)."],
      ["Xr", "getSecurityMessages", "Security message map for lockdown toasts (`settingsModal.lockdownModeUpdateFailed`, `lockdownModeEnabledToast`, `lockdownModeDisabledToast`)."],
    ],
  },
  "age-settings": {
    source: "age-settings-61207ebb5d33.js",
    renames: [
      ["we", "AgeSettingsRoute", "Chooses the verified-age view or the pending-verification view from the account age state."],
      ["Ee", "AgeSettingsPage", "Age settings page (`settings.chatGpt.age.teenRange`, `settings.chatGpt.age.back`)."],
      ["$", "getAgeSettingsMessages", "Age message map (`settings.chatGpt.age.verification`)."],
      ["U", "AgeVerificationSection", "Verification status with refresh and error copy (`settings.chatGpt.age.refresh`, `verificationError`, `refreshError`)."],
      ["Te", "AgeStatusCard", "Renders the adult or teen status (`settings.chatGpt.age.adult`, `settings.chatGpt.age.teen`)."],
      ["q", "AgeExplanationNote", "Explains the teen experience (`settings.chatGpt.age.teenExplanation`, `settings.chatGpt.age.unsureExplanation`)."],
    ],
  },
  "agreements-settings": {
    source: "agreements-settings-a2eb534d7616.js",
    renames: [
      ["H", "AgreementsSettingsPage", "Agreements page container with a loading branch before the agreements list."],
      ["$", "getAgreementsMessages", "Workspace agreements message map (`workspaceAgreements.serviceAgreementTitle`, `baaTitle`, `viewLink`, `agreeButton`)."],
      ["K", "AgreementsLoadError", "Load-error alert with retry (`workspaceAgreements.loadError`, `workspaceAgreements.retry`)."],
      ["U", "AgreementsSignPanel", "Signing panel reading `account-agreements-metadata` and reporting `workspaceAgreements.signError`."],
    ],
  },
  "trusted-contact-settings": {
    source: "trusted-contact-settings-bdcbbb8929a1.js",
    renames: [
      ["nt", "TrustedContactSettingsPage", "Page gate rendering the trusted-contact section for an allowed account."],
      ["rt", "TrustedContactEnrollmentGate", "Enrollment entry point that renders nothing unless the trusted-contact status is `allowed`."],
      ["tt", "getTrustedContactModalMessages", "Trusted-contact info-modal message map (`settingsModal.trustedContacts.infoModal.*`)."],
      ["Ke", "TrustedContactFormDialog", "Enrollment form with `trusted-contact-name`/`-phone`/`-email` fields, validation and consent checkbox."],
      ["gt", "getTrustedContactSectionMessages", "Section message map (`settingsModal.trustedContacts.sectionDescriptionPrimary.v2`, `addTrustedContact`, `pendingStatus`)."],
      ["He", "TrustedContactsInfoDialog", "Trusted-contact info dialog with continue/cancel."],
    ],
  },
  "safety-settings": {
    source: "safety-settings-c8bfa39ed443.js",
    renames: [
      ["Re", "SafetySettingsPage", "Page gate with a denied branch before the time-management section."],
      ["Pe", "QuietHoursEditorDialog", "Quiet-hours/schedule editor (`settings.chatGpt.timeManagement.quietHours.title`, `common.cancel`, `common.save`)."],
      ["ze", "TimeManagementSection", "Time-management section (`settings.chatGpt.timeManagement.quietHours.title`, `managedByParent`, `unsupportedStatus`)."],
      ["Te", "RecurringScheduleEditor", "Recurring schedule blocks (`settings.recurringSchedule.enabled`, `block`, `start`, `end`)."],
      ["Ee", "RecurringScheduleDays", "Day selector (`settings.recurringSchedule.days.everyDay`, `settings.recurringSchedule.days.weekDays`)."],
      ["Be", "RecurringScheduleLoadState", "Schedule loading/error/retry state (`settings.chatGpt.timeManagement.schedule.loadError`, `schedule.loading`, `common.retry`)."],
    ],
  },
  "time-management-settings": {
    source: "time-management-settings-9936de27f105.js",
    renames: [
      ["o", "TimeManagementSettingsPage", "Route page that reads the `tab`/`state` search params and selects the time-management surface."],
    ],
  },
  "keyboard-shortcuts-settings": {
    source: "keyboard-shortcuts-settings-bac62a963482.js",
    renames: [
      ["Wt", "KeyboardShortcutsSettings", "Shortcut editor (`settings.keyboardShortcuts.searchByKeystrokes.tooltip`, `resetAllError`, `restoreDefaults`)."],
      ["sn", "initKeyboardShortcutsModule", "Module initializer wiring the shortcut-editing stores and message bindings."],
      ["Bt", "NumberTargetShortcutRow", "Number-target shortcut row (`settings.keyboardShortcuts.numberTarget.error`, `numberTarget.conflict`)."],
      ["$t", "ResetAllShortcutsDialog", "Reset-all confirmation (`settings.keyboardShortcuts.resetAllConfirm.title`, `.description`, `.cancel`, `.confirm`)."],
      ["kt", "DictationShortcutSection", "Dictation shortcut section (`settings.keyboardShortcuts.dictation.advanced`, `.appHint`, `.hint`)."],
      ["Nt", "ExternalBrowserLinkShortcut", "External-browser-link shortcut row (`settings.keyboardShortcuts.externalBrowserLinkShortcut.*`)."],
    ],
  },
  "codex-micro-settings": {
    source: "codex-micro-settings-1e444c83baa2.js",
    renames: [
      ["ui", "CodexMicroSettingsPage", "Page gate that returns null when `codexMicro` state is absent."],
      ["di", "CodexMicroSettingsSection", "Device section (`settings.codexMicro.connection.remove`, `.battery`, `.inputMonitoring.*`, `.keyboardLayout.reset`)."],
      ["Jr", "CodexMicroCustomAgentDialog", "Custom-agent picker (`settings.codexMicro.customAgent.title`, `.description`, `.search.placeholderByType`, `.search.empty`)."],
      ["Ci", "CodexMicroKeyboardLayoutPreview", "Keyboard-layout knob preview (`settings.codexMicro.keyboardLayout.knobTooltip.*`)."],
    ],
  },
  "appshots-settings": {
    source: "appshots-settings-73a1db0fe376.js",
    renames: [
      ["we", "AppshotsSettingsPage", "Appshots page (`settings.appshots.destination.automatic`, `.lastChat`, `.automatic.description`)."],
      ["_e", "AppshotHotkeySection", "Hotkey section (`settings.appshotHotkey.label`, `.none`) with save mutation."],
      ["ye", "AppshotHotkeyDescription", "Hotkey description variants (`settings.appshotHotkey.description.command`, `.option`, `.windowsShift`)."],
      ["Se", "getAppshotHotkeyMessages", "Hotkey option message map (`settings.appshotHotkey.option.windowsAlt`, `.option.windowsShift`)."],
      ["Ce", "getAppshotsMessages", "Appshots message map (`settings.appshots.hero.title`, `settings.appshots.soundEffect.label`)."],
      ["Te", "AppshotSoundToggle", "Sound-effect toggle (`settings.appshots.soundEffect.ariaLabel`)."],
    ],
  },
  "voice-settings": {
    source: "voice-settings-320cf1212f2c.js",
    renames: [
      ["ur", "VoiceSettingsPage", "Voice settings container (`settings.voice.general`)."],
      ["Tr", "GlobalDictationHotkeySection", "Global dictation hotkey capture (`settings.general.globalDictationToggleHotkey.*`, `settings.general.globalDictationHotkey.*`)."],
      ["dr", "MicrophoneInputSection", "Microphone selection (`settings.general.microphoneInput.systemDefault`, `.selected`, `.unavailable`, `.saveError`)."],
      ["$n", "DictationHistorySection", "Dictation history (`settings.general.globalDictationHistory.copy`, `.retryTranscription`, `.audioOnly`, `.retry`)."],
      ["xn", "RealtimeVoiceSection", "Realtime voice (`settings.general.realtimeVoice.retry`, `.loading`, `.voice.label`)."],
      ["br", "DictationDictionarySection", "Dictation dictionary editor (`settings.general.dictationDictionary.label`, `.addEntry`, `.removeEntry`)."],
    ],
  },
  "device-storage-settings": {
    source: "device-storage-49df203d8c6c.js",
    renames: [
      ["ft", "DeviceStorageSettingsPage", "Page for the host selected in storage settings."],
      ["it", "StorageOverviewSection", "Storage overview (`settings.storage.homeLabel`, `.measuredTotal`, `.total`, `.updating`)."],
      ["Ke", "StorageCleanupDialog", "Cleanup review dialog (`settings.storage.cleanup.dialogTitle`, `.warningWithSize`, `.empty`, `.untitled`)."],
      ["et", "useStorageCleanup", "Cleanup runner with `settings.storage.cleanup.deleting`, `.result`, `.recheckError`."],
      ["Ue", "StorageCleanupAgeControls", "Cleanup age/archived-only controls (`settings.storage.cleanup.age`, `.days`, `.include`)."],
      ["Xe", "StorageCleanupConfirmDialog", "Destructive cleanup confirmation (`settings.storage.cleanup.confirmTitle`, `.confirmBody`, `.cancel`, `.confirmDelete`)."],
    ],
  },
  "pets-settings": {
    source: "pets-settings-route-bfcfc8a51f0e.js",
    renames: [
      ["gr", "PetsSettingsRoute", "Route switch between the mini-pet settings and the pets page."],
      ["kn", "PetsSettingsSection", "Pets section (`settings.pets.custom.create.label`, `.create.title`, `settings.pets.refresh`)."],
      ["ar", "MiniPetsSettings", "Mini-pet controls (`settings.mini.pets.create.accessibleLabel`, `settings.pets.size`, `.size.description`, `.size.reset`)."],
      ["Hn", "CustomPetCreationSection", "Custom-pet creation (`settings.pets.createCustom.error`, `settings.pets.upgradeCustom.error`)."],
      ["dr", "PetVisibilitySettings", "Visibility options (`settings.pets.visibility`, `.description`, `.onDemand`)."],
      ["$n", "PetPreviewCustomizeDialog", "Preview/customize dialog (`settings.pets.preview.customize`, `.customize.title`)."],
    ],
  },
  "configuration-panel": {
    source: "configuration-panel-45ebea890453.js",
    renames: [
      ["ur", "ConfigurationPanelPage", "Configuration panel reading `work.cloud-orchestration.preferences` (`settings.configuration.workDefaults`)."],
      ["Hn", "AgentConfigurationSection", "Agent configuration (`settings.configuration.codexDefaults`, `.admin`, `settings.agent.configuration.scope.managedDescription`)."],
      ["Bn", "AgentDependenciesDiagnostics", "Dependency diagnostics (`settings.agent.dependencies.diagnose.ok`, `.problem`, `.failed`)."],
      ["vn", "PrimaryRuntimeInstallStatus", "Runtime install status text (`localConversation.primaryRuntimeInstallStatus.downloading`, `.extracting`)."],
      ["Xn", "formatConfigScopeMessage", "Formats the config-scope label (`settings.agent.configuration.scope.unavailable`, `.readOnly`)."],
      ["Un", "ConfigurationSelectRow", "Configuration select row with lock reason, error and disabled states."],
    ],
  },
  "work-settings-panel": {
    source: "panel-26b1f785a3f3.js",
    renames: [
      ["R", "WorkSettingsPanel", "Settings panel shell with the `agent` slug title around the work-preferences body."],
      ["z", "WorkSettingsPanelBody", "Loading/error wrapper around the work-preferences form."],
      ["B", "WorkPreferencesForm", "Work preferences form (`workPreferences.model`, `.reasoning`, `.localApprovalPolicy`)."],
      ["W", "getWorkPreferencesMessages", "Work-preferences message map (`workPreferences.localBrowser`, `.off`, `.on`)."],
    ],
  },
  "ads-controls-settings": {
    source: "route-750a592badca.js",
    renames: [
      ["Ge", "AdsControlsSettingsPage", "Ads controls page with an optional back action."],
      ["Ve", "AdsControlsSection", "Ads controls section (`settings.ads.updateError`, `.historyTitle`, `.topicsTitle`, `.settings.title`)."],
      ["Ee", "AdsDeleteDataDialog", "Delete-ads-data dialog (`settings.ads.deleteError`, `.deleteDataTitle`, `.deleteDataDescriptionHistory.confirmation`)."],
      ["De", "AdsChoicesDialog", "Personalization-choices dialog (`settings.ads.choicesTitle`, `.upgradeTitle`, `.choicesDescription`)."],
      ["Fe", "AdsTopicsSection", "Topics list with `settings.ads.topicsEmptyTitle`, `.topicsEmptyDescription`, `.topicsListHeader`."],
      ["je", "AdsHistorySection", "History list (`settings.ads.historyDescription`, `.historyEmpty`, `.historyEmptyDescription`)."],
    ],
  },
  "archived-chats-settings": {
    source: "data-controls-237ffa3a96c3.js",
    renames: [
      ["bn", "ArchivedChatsSettingsPage", "Archived chats page container with back slot and host selection."],
      ["kn", "ArchivedChatsList", "Archived chat list (`settings.dataControls.archivedChats.loading`, `.error`, `.empty`, `.noMatches`, `.deleteAllSuccess`)."],
      ["hn", "ArchivedTaskRow", "Archived task row (`settings.dataControls.archivedChats.dateTime`, `.deleteWithTitle`, `.untitled`)."],
      ["Rn", "ArchivedChatsProjectGroup", "Project group header/actions (`settings.dataControls.archivedChats.noProject`, `.groupCount`, `.projectActions`)."],
      ["Fn", "ArchivedChatsToolbar", "Search and kind/project filters (`archived-tasks-search`)."],
      ["Dn", "paginateArchivedChats", "Async generator paging archived chats with abort support.", "async function* Dn("],
    ],
  },
  "data-controls-settings": {
    source: "data-controls-settings-8581e5145e81.js",
    renames: [
      ["gn", "ChatGptDataControlsSettingsPage", "Data controls page (`settings.chatGpt.dataControls.updateError`, `.training.trustedAccess.description`)."],
      ["Rt", "DataControlsExportDialog", "Export dialog (`settings.chatGpt.dataControls.export.success`, `.export.conflictError`)."],
      ["nn", "DataControlsNetworkAccessSection", "Network access section (`settings.chatGpt.dataControls.networkAccess`, `.networkAccess.ariaLabel`)."],
      ["dn", "DataControlsWorkResetRow", "Work reset row (`chatgpt.settings.data_controls.work_reset.row.label`, `.row.button.label`)."],
      ["en", "getDataControlsMessages", "Data-controls message map and help URL."],
    ],
  },
  "code-review-settings": {
    source: "settings-723cc33ccc8a.js",
    renames: [
      ["Vr", "CodeReviewAppSettingsPage", "Code-review page selecting the surface from `gitHostingProvider`."],
      ["On", "CodeReviewRepositorySection", "Per-repository review settings (`settings.codeReview.repository.securityTitle`, `.securityTrigger`, `.threatSource`)."],
      ["ar", "CodeReviewSettingsSection", "Main section with `settings.codeReview.save.error`, `.error`, `.retry`, `.loading`."],
      ["ln", "CodeReviewRepositoriesSection", "Repository picker (`settings.codeReview.repositories.connections.loading.v2`, `.error.v2`, `.empty.v2`)."],
      ["Yn", "CodeReviewSecuritySection", "Security review preferences (`settings.codeReview.security.title`, `.autoReview.label`, `.trigger.label`)."],
      ["Hr", "CodeReviewAppPreferencesSection", "App preferences (`settings.codeReview.githubLinks.saveError`, `.appPreferences.title`, `.githubLinks.label`)."],
    ],
  },
  "cloud-preferences-settings": {
    source: "cloud-preferences-settings-1ff207242b24.js",
    renames: [
      ["F", "CloudPreferencesPage", "Cloud preferences panel shell with its title."],
      ["I", "CloudPreferencesForm", "Preferences form (`settings.general.cloudPreferences.loading`, `.save.error`, `.diffView.label`)."],
      ["B", "getCloudPreferencesMessages", "Cloud-preferences message map."],
      ["ae", "CloudPreferencesBranchFormatError", "Branch-format validation (`settings.general.cloudPreferences.branchFormat.error.bracketMismatch`, `.error.invalid`)."],
    ],
  },
  "cloud-environments-settings": {
    source: "cloud-environments-settings-page-b4df36cc3786.js",
    renames: [
      ["Wt", "CloudEnvironmentsSettingsPage", "Cloud environments page with create/edit/select callbacks."],
      ["Kt", "CloudEnvironmentDetails", "Environment details (`settings.cloudEnvironments.details.loading`, `.details.error`, `settings.cloudEnvironments.retry`)."],
      ["yt", "CloudEnvironmentSummary", "Configured environment summary (`settings.cloudEnvironments.details.configured`, `.details.repository`, `list.noRepository`)."],
      ["Rt", "CloudEnvironmentsSearchSection", "Search/list section (`cloud-environments-search`, `settings.cloudEnvironments.search.label`)."],
      ["Tt", "CloudEnvironmentDeleteDialog", "Delete confirmation (`settings.cloudEnvironments.delete.title`, `.delete.description`, `dialog.cancel`)."],
      ["Et", "CloudEnvironmentResetCacheDialog", "Reset-cache confirmation (`settings.cloudEnvironments.resetCache.title`, `.resetCache.description`, `dialog.cancel`)."],
    ],
  },
  "personalization-settings": {
    source: "personalization-settings-7ef3fe8695e0.js",
    renames: [
      ["ka", "ChatGptPersonalizationSettingsPage", "Personalization page shell."],
      ["Na", "PersonalizationSettingsSection", "Personalization content (`settings.chatGpt.personalization.instructionsSaved`, `.saved`, `.saveError`)."],
      ["Qr", "PersonalizationMemorySection", "Memory section (`settings.memory.loadError`, `.retry`, `.unavailable`)."],
      ["ui", "getWorkspaceOwnerOnboardingMessages", "Workspace-owner onboarding role map (`workspaceOwnerOnboarding.role.customerSupport`, `.role.itSecurity`)."],
    ],
  },
  "chronicle-settings": {
    source: "chronicle-settings-page-3f9a395ea72a.js",
    renames: [
      ["On", "ComputerHistorySettingsPage", "Computer history page (`settings.chronicle.enableErrorToast`, `.disableErrorToast`, `.permissionsDeniedToast`)."],
      ["Ht", "ChronicleEnableSection", "Enable/off explainer (`settings.chronicle.turnOn`, `.off.example.previous`, `.off.example.next`)."],
      ["un", "ChronicleHistoryEntry", "History entry with reveal/delete (`settings.chronicle.history.revealMemoryFile`, `.revealMemoryFileAriaLabel`)."],
      ["sn", "ChronicleHistorySection", "History list (`settings.chronicle.history.description`, `.descriptionAriaLabel`, `.loading`)."],
      ["mn", "ChronicleClearMenu", "Clear-history menu (`settings.chronicle.history.clearTenMinutes`, `.clearHour`, `.clearDay`, `.clearAll`)."],
    ],
  },
  "usage-settings": {
    source: "page-a10a550636a7.js",
    renames: [
      ["ls", "UsageSettingsPage", "Usage page that maps visible sections and reads the `tab` search param."],
      ["so", "UsageBalanceHistoryChart", "Balance history (`settings.usage.balanceHistory.title`, `.description`, `.window`, `.empty`)."],
      ["Ro", "UsageOverviewSection", "Usage overview (`settings.usage.load.error`, `.load.retry`, `.load.loading`)."],
      ["Zi", "UsageResetsSection", "Rate-limit resets (`settings.usage.resets.success`, `.resets.loading`, `.resets.loadError`, `.resets.retry`)."],
      ["fi", "getUsageMessages", "Usage message map (`settings.usage.subscriptionSharing.apps.limitsUpdated`, `.limitsUpdateError`)."],
    ],
  },
  "debug-settings": {
    source: "debug-settings-ce35e654cb4e.js",
    renames: [
      ["P", "DebugSettingsPage", "Page gate on `allowDebugMenu(\"prod\")`."],
      ["I", "DebugToggleSection", "GPU-tearing and scroll-fade debug toggles (`settings.general.gpuTearingDebug.disableScrollFadeMask.label`)."],
      ["F", "DebugChromePagesSection", "Chrome-pages toggle (`settings.debug.chromePages.title`, `.subtitle`, `.label`, `.toggle`)."],
    ],
  },
  "computer-use-settings": {
    source: "computer-use-settings-981697fabc1b.js",
    renames: [
      ["Vr", "ComputerUseSettingsPage", "Computer-use page (`settings.computerUse.subtitle`) for the selected host."],
      ["Cr", "ComputerUseAccessSection", "Read-access controls (`settings.computerUse.messages.readAccess.saveError`, `.readAccess.removeError`)."],
      ["mr", "getComputerUseMessages", "Computer-use message map (`settings.computerUse.messages.chatSearch.searching`)."],
      ["ti", "ComputerUseBrowserSection", "Browser detection/reinstall (`settings.computerUse.browser.loading`, `.retryDetection`, `.reinstallExtension`)."],
      ["ni", "ChromeInstallStatusBadge", "Chrome installed/not-installed badge (`settings.computerUse.chrome.installed`, `.notInstalled`)."],
    ],
  },
  "browser-use-settings": {
    source: "browser-use-settings-802dca83451c.js",
    renames: [
      ["ua", "BrowserUseSettingsPage", "Browser-use page (`settings.browserUse.policyAuthoring.subtitle`)."],
      ["Ur", "BrowserBrowsingHistorySection", "Browsing history (`settings.browserUse.browsingHistory.search`, `.loading`, `.retry`)."],
      ["oi", "BrowserDownloadHistorySection", "Download history (`settings.browserUse.downloadHistory.search`, `.clearError`)."],
      ["fa", "BrowserDownloadsSection", "Downloads location (`settings.browserUse.downloads.location.changeError`, `.downloads.title`)."],
    ],
  },
  "browser-extension-settings": {
    source: "browser-extension-settings-289e421d50e0.js",
    renames: [
      ["Q", "BrowserExtensionSettingsPage", "Extension page (`settings.chromeExtension.description`, `.descriptionWithBrowser`)."],
      ["Ve", "getBrowserExtensionMessages", "Extension message map (`settings.chromeExtension.neverAllow`, `.retry`)."],
      ["Z", "getBrowserExtensionPermissionMessages", "Permission labels (`settings.chromeExtension.autoApprove`, `.alwaysAsk`)."],
      ["je", "BrowserExtensionPermissionsForm", "Permission form driven by `preferences` and `rules`."],
      ["Pe", "PermissionSelectRow", "Approval select row with value/options/disabled/managed state."],
      ["K", "getApprovalModeMessages", "Approval-mode message map and `always_ask`/`never_ask` value table."],
    ],
  },
  "local-environments-settings": {
    source: "local-environments-settings-page-a6d95bf07320.js",
    renames: [
      ["p", "LocalEnvironmentsSettingsPage", "Local environments page for the selected host and `tab` param."],
    ],
  },
  "external-section-settings": {
    source: "settings-external-section-df17affc8af1.js",
    renames: [
      ["l", "SettingsExternalSectionPage", "External settings section shell (`settings.section.external`)."],
    ],
  },
  "hooks-settings": {
    source: "hooks-settings-dcc6d1fed9c5.js",
    renames: [
      ["qt", "HooksSettingsPage", "Hooks page with refresh (`settings.hooks.refresh.success`) and host selection."],
      ["Dt", "HooksSettingsSection", "Hooks section (`settings.hooks.refresh`, `.source.sharedProjects`, `.loading.label`, `.subtitle`)."],
      ["ht", "HookDetailPanel", "Hook detail panel (`settings.hooks.trustAll`, `.trust.overridden`, `.trust.error`)."],
      ["ft", "formatHookSummary", "Formats issue/review/attention counts (`settings.hooks.summary.issueCounts`, `.reviewCounts`, `.attentionCounts`)."],
      ["At", "HooksEmptyState", "Empty hooks state (`settings.hooks.emptyHooks.label`, `.emptyHooks.description`)."],
      ["vt", "HookEventSection", "Per-event hook list with enable/trust handlers."],
    ],
  },
  "file-type-handlers-settings": {
    source: "file-type-handlers-657061e9bee1.js",
    renames: [
      ["J", "FileTypeHandlersSettingsPage", "File-type handlers page (`settings.fileTypeHandlers.subtitle`, `.empty`)."],
      ["W", "FileTypeHandlersLoadState", "Load failed/loading state (`settings.fileTypeHandlers.loadFailed`, `common.retry`, `.loading`)."],
      ["le", "FileTypeHandlerRow", "Per-extension handler row (`settings.fileTypeHandlers.default`)."],
      ["U", "FileTypeHandlersSaveError", "Save error alert (`settings.fileTypeHandlers.saveFailed`)."],
    ],
  },
  "skills-settings-route": {
    source: "skills-settings-2afc96506181.js",
    renames: [
      ["s", "SkillsSettingsRoute", "Route shell that lazy-mounts the skills settings page."],
    ],
  },
  "cloud-computer-settings": {
    renames: [
      ["jt", "ChatGptCloudBrowserSettingsPage", "Cloud browser settings page selected by the `tab` param."],
      ["Mt", "CloudBrowserSitePermissionsSection", "Site permissions (`settings.chatGpt.cloudBrowser.sitePermissions`, `.addSite`, `cloudComputer.site.remove.label`)."],
      ["ft", "OrbitCookiesSection", "Orbit cookie controls (`settings.cloudComputer.orbitCookies.unavailable`, `.unavailableDescription`, `.refresh`)."],
      ["nt", "CloudBrowserCookiesSection", "Cloud browser cookies (`settings.chatGpt.cloudBrowser.cookies`, `.cookies.empty`, `.cookies.clearDescription`)."],
      ["bt", "CloudBrowserAddSiteDialog", "Add-site dialog (`settings.chatGpt.cloudBrowser.addDialog.title`, `.site`, `.placeholder`, `.invalid`)."],
      ["pt", "getOrbitCookiesConfirmMessage", "Orbit cookie clear/delete confirmation copy (`restricted.cloudComputer.orbitCookies.clearTitle.dot`, `.deleteTitle.dot`)."],
    ],
  },
  "passwords-settings": {
    renames: [
      ["pt", "PasswordsSettingsPage", "Passwords page gate on account id and user id."],
      ["tt", "SavedPasswordsSection", "Credentials section (`settings.chatGpt.cloudBrowser.credentials.error`, `.workspaceMismatch`)."],
      ["mt", "SavedPasswordsList", "Saved-password search/list (`saved-password-search`, `restricted.passwords.count`, `.saved`, `.refresh`)."],
      ["Ze", "PasswordStoreSelector", "Default password store (`settings.passwords.default.openai`, `.default.onepassword`, `.default.saveError`)."],
      ["ot", "PasswordDeleteConfirmDialog", "Delete confirmation (`restricted.passwords.confirmTitle`, `.confirmDescription`)."],
      ["lt", "useSavedCredentialsQuery", "Saved-credentials query against `/saved-credentials` with account-id header."],
    ],
  },
  "team-settings": {
    source: "team-settings-page-35cc479e7ba6.js",
    renames: [
      ["Wr", "TeamSettingsPageRoute", "Team settings route container for one account."],
      ["Kr", "TeamSettingsSection", "Team content (`settings.teams.preview.notMember`, `.preview.accessDescription`, `.members.adminManagedBanner.description`)."],
      ["sr", "getTeamActivityMessages", "Activity message map (`settings.teams.activity.pluginInstalled`, `.pluginUninstalled`, `.teamConnectionAdded`)."],
      ["xr", "TeamInlineFieldEditor", "Inline field editing (`settings.teams.inline.name`, `.description`, `.nameRequired`, `.addDescription`)."],
      ["pr", "TeamSearch", "Team search (`settings.teams.shared.search`, `settings.teams.searchLabel`, `settings.teams.tabs.label`)."],
    ],
  },
  "parental-controls-settings": {
    source: "settings-eb3d9188c741.js",
    renames: [
      ["Fe", "ChatGptParentalControlsSettingsPage", "Parental-controls page shell."],
      ["Ve", "ParentalControlsMemberSection", "Member section (`settings.chatGpt.parentalControls.memberUnavailable`, `settingsModal.pendingAmphoraInvite`)."],
      ["Oe", "FamilyUnlinkNotices", "Family unlink notices (`settings.chatGpt.familyUnlink.notices.pending`, `.loadError`, `.dismissError`)."],
      ["ke", "U18GraduationNotice", "Under-18 graduation notices (`amphora.settings.u18GraduationUnlinkNotice.teen.description`)."],
      ["Re", "FamilyInviteList", "Paged family invite list for an account with unavailable family ids."],
      ["ze", "FamilyInviteRow", "Single family invite row with retry and next-page callback."],
    ],
  },
  "rosalind-settings": {
    source: "settings-a4d0fe674176.js",
    renames: [
      ["d", "RosalindSettingsPage", "Rosalind settings page bound to the selected host."],
    ],
  },
  "codex-cloud-settings": {
    source: "settings-c45e6ea36c88.js",
    renames: [
      ["Qn", "CodexCloudSettingsPage", "Cloud settings page that redirects `tab=personal-vault` to the vault panel."],
      ["Ln", "EnvironmentSetupConfiguration", "Environment configuration editor (`restricted.environmentSetup.configuration.name.edit`, `settings.localEnvironments.catalog.*`)."],
      ["er", "LocalEnvironmentsCatalogSection", "Saved-config/catalog section (`settings.localEnvironments.catalog.repository.unknown`, `restricted.cloudV2.savedConfigs.*`)."],
      ["nr", "CloudEnvironmentSharingControls", "Sharing controls (`cloudEnvironments.sharing.private`, `.shared`, `.by`)."],
      ["En", "CodexCloudEnvironmentDeleteDialog", "Delete-environment dialog (`cloudEnvironments.delete.title`, `.description`, `.permissionDenied`)."],
      ["$n", "PersonalVaultPanel", "Personal vault panel (`personalVault.moduleLoadError`, `.loading`)."],
    ],
  },
  "hooks-settings-copy": {
    source: "hooks-settings-copy-ac22c0a8ed0d.js",
    renames: [
      ["r", "formatHookEventLabel", "Maps a hook event name to its label (`settings.hooks.event.preModelRequest`, `.postModelRequest`)."],
      ["i", "formatHookEventDescription", "Maps a hook event name to its description (`settings.hooks.event.interrupt.description`)."],
      ["a", "formatHookEntryTitle", "Numbered hook title using `statusMessage` when present (`numberedHookTitle`/`fallbackHookTitle`)."],
      ["o", "parseHookPluginId", "Extracts the plugin id before `@` from a hook source key."],
      ["c", "getHookEventMessages", "Hook event message map (`settings.hooks.event.preModelRequest`, `.postModelRequest`, `.preToolUse`, `.permissionRequest`)."],
    ],
  },
  "hooks-settings-model": {
    source: "hooks-settings-model-139d6975b199.js",
    renames: [
      ["t", "groupHookEntriesByEvent", "Groups hook entries by event name with active/installed/needsReview counts."],
      ["n", "selectInstalledPluginHooks", "Selects hooks installed by one plugin for a given event key set."],
      ["r", "getHookEntryReviewCandidate", "Returns the hook entry that needs review, or undefined when complete."],
      ["i", "mergeEventNameOptions", "Merges known and discovered event names into a sorted option list."],
      ["a", "summarizeHookEntry", "Issue and review counts (`issueCount`, `needsReview`) for one hook entry."],
      ["o", "buildHookSourceSections", "Builds one section per hook source in `C`."],
      ["s", "resolveHookTargetScope", "Resolves the plugin/project target scope for a hook source."],
      ["c", "findHookSourceEntry", "Finds the source entry (project/plugin) for a hook's source id."],
      ["l", "filterHooksByEvent", "Filters hooks by event name and sorts by `displayOrder`."],
      ["u", "isHookNeedingReview", "True when a hook is `untrusted` or `modified`."],
      ["d", "normalizeHookSource", "Normalizes raw hook source strings (`plugin`, `user`, `system`, `mdm`, ...)."],
      ["f", "getSharedHookSource", "Returns the single shared source id, or null when sources differ."],
      ["p", "buildHookSourceSection", "Builds the project/plugin/unknown section model for one source."],
      ["m", "buildProjectHookEntry", "Builds the project-source hook entry from an entry's project hooks."],
      ["h", "groupPluginHooksByPluginId", "Groups plugin hooks by `pluginId`."],
      ["g", "collectHooksForSource", "Collects hooks belonging to one source, including empty unknown entries."],
      ["_", "createHookSection", "Creates a hook section with deduped hooks, warnings and errors."],
      ["v", "dedupeHooksByKey", "Dedupes hooks by `key`."],
      ["y", "dedupeHookMessages", "Dedupes hook messages by `path:message`."],
      ["b", "isHookEmptyWithIssues", "True for an entry with no hooks but warnings or errors."],
      ["x", "isHookActiveAndTrusted", "True when a hook is managed, or enabled and trusted."],
      ["S", "normalizeHookSourceId", "Whitelists known hook source ids."],
      ["T", "initHookSourceConstants", "Initializes the hook source id list and the `__unknown` sentinel."],
    ],
  },
  "hooks-settings-source-label": {
    source: "hooks-settings-source-label-4e76521f983f.js",
    renames: [
      ["z", "HookEventSection", "Event section with changed/untrusted review reasons and trust controls."],
      ["B", "NoopComponent", "Empty placeholder component."],
      ["V", "HookDetailList", "Hook detail list (`settings.hooks.event.source`, `.hookType`, `.handler`, `.command`)."],
      ["H", "HookDetailRow", "Definition-list row pairing a label with its value."],
      ["U", "HookHandlerLabel", "Handler label (`settings.hooks.event.promptHandler`, `.agentHandler`)."],
      ["J", "getHookDetailMessages", "Hook detail message map (`settings.hooks.event.openConfigFile`)."],
      ["Q", "initHookSourceLabelModule", "Module initializer for the hook source-label surface."],
      ["Y", "HookSourceLabel", "Source label (`settings.hooks.source.plugin`, `.userConfig`, `.adminConfig`, `.projectConfig`)."],
    ],
  },
  "hooks-settings-route": {
    source: "hooks-settings-route-91e970e20353.js",
    renames: [
      ["n", "buildHooksSettingsSearch", "Builds the `hostId`/`pluginId`/`projectRoot`/`source` search string."],
      ["r", "buildHooksSettingsPath", "Joins the settings base path with the hooks search string."],
      ["i", "applyHooksSettingsSearch", "Writes the current hook target back onto a URL search params object."],
      ["s", "initHooksSettingsRouteConstants", "Initializes the `hooks-settings` route slug and `/settings` base path."],
    ],
  },
  "hooks-settings-navigation": {
    source: "hooks-settings-navigation-211788f711da.js",
    renames: [
      ["n", "navigateToHooksSettings", "Selects the host and navigates to the hooks settings section."],
      ["r", "initHooksNavigationModule", "Module initializer for the hooks navigation helper."],
    ],
  },
  "import-settings-gate": {
    source: "import-settings-gate-2dda22aea76f.js",
    renames: [
      ["n", "initImportSettingsGate", "Initializes the import-settings gate flag `3278809559`."],
    ],
  },
  "age-settings-state": {
    source: "age-settings-state-b9e98e16375a.js",
    renames: [
      ["u", "initAgeSettingsStore", "Derives the age-group store: non-adult accounts are gated."],
    ],
  },
  "settings-availability": {
    source: "settings-availability-ba7737bda596.js",
    renames: [
      ["m", "initSettingsAvailabilityStore", "Derives per-host settings availability from plugin settings state."],
    ],
  },
  "settings-visible-sections": {
    source: "use-visible-settings-sections-3d650a480b6f.js",
    renames: [
      ["za", "resolveSettingsSectionOverride", "Returns the component/command overrides for `cloud-environments` and `appshots` sections."],
      ["ta", "getSettingsNavMessages", "Settings navigation message map (`settings.nav.chatSettings`, `settings.nav.securitySettings`)."],
      ["Va", "initSettingsSectionsModule", "Module initializer wiring every settings section registration."],
      ["Za", "isSettingsSectionVisible", "Section visibility predicate."],
      ["Qa", "noop", "No-op module initializer."],
      ["no", "useVisibleSettingsSections", "Derives the visible settings sections from account, auth and host state."],
      ["ro", "isAnalyticsSettingsSection", "True for the `analytics` section slug."],
      ["fo", "initVisibleSettingsSectionsModule", "Module initializer for the visible-sections store."],
    ],
  },
};

/**
 * Resolve the baseline chunk a slice was cut from. The slice manifest is the
 * authority: it records the exact source path and hash the members came from,
 * so files whose names are masked in this transcript are never guessed.
 */
function sourceForSlice(slice, declared) {
  const manifestPath = path.join(phaseDir, "reports", `${slice}-slice.json`);
  const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
  const actual = path.basename(manifest.source.path);
  if (declared && declared !== actual) {
    throw new Error(`${slice}: declared source ${declared} != slice manifest ${actual}`);
  }
  return actual;
}

fs.mkdirSync(outDir, { recursive: true });
const summary = [];
for (const [slice, spec] of Object.entries(specs)) {
  const source = sourceForSlice(slice);
  const entries = spec.renames.map(([oldName, newName, evidence, anchor]) => ({
    old: oldName,
    new: newName,
    declAnchor: anchor ?? `function ${oldName}(`,
    originalPath: `webview/assets/${source}`,
    evidence,
    confidence: "high",
  }));
  fs.writeFileSync(
    path.join(outDir, `${slice}.json`),
    `${JSON.stringify(entries, null, 2)}\n`,
  );
  summary.push({ slice, bindings: entries.length });
}

const total = summary.reduce((sum, item) => sum + item.bindings, 0);
console.log(
  `${summary.length} renames files, ${total} bindings, ${Object.keys(specs).length} slices`,
);
for (const item of summary) console.log(`  ${item.slice}: ${item.bindings}`);
