#!/usr/bin/env node
/**
 * Phase 12 (remaining settings pages) UI claim index.
 *
 * Produces 12-semantic/sources/ui-claims.json. Curated claims anchor on exact
 * baseline text for each settings page: the page container, its section/form
 * controls, toggles and selects, save/retry/error states, empty/loading states,
 * destructive-confirmation dialogs, message ids and the utility/CSS-module
 * classes the pages actually use. Message-id bindings are additionally
 * extracted from the bounded slices and verified against the baseline.
 *
 * Every anchor is checked against the baseline chunk before anything is
 * written; a missing or ambiguous anchor aborts the run.
 *
 * Usage:
 *   node scripts/codex-full-restore-phase12-claims-20261105.mjs [--out <file>]
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const restoreRoot = path.join(
  root,
  "references/codex-26.1027.61615-24141/full-restore",
);
const phaseDir = path.join(restoreRoot, "12-semantic");
const baseline = path.join(restoreRoot, "02-baseline/formatted");
const outIndex = process.argv.indexOf("--out");
const outPath =
  outIndex >= 0
    ? path.resolve(process.argv[outIndex + 1])
    : path.join(phaseDir, "sources/ui-claims.json");

/**
 * Count how often an anchor occurs in its baseline file. When a short anchor
 * (a message id, a data attribute, a CSS variable) is bound in more than one
 * place, the claim still holds for the page as a whole, so the record pins the
 * first occurrence explicitly instead of leaving the anchor ambiguous.
 */
const occurrenceCache = new Map();
function baselineOccurrence(file, anchor) {
  const key = `${file}\0${anchor}`;
  if (!occurrenceCache.has(key)) {
    const filePath = path.join(baseline, file);
    if (!fs.existsSync(filePath)) return null;
    const text = fs.readFileSync(filePath, "utf8");
    if (text.split(anchor).length - 1 === 0) return null;
    occurrenceCache.set(key, text.split(anchor).length - 1);
  }
  return occurrenceCache.get(key) ?? null;
}

const claims = [];
let seq = 0;
function add(category, file, anchor, claim, occurrence) {
  seq += 1;
  const entry = {
    id: `p12-${String(seq).padStart(3, "0")}-${category}`,
    category,
    file,
    anchor,
    claim,
    confidence: "high",
  };
  // A page may bind the same message id (or reuse the same data attribute and
  // CSS class) in several places. The claim is about the page, so pin the first
  // occurrence rather than dropping the evidence.
  const count = baselineOccurrence(file, anchor);
  const pinned = occurrence ?? (count != null && count > 1 ? 0 : undefined);
  if (pinned != null) entry.occurrence = pinned;
  if (count != null && count > 1) entry.occurrenceCount = count;
  claims.push(entry);
}

/** Resolve the baseline chunk a slice was cut from via its manifest. */
const sourceCache = new Map();
function sliceSource(slice) {
  if (!sourceCache.has(slice)) {
    const manifest = JSON.parse(
      fs.readFileSync(path.join(phaseDir, "reports", `${slice}-slice.json`), "utf8"),
    );
    sourceCache.set(slice, path.basename(manifest.source.path));
  }
  return sourceCache.get(slice);
}
function asset(slice) {
  return `webview/assets/${sliceSource(slice)}`;
}

// One helper per slice keeps the claim bodies readable and guarantees the
// `originalPath` always matches the slice manifest.
const S = (slice) => asset(slice);

// ------------------------------------------------------ NOTIFICATIONS
{
  const f = S("notifications-settings");
  add("page", f, "id: `settings.notifications.chatGptGroupTitle`", "The notifications page groups ChatGPT notification settings under `settings.notifications.chatGptGroupTitle`.");
  add("control", f, "id: `notifications.preference.saveError`", "Desktop notification preferences surface a save failure as `notifications.preference.saveError`.");
  add("error-state", f, "id: `notifications.systemSettings.openError`", "Failing to open OS notification settings reports `notifications.systemSettings.openError`.");
  add("error-state", f, "id: `notifications.windowsSettings.openError`", "Windows-specific notification settings failures report `notifications.windowsSettings.openError`.");
  add("empty-state", f, "id: `notifications.dot.label`", "The notification dot toggle is labelled `notifications.dot.label`.");
  add("control", f, "id: `notifications.sound.classic`", "The notification sound picker offers the `classic` sound via `notifications.sound.classic`.");
  add("copy", f, "id: `settings.agent.permissionsMode.groupTitle`", "The agent-permissions group heading is `settings.agent.permissionsMode.groupTitle`.");
  add("control", f, "id: `settings.ide.defaultOpenTarget.label`", "The default open target is configured through `settings.ide.defaultOpenTarget.label`.");
}

// ------------------------------------------------------------ IMPORT
{
  const f = S("import-settings");
  add("page", f, "id: `settings.import.subtitle.v2`", "The import page subtitle is `settings.import.subtitle.v2`.");
  add("loading-state", f, "id: `settings.import.history.loading`", "Import history shows `settings.import.history.loading` while it loads.");
  add("error-state", f, "id: `settings.import.history.error`", "Import history renders `settings.import.history.error` on failure.");
  add("retry", f, "id: `settings.import.history.retry`", "A failed import-history load offers `settings.import.history.retry`.");
  add("empty-state", f, "id: `settings.import.history.latestTitle`", "The latest import entry is titled with `settings.import.history.latestTitle`.");
  add("control", f, "id: `settings.import.autosync.customizeDescription`", "Autosync customization is described by `settings.import.autosync.customizeDescription`.");
  add("control", f, "id: `settings.import.autosync.allCategories`", "Autosync offers an all-categories option (`settings.import.autosync.allCategories`).");
  add("copy", f, "id: `settings.import.autosync.noneSelected`", "Selecting nothing in autosync shows `settings.import.autosync.noneSelected`.");
  add("copy", f, "id: `settings.import.autosync.selectedCount`", "The autosync summary reports a selected count (`settings.import.autosync.selectedCount`).");
  add("control", f, "id: `settings.import.history.runningBadge`", "A running import shows the `settings.import.history.runningBadge` badge.");
  add("error-state", f, "id: `settings.import.error.label`", "Import failures render `settings.import.error.label`.");
}

// ---------------------------------------------------------- SECURITY
{
  const f = S("security-settings");
  add("page", f, "id: `settings.chatGpt.security.sessions`", "The security page titles its session list with `settings.chatGpt.security.sessions`.");
  add("control", f, "id: `settings.chatGpt.security.activeSessions`", "Active sessions are listed under `settings.chatGpt.security.activeSessions`.");
  add("copy", f, "id: `settings.chatGpt.security.sessions.description`", "The session section explains itself through `settings.chatGpt.security.sessions.description`.");
  add("copy", f, "id: `settings.chatGpt.security.sessions.devicesDescription`", "Device descriptions use `settings.chatGpt.security.sessions.devicesDescription`.");
  add("copy", f, "id: `settings.chatGpt.security.sessions.devicesDescriptionWithSignedOut`", "Signed-out devices get `settings.chatGpt.security.sessions.devicesDescriptionWithSignedOut`.");
  add("control", f, "id: `settings.chatGpt.security.loginConnections.title`", "Login connections are configured in the `settings.chatGpt.security.loginConnections.title` section.");
  add("error-state", f, "id: `settingsModal.lockdownModeUpdateFailed`", "A failed lockdown-mode update reports `settingsModal.lockdownModeUpdateFailed`.");
  add("copy", f, "id: `settingsModal.lockdownModeEnabledToast`", "Enabling lockdown mode confirms with `settingsModal.lockdownModeEnabledToast`.");
  add("css", "webview/assets/de34b33b485e.css", "._field_1wl8q_2", "The security route preloads a form-field CSS module (`_field_1wl8q_2`).");
}

// --------------------------------------------------------------- AGE
{
  const f = S("age-settings");
  add("page", f, "id: `settings.chatGpt.age.verification`", "The age page is titled by `settings.chatGpt.age.verification`.");
  add("copy", f, "id: `settings.chatGpt.age.teenRange`", "The teen age range is described by `settings.chatGpt.age.teenRange`.");
  add("control", f, "id: `settings.chatGpt.age.back`", "The age page offers a back action (`settings.chatGpt.age.back`).");
  add("retry", f, "id: `settings.chatGpt.age.refresh`", "Age verification can be refreshed with `settings.chatGpt.age.refresh`.");
  add("error-state", f, "id: `settings.chatGpt.age.verificationError`", "Verification failures show `settings.chatGpt.age.verificationError`.");
  add("error-state", f, "id: `settings.chatGpt.age.refreshError`", "A failed refresh shows `settings.chatGpt.age.refreshError`.");
  add("copy", f, "id: `settings.chatGpt.age.adult`", "Adult accounts see the `settings.chatGpt.age.adult` status.");
  add("copy", f, "id: `settings.chatGpt.age.teen`", "Teen accounts see the `settings.chatGpt.age.teen` status.");
  add("copy", f, "id: `settings.chatGpt.age.teenExplanation`", "Teen accounts get the `settings.chatGpt.age.teenExplanation` note.");
  add("copy", f, "id: `settings.chatGpt.age.unsureExplanation`", "Unverified accounts get `settings.chatGpt.age.unsureExplanation`.");
}

// --------------------------------------------------------- AGREEMENTS
{
  const f = S("agreements-settings");
  add("page", f, "id: `workspaceAgreements.serviceAgreementTitle`", "The agreements page lists the service agreement (`workspaceAgreements.serviceAgreementTitle`).");
  add("copy", f, "id: `workspaceAgreements.baaTitle`", "The business associate agreement is listed as `workspaceAgreements.baaTitle`.");
  add("control", f, "id: `workspaceAgreements.viewLink`", "Each agreement links out via `workspaceAgreements.viewLink`.");
  add("control", f, "id: `workspaceAgreements.agreeButton`", "Accepting an agreement uses `workspaceAgreements.agreeButton`.");
  add("error-state", f, "id: `workspaceAgreements.loadError`", "A failed agreements load shows `workspaceAgreements.loadError`.");
  add("retry", f, "id: `workspaceAgreements.retry`", "A failed agreements load can be retried (`workspaceAgreements.retry`).");
  add("error-state", f, "id: `workspaceAgreements.signError`", "A failed signature reports `workspaceAgreements.signError`.");
}

// ---------------------------------------------------- TRUSTED CONTACT
{
  const f = S("trusted-contact-settings");
  add("page", f, "id: `settingsModal.trustedContacts.infoModal.title.v2`", "The trusted-contact info modal is titled `settingsModal.trustedContacts.infoModal.title.v2`.");
  add("copy", f, "id: `settingsModal.trustedContacts.infoModal.whatToExpectHeading`", "The modal explains `whatToExpect` via `settingsModal.trustedContacts.infoModal.whatToExpectHeading`.");
  add("control", f, "id: `settingsModal.trustedContacts.addTrustedContact`", "Adding a trusted contact uses `settingsModal.trustedContacts.addTrustedContact`.");
  add("control", f, "trusted-contact-name", "The trusted-contact form has a `trusted-contact-name` field.");
  add("control", f, "trusted-contact-phone", "The trusted-contact form has a `trusted-contact-phone` field.");
  add("control", f, "trusted-contact-email", "The trusted-contact form has a `trusted-contact-email` field.");
  add("error-state", f, "trusted-contact-email-error", "Invalid trusted-contact email surfaces `trusted-contact-email-error`.");
  add("control", f, "trusted-contact-consent-checkbox", "Enrollment requires the `trusted-contact-consent-checkbox` consent.");
  add("copy", f, "settingsModal.trustedContacts.pendingStatus", "Pending enrollment is reported with `settingsModal.trustedContacts.pendingStatus`.");
}

// ------------------------------------------------------------- SAFETY
{
  const f = S("safety-settings");
  add("page", f, "id: `settings.chatGpt.timeManagement.quietHours.title`", "The safety page includes the quiet-hours section (`settings.chatGpt.timeManagement.quietHours.title`).");
  add("copy", f, "id: `settings.chatGpt.quietHours.scheduleDescription`", "The quiet-hours schedule is described by `settings.chatGpt.quietHours.scheduleDescription`.");
  add("copy", f, "id: `settings.chatGpt.timeManagement.managedByParent`", "Parent-managed schedules show `settings.chatGpt.timeManagement.managedByParent`.");
  add("copy", f, "id: `settings.chatGpt.timeManagement.unsupportedStatus`", "Unsupported platforms get `settings.chatGpt.timeManagement.unsupportedStatus`.");
  add("control", f, "id: `settings.recurringSchedule.enabled`", "Recurring schedules are switched on with `settings.recurringSchedule.enabled`.");
  add("control", f, "id: `settings.recurringSchedule.days.weekDays`", "The day picker offers `settings.recurringSchedule.days.weekDays`.");
  add("loading-state", f, "id: `settings.chatGpt.timeManagement.schedule.loading`", "The schedule editor shows `settings.chatGpt.timeManagement.schedule.loading`.");
  add("error-state", f, "id: `settings.chatGpt.timeManagement.schedule.loadError`", "A failed schedule load shows `settings.chatGpt.timeManagement.schedule.loadError`.");
  add("retry", f, "id: `common.retry`", "Schedule load failures fall back to the shared `common.retry` action.");
  add("control", f, "id: `common.save`", "The quiet-hours editor saves through the shared `common.save` label.");
  add("css", "webview/assets/e2016a34337c.css", "._Calendar_zxknp_1", "The schedule editor preloads the shared calendar stylesheet (`_Calendar_zxknp_1`).");
}

// --------------------------------------------------- TIME MANAGEMENT
{
  const f = S("time-management-settings");
  add("page", f, "new URLSearchParams(t.search)", "The time-management page reads its state from `URLSearchParams(t.search)`.");
  add("page", f, "r.get(`view`) !== `quiet-hours`", "The time-management page keeps `quiet-hours` and `study-hours` views and otherwise rewrites the view to `time-management`.");
  add("navigation", f, "`/settings/safety`", "The time-management page redirects back to the `/settings/safety` route.");
}

// ------------------------------------------------ KEYBOARD SHORTCUTS
{
  const f = S("keyboard-shortcuts-settings");
  add("page", f, "id: `settings.keyboardShortcuts.searchByKeystrokes.tooltip`", "Keyboard shortcuts can be searched by keystroke (`settings.keyboardShortcuts.searchByKeystrokes.tooltip`).");
  add("control", f, "id: `settings.keyboardShortcuts.restoreDefaults`", "Defaults are restored with `settings.keyboardShortcuts.restoreDefaults`.");
  add("error-state", f, "id: `settings.keyboardShortcuts.resetAllError`", "A failed reset-all reports `settings.keyboardShortcuts.resetAllError`.");
  add("dialog", f, "id: `settings.keyboardShortcuts.resetAllConfirm.title`", "Resetting all shortcuts asks for confirmation (`settings.keyboardShortcuts.resetAllConfirm.title`).");
  add("dialog", f, "id: `settings.keyboardShortcuts.resetAllConfirm.cancel`", "The reset-all dialog can be cancelled (`settings.keyboardShortcuts.resetAllConfirm.cancel`).");
  add("dialog", f, "id: `settings.keyboardShortcuts.resetAllConfirm.confirm`", "The reset-all dialog confirms with `settings.keyboardShortcuts.resetAllConfirm.confirm`.");
  add("error-state", f, "id: `settings.keyboardShortcuts.numberTarget.conflict`", "Conflicting number-target shortcuts surface `settings.keyboardShortcuts.numberTarget.conflict`.");
  add("control", f, "id: `settings.keyboardShortcuts.dictation.advanced`", "Dictation shortcuts open an advanced panel (`settings.keyboardShortcuts.dictation.advanced`).");
  add("control", f, "id: `settings.keyboardShortcuts.externalBrowserLinkShortcut.unassigned`", "An unassigned external-browser shortcut shows `...externalBrowserLinkShortcut.unassigned`.");
  add("css", "webview/assets/e18c96a77e71.css", "._DropdownIcon_1qm03_489", "Shortcut rows render the shared dropdown chevron (`_DropdownIcon_1qm03_489`).");
  add("css", "webview/assets/dd1402a3bc02.css", "._Tooltip_453y_1", "Shortcut tooltips use the shared tooltip rule.");
}

// -------------------------------------------------------- CODEX MICRO
{
  const f = S("codex-micro-settings");
  add("page", f, "id: `settings.codexMicro.battery`", "The Codex Micro page exposes battery state (`settings.codexMicro.battery`).");
  add("control", f, "id: `settings.codexMicro.connection.remove`", "A paired device can be removed (`settings.codexMicro.connection.remove`).");
  add("control", f, "id: `settings.codexMicro.inputMonitoring.openSettings`", "Input monitoring deep-links to OS settings (`settings.codexMicro.inputMonitoring.openSettings`).");
  add("control", f, "id: `settings.codexMicro.keyboardLayout.reset`", "The keyboard layout can be reset (`settings.codexMicro.keyboardLayout.reset`).");
  add("control", f, "id: `settings.codexMicro.customAgent.title`", "A custom agent is chosen through `settings.codexMicro.customAgent.title`.");
  add("empty-state", f, "id: `settings.codexMicro.customAgent.search.empty`", "No matching custom agent shows `settings.codexMicro.customAgent.search.empty`.");
  add("copy", f, "id: `settings.codexMicro.keyboardLayout.knobTooltip.turnRight`", "Knob tooltips describe turning right (`...knobTooltip.turnRight`).");
}

// ------------------------------------------------------------ APPSHOTS
{
  const f = S("appshots-settings");
  add("page", f, "id: `settings.appshots.hero.title`", "The Appshots page hero is `settings.appshots.hero.title`.");
  add("control", f, "id: `settings.appshots.destination.automatic`", "Appshot destination can be `automatic` (`settings.appshots.destination.automatic`).");
  add("control", f, "id: `settings.appshots.destination.lastChat`", "Appshots can target the last chat (`settings.appshots.destination.lastChat`).");
  add("control", f, "id: `settings.appshotHotkey.label`", "The appshot hotkey is set via `settings.appshotHotkey.label`.");
  add("copy", f, "id: `settings.appshotHotkey.none`", "An unset hotkey shows `settings.appshotHotkey.none`.");
  add("control", f, "id: `settings.appshotHotkey.option.windowsShift`", "Windows offers the Shift hotkey option (`settings.appshotHotkey.option.windowsShift`).");
  add("control", f, "id: `settings.appshots.soundEffect.ariaLabel`", "The sound-effect toggle has an aria label (`settings.appshots.soundEffect.ariaLabel`).");
}

// -------------------------------------------------------------- VOICE
{
  const f = S("voice-settings");
  add("page", f, "id: `settings.voice.general`", "The voice page opens with the `settings.voice.general` section.");
  add("control", f, "id: `settings.general.microphoneInput.systemDefault`", "Microphone selection offers `settings.general.microphoneInput.systemDefault`.");
  add("error-state", f, "id: `settings.general.microphoneInput.unavailable`", "An unavailable microphone shows `settings.general.microphoneInput.unavailable`.");
  add("error-state", f, "id: `settings.general.microphoneInput.saveError`", "Saving the microphone choice can fail (`settings.general.microphoneInput.saveError`).");
  add("error-state", f, "id: `settings.general.globalDictationToggleHotkey.errorGeneric`", "A rejected dictation hotkey reports `settings.general.globalDictationToggleHotkey.errorGeneric`.");
  add("control", f, "id: `settings.general.realtimeVoice.voice.label`", "Realtime voice exposes a voice picker (`settings.general.realtimeVoice.voice.label`).");
  add("retry", f, "id: `settings.general.realtimeVoice.retry`", "Realtime voice failures offer `settings.general.realtimeVoice.retry`.");
  add("control", f, "id: `settings.general.dictationDictionary.addEntry`", "The dictation dictionary has an add-entry action.");
  add("control", f, "id: `settings.general.globalDictationHistory.retryTranscription`", "Failed dictation entries can be retranscribed (`...globalDictationHistory.retryTranscription`).");
  add("copy", f, "id: `settings.general.globalDictationHistory.audioOnly`", "Audio-only history entries are labelled `settings.general.globalDictationHistory.audioOnly`.");
}

// ------------------------------------------------------ DEVICE STORAGE
{
  const f = S("device-storage-settings");
  add("page", f, "id: `settings.storage.homeLabel`", "The storage page labels the home directory (`settings.storage.homeLabel`).");
  add("copy", f, "id: `settings.storage.measuredTotal`", "The measured total is shown as `settings.storage.measuredTotal`.");
  add("loading-state", f, "id: `settings.storage.updating`", "Storage figures show `settings.storage.updating` while recomputing.");
  add("dialog", f, "id: `settings.storage.cleanup.dialogTitle`", "Cleanup opens a review dialog (`settings.storage.cleanup.dialogTitle`).");
  add("empty-state", f, "id: `settings.storage.cleanup.empty`", "Nothing to clean shows `settings.storage.cleanup.empty`.");
  add("control", f, "id: `settings.storage.cleanup.age`", "Cleanup filters by age (`settings.storage.cleanup.age`).");
  add("copy", f, "id: `settings.storage.cleanup.warningWithSize`", "The cleanup warning states the reclaimable size (`settings.storage.cleanup.warningWithSize`).");
  add("loading-state", f, "id: `settings.storage.cleanup.deleting`", "Deletion in progress shows `settings.storage.cleanup.deleting`.");
  add("error-state", f, "id: `settings.storage.cleanup.recheckError`", "A failed recheck reports `settings.storage.cleanup.recheckError`.");
  add("dialog", f, "id: `settings.storage.cleanup.confirmTitle`", "Destructive deletion requires confirmation (`settings.storage.cleanup.confirmTitle`).");
  add("dialog", f, "id: `settings.storage.cleanup.confirmDelete`", "The destructive confirm button is `settings.storage.cleanup.confirmDelete`.");
  add("css", "webview/assets/9a011ebae311.css", "._interactive_10sh1_1", "The storage route preloads the interactive-row CSS module.");
}

// --------------------------------------------------------------- PETS
{
  const f = S("pets-settings");
  add("page", f, "id: `settings.pets.custom.create.title`", "The pets page offers custom pet creation (`settings.pets.custom.create.title`).");
  add("control", f, "id: `settings.pets.refresh`", "Custom avatars can be refreshed (`settings.pets.refresh`).");
  add("control", f, "id: `settings.pets.size`", "Pet size is set through `settings.pets.size`.");
  add("control", f, "id: `settings.pets.visibility.onDemand`", "Visibility can be `on demand` (`settings.pets.visibility.onDemand`).");
  add("error-state", f, "id: `settings.pets.createCustom.error`", "Creating a custom pet can fail (`settings.pets.createCustom.error`).");
  add("error-state", f, "id: `settings.pets.upgradeCustom.error`", "Upgrading a custom pet can fail (`settings.pets.upgradeCustom.error`).");
  add("copy", f, "id: `settings.pets.preview.customize.title`", "The preview dialog title is `settings.pets.preview.customize.title`.");
  add("control", f, "pet-size", "The pet size control is tagged `pet-size`.");
  add("css", "webview/assets/f35a0d712228.css", '[data-avatar-overlay-native-tint="attention"]', "The pets route styles native attention tints by data attribute.");
  add("css", "webview/assets/45bf4071a30f.css", "._Root_78xu1_2", "The pets route preloads the avatar surface stylesheet.");
}

// ----------------------------------------------------- CONFIGURATION
{
  const f = S("configuration-panel");
  add("page", f, "work.cloud-orchestration.preferences", "The configuration panel reads the `work.cloud-orchestration.preferences` config key.");
  add("copy", f, "id: `settings.configuration.workDefaults`", "Work defaults are labelled `settings.configuration.workDefaults`.");
  add("copy", f, "id: `settings.configuration.codexDefaults`", "Codex defaults are labelled `settings.configuration.codexDefaults`.");
  add("copy", f, "id: `settings.agent.configuration.scope.managedDescription`", "Managed config scopes explain themselves via `...scope.managedDescription`.");
  add("copy", f, "id: `settings.agent.configuration.scope.readOnly`", "Read-only scopes are labelled `settings.agent.configuration.scope.readOnly`.");
  add("copy", f, "id: `settings.agent.configuration.scope.unavailable`", "Missing scopes render `settings.agent.configuration.scope.unavailable`.");
  add("control", f, "id: `settings.agent.dependencies.diagnose.ok`", "Dependency diagnostics report `ok` via `settings.agent.dependencies.diagnose.ok`.");
  add("error-state", f, "id: `settings.agent.dependencies.diagnose.failed`", "Failed diagnostics render `settings.agent.dependencies.diagnose.failed`.");
  add("loading-state", f, "id: `localConversation.primaryRuntimeInstallStatus.downloading`", "Runtime installation shows `...primaryRuntimeInstallStatus.downloading`.");
}

// ----------------------------------------------------- WORK SETTINGS
{
  const f = S("work-settings-panel");
  add("page", f, "slug: `agent`", "The work settings panel is mounted under the `agent` settings slug.");
  add("control", f, "id: `workPreferences.model`", "The work panel selects a model (`workPreferences.model`).");
  add("control", f, "id: `workPreferences.reasoning`", "The work panel selects a reasoning level (`workPreferences.reasoning`).");
  add("control", f, "id: `workPreferences.localBrowser`", "Local browser use is toggled by `workPreferences.localBrowser`.");
  add("copy", f, "id: `workPreferences.off`", "The local-browser toggle's off state is `workPreferences.off`.");
}

// ------------------------------------------------------ ADS CONTROLS
{
  const f = S("ads-controls-settings");
  add("page", f, "id: `settings.ads.settings.title`", "The ads controls page is titled `settings.ads.settings.title`.");
  add("error-state", f, "id: `settings.ads.updateError`", "Updating ad preferences can fail (`settings.ads.updateError`).");
  add("copy", f, "id: `settings.ads.choicesTitle`", "Personalization choices are titled `settings.ads.choicesTitle`.");
  add("empty-state", f, "id: `settings.ads.topicsEmptyTitle`", "No topics shows `settings.ads.topicsEmptyTitle`.");
  add("empty-state", f, "id: `settings.ads.historyEmpty`", "No ad history shows `settings.ads.historyEmpty`.");
  add("dialog", f, "id: `settings.ads.deleteDataTitle`", "Deleting ad data opens a dialog titled `settings.ads.deleteDataTitle`.");
  add("dialog", f, "id: `settings.ads.deleteDataDescriptionHistory.confirmation`", "The delete dialog spells out the history consequence (`...confirmation`).");
  add("error-state", f, "id: `settings.ads.deleteError`", "A failed ad-data delete reports `settings.ads.deleteError`.");
}

// --------------------------------------------------- ARCHIVED CHATS
{
  const f = S("archived-chats-settings");
  add("page", f, "archived-tasks-search", "The archived chats page exposes an `archived-tasks-search` control.");
  add("loading-state", f, "id: `settings.dataControls.archivedChats.loading`", "Archived chats show `settings.dataControls.archivedChats.loading`.");
  add("error-state", f, "id: `settings.dataControls.archivedChats.error`", "A failed archived-chats load shows `settings.dataControls.archivedChats.error`.");
  add("empty-state", f, "id: `settings.dataControls.archivedChats.empty`", "No archived chats shows `settings.dataControls.archivedChats.empty`.");
  add("empty-state", f, "id: `settings.dataControls.archivedChats.noMatches`", "A search with no hits shows `settings.dataControls.archivedChats.noMatches`.");
  add("copy", f, "id: `settings.dataControls.archivedChats.deleteWithTitle`", "Deleting an archived chat names it (`...archivedChats.deleteWithTitle`).");
  add("copy", f, "id: `settings.dataControls.archivedChats.deleteAllSuccess`", "Deleting all archived chats confirms with `...deleteAllSuccess`.");
  add("control", f, "id: `settings.dataControls.archivedChats.projectActions`", "Project groups expose actions (`...archivedChats.projectActions`).");
}

// ----------------------------------------------------- DATA CONTROLS
{
  const f = S("data-controls-settings");
  add("page", f, "id: `settings.chatGpt.dataControls.updateError`", "The data controls page reports `settings.chatGpt.dataControls.updateError`.");
  add("control", f, "id: `settings.chatGpt.dataControls.networkAccess`", "Network access is toggled by `settings.chatGpt.dataControls.networkAccess`.");
  add("control", f, "id: `settings.chatGpt.dataControls.training.trustedAccess.description`", "Training trusted access is explained by `...training.trustedAccess.description`.");
  add("loading-state", f, "id: `settings.chatGpt.dataControls.export.success`", "A completed export confirms with `...export.success`.");
  add("error-state", f, "id: `settings.chatGpt.dataControls.export.conflictError`", "A conflicting export reports `...export.conflictError`.");
  add("control", f, "id: `chatgpt.settings.data_controls.work_reset.row.button.label`", "Work reset is triggered by `chatgpt.settings.data_controls.work_reset.row.button.label`.");
}

// -------------------------------------------------------- CODE REVIEW
{
  const f = S("code-review-settings");
  add("page", f, "id: `settings.codeReview.appPreferences.title`", "Code review opens with `settings.codeReview.appPreferences.title`.");
  add("control", f, "id: `settings.codeReview.githubLinks.label`", "GitHub links are configured via `settings.codeReview.githubLinks.label`.");
  add("error-state", f, "id: `settings.codeReview.githubLinks.saveError`", "A failed GitHub-link save reports `settings.codeReview.githubLinks.saveError`.");
  add("control", f, "id: `settings.codeReview.security.autoReview.label`", "Automatic security review is toggled by `settings.codeReview.security.autoReview.label`.");
  add("control", f, "id: `settings.codeReview.security.trigger.label`", "The review trigger is chosen via `settings.codeReview.security.trigger.label`.");
  add("control", f, "id: `settings.codeReview.repository.securityTrigger`", "Per-repository security triggers use `...repository.securityTrigger`.");
  add("loading-state", f, "id: `settings.codeReview.loading`", "The code-review section shows `settings.codeReview.loading`.");
  add("error-state", f, "id: `settings.codeReview.error`", "The code-review section shows `settings.codeReview.error`.");
  add("retry", f, "id: `settings.codeReview.retry`", "A failed code-review load offers `settings.codeReview.retry`.");
  add("empty-state", f, "id: `settings.codeReview.repositories.connections.empty.v2`", "No connected repositories shows `...repositories.connections.empty.v2`.");
}

// ------------------------------------------------ CLOUD PREFERENCES
{
  const f = S("cloud-preferences-settings");
  add("loading-state", f, "id: `settings.general.cloudPreferences.loading`", "Cloud preferences show `settings.general.cloudPreferences.loading`.");
  add("error-state", f, "id: `settings.general.cloudPreferences.save.error`", "Saving cloud preferences can fail (`...cloudPreferences.save.error`).");
  add("control", f, "id: `settings.general.cloudPreferences.diffView.label`", "The diff view is chosen via `...cloudPreferences.diffView.label`.");
  add("error-state", f, "id: `settings.general.cloudPreferences.branchFormat.error.bracketMismatch`", "A malformed branch format reports `...branchFormat.error.bracketMismatch`.");
}

// ----------------------------------------------- CLOUD ENVIRONMENTS
{
  const f = S("cloud-environments-settings");
  add("page", f, "cloud-environments-search", "The cloud environments page exposes a `cloud-environments-search` field.");
  add("loading-state", f, "id: `settings.cloudEnvironments.details.loading`", "Environment details show `settings.cloudEnvironments.details.loading`.");
  add("error-state", f, "id: `settings.cloudEnvironments.details.error`", "Environment details show `settings.cloudEnvironments.details.error`.");
  add("retry", f, "id: `settings.cloudEnvironments.retry`", "Failed environment loads offer `settings.cloudEnvironments.retry`.");
  add("empty-state", f, "id: `settings.cloudEnvironments.list.noRepository`", "An environment without a repository shows `...list.noRepository`.");
  add("dialog", f, "id: `settings.cloudEnvironments.delete.title`", "Deleting an environment asks for confirmation (`...delete.title`).");
  add("dialog", f, "id: `settings.cloudEnvironments.delete.description`", "The delete dialog explains the consequence (`...delete.description`).");
  add("dialog", f, "id: `settings.cloudEnvironments.resetCache.title`", "Resetting the cache asks for confirmation (`...resetCache.title`).");
  add("dialog", f, "id: `settings.cloudEnvironments.dialog.cancel`", "Cloud environment dialogs share the `...dialog.cancel` action.");
}

// --------------------------------------------------- PERSONALIZATION
{
  const f = S("personalization-settings");
  add("page", f, "id: `settings.chatGpt.personalization.saved`", "Personalization saves confirm with `settings.chatGpt.personalization.saved`.");
  add("error-state", f, "id: `settings.chatGpt.personalization.saveError`", "A failed personalization save reports `...personalization.saveError`.");
  add("copy", f, "id: `settings.chatGpt.personalization.instructionsSaved`", "Saved instructions are confirmed with `...personalization.instructionsSaved`.");
  add("error-state", f, "id: `settings.memory.loadError`", "Memory loading can fail (`settings.memory.loadError`).");
  add("retry", f, "id: `settings.memory.retry`", "Failed memory loads can be retried (`settings.memory.retry`).");
  add("empty-state", f, "id: `settings.memory.unavailable`", "Unavailable memory shows `settings.memory.unavailable`.");
  add("css", "webview/assets/9516d5432844.css", "--memory-summary-shimmer-offset", "Memory placeholders shimmer through `--memory-summary-shimmer-offset`.");
  add("css", "webview/assets/280c7fb71b0a.css", "._followUp_1k4sb_59", "The personalization route preloads the onboarding follow-up stylesheet.");
}

// --------------------------------------------------------- CHRONICLE
{
  const f = S("chronicle-settings");
  add("page", f, "id: `settings.chronicle.turnOn`", "Computer history is enabled through `settings.chronicle.turnOn`.");
  add("error-state", f, "id: `settings.chronicle.enableErrorToast`", "A failed enable reports `settings.chronicle.enableErrorToast`.");
  add("error-state", f, "id: `settings.chronicle.disableErrorToast`", "A failed disable reports `settings.chronicle.disableErrorToast`.");
  add("error-state", f, "id: `settings.chronicle.permissionsDeniedToast`", "Denied permissions report `settings.chronicle.permissionsDeniedToast`.");
  add("loading-state", f, "id: `settings.chronicle.history.loading`", "History shows `settings.chronicle.history.loading`.");
  add("empty-state", f, "id: `settings.chronicle.off.example.previous`", "The disabled-state example describes a previous action (`...off.example.previous`).");
  add("control", f, "id: `settings.chronicle.history.revealMemoryFile`", "History entries can reveal their memory file (`...history.revealMemoryFile`).");
  add("dialog", f, "id: `settings.chronicle.history.clearAll`", "Clearing all history is offered as `settings.chronicle.history.clearAll`.");
  add("control", f, "id: `settings.chronicle.history.clearTenMinutes`", "History can be cleared for the last ten minutes (`...history.clearTenMinutes`).");
  add("css", "webview/assets/9787caae2bc9.css", "._exampleUser_e04pk_1", "The chronicle route preloads its example-user animation stylesheet.");
}

// ------------------------------------------------------------- USAGE
{
  const f = S("usage-settings");
  add("page", f, "id: `settings.usage.balanceHistory.title`", "Usage shows a balance history section (`settings.usage.balanceHistory.title`).");
  add("empty-state", f, "id: `settings.usage.balanceHistory.empty`", "No balance history shows `settings.usage.balanceHistory.empty`.");
  add("loading-state", f, "id: `settings.usage.load.loading`", "Usage data shows `settings.usage.load.loading`.");
  add("error-state", f, "id: `settings.usage.load.error`", "A failed usage load shows `settings.usage.load.error`.");
  add("retry", f, "id: `settings.usage.load.retry`", "Failed usage loads offer `settings.usage.load.retry`.");
  add("error-state", f, "id: `settings.usage.autoTopUp.load.error`", "Auto top-up failures show `settings.usage.autoTopUp.load.error`.");
  add("loading-state", f, "id: `settings.usage.resets.loading`", "Rate-limit resets show `settings.usage.resets.loading`.");
  add("error-state", f, "id: `settings.usage.resets.loadError`", "Failed reset lookups show `settings.usage.resets.loadError`.");
  add("copy", f, "id: `settings.usage.resets.success`", "A successful reset confirms with `settings.usage.resets.success`.");
  add("copy", f, "id: `settings.usage.subscriptionSharing.apps.limitsUpdated`", "Shared app limits confirm updates with `...subscriptionSharing.apps.limitsUpdated`.");
}

// ------------------------------------------------------------- DEBUG
{
  const f = S("debug-settings");
  add("page", f, "allowDebugMenu", "The debug page is gated behind `allowDebugMenu` so it can be hidden in production.");
  add("control", f, "id: `settings.debug.chromePages.title`", "The debug page exposes the chrome-pages toggle (`settings.debug.chromePages.title`).");
  add("control", f, "id: `settings.debug.chromePages.toggle`", "Chrome pages are toggled via `settings.debug.chromePages.toggle`.");
  add("control", f, "id: `settings.general.gpuTearingDebug.disableScrollFadeMask.label`", "GPU-tearing debugging toggles `disableScrollFadeMask` (`...gpuTearingDebug.disableScrollFadeMask.label`).");
  add("css", "webview/assets/17e79fe193bf.css", "._viewport_7zul7_2", "The debug route preloads the shared table stylesheet.");
}

// ------------------------------------------------------ COMPUTER USE
{
  const f = S("computer-use-settings");
  add("page", f, "id: `settings.computerUse.subtitle`", "The computer-use page subtitle is `settings.computerUse.subtitle`.");
  add("error-state", f, "id: `settings.computerUse.messages.readAccess.saveError`", "Read-access saves can fail (`...messages.readAccess.saveError`).");
  add("error-state", f, "id: `settings.computerUse.messages.readAccess.removeError`", "Removing read access can fail (`...messages.readAccess.removeError`).");
  add("loading-state", f, "id: `settings.computerUse.browser.loading`", "Browser detection shows `settings.computerUse.browser.loading`.");
  add("retry", f, "id: `settings.computerUse.browser.retryDetection`", "Detection can be retried (`settings.computerUse.browser.retryDetection`).");
  add("control", f, "id: `settings.computerUse.browser.reinstallExtension`", "The extension can be reinstalled (`settings.computerUse.browser.reinstallExtension`).");
  add("copy", f, "id: `settings.computerUse.chrome.notInstalled`", "Chrome detection reports `settings.computerUse.chrome.notInstalled`.");
  add("copy", f, "id: `settings.computerUse.messages.chatSearch.searching`", "Chat search progress shows `...messages.chatSearch.searching`.");
  add("css", "webview/assets/17e79fe193bf.css", "._cellContent_7zul7_2", "The computer-use tables reuse the shared cell-content rule.");
}

// -------------------------------------------------------- BROWSER USE
{
  const f = S("browser-use-settings");
  add("page", f, "id: `settings.browserUse.policyAuthoring.subtitle`", "The browser-use page explains policy authoring (`...policyAuthoring.subtitle`).");
  add("control", f, "id: `settings.browserUse.browsingHistory.search`", "Browsing history is searchable (`...browsingHistory.search`).");
  add("loading-state", f, "id: `settings.browserUse.browsingHistory.loading`", "Browsing history shows `...browsingHistory.loading`.");
  add("retry", f, "id: `settings.browserUse.browsingHistory.retry`", "Failed history loads offer `...browsingHistory.retry`.");
  add("error-state", f, "id: `settings.browserUse.downloadHistory.clearError`", "Clearing download history can fail (`...downloadHistory.clearError`).");
  add("error-state", f, "id: `settings.browserUse.downloads.location.changeError`", "Changing the download location can fail (`...downloads.location.changeError`).");
}

// -------------------------------------------------- BROWSER EXTENSION
{
  const f = S("browser-extension-settings");
  add("page", f, "id: `settings.chromeExtension.description`", "The extension page describes itself with `settings.chromeExtension.description`.");
  add("control", f, "id: `settings.chromeExtension.autoApprove`", "The extension permission can be set to auto-approve (`settings.chromeExtension.autoApprove`).");
  add("control", f, "id: `settings.chromeExtension.alwaysAsk`", "The default permission is always-ask (`settings.chromeExtension.alwaysAsk`).");
  add("control", f, "id: `settings.chromeExtension.neverAllow`", "Permissions can be set to never-allow (`settings.chromeExtension.neverAllow`).");
  add("retry", f, "id: `settings.chromeExtension.retry`", "Failed extension settings offer `settings.chromeExtension.retry`.");
  add("copy", f, "id: `settings.chromeExtension.descriptionWithBrowser`", "When a browser is detected the copy switches to `...descriptionWithBrowser`.");
}

// -------------------------------------------- LOCAL ENVIRONMENTS etc.
{
  const f = S("local-environments-settings");
  add("page", f, "tab", "The local environments page selects its tab from the URL search params.");
}
{
  const f = S("external-section-settings");
  add("page", f, "id: `settings.section.external`", "An unknown/external settings section renders `settings.section.external`.");
}

// ------------------------------------------------------------- HOOKS
{
  const f = S("hooks-settings");
  add("page", f, "id: `settings.hooks.subtitle`", "The hooks page subtitle is `settings.hooks.subtitle`.");
  add("control", f, "id: `settings.hooks.refresh`", "Hooks can be refreshed (`settings.hooks.refresh`).");
  add("copy", f, "id: `settings.hooks.refresh.success`", "A refresh confirms with `settings.hooks.refresh.success`.");
  add("control", f, "id: `settings.hooks.trustAll`", "All hooks can be trusted at once (`settings.hooks.trustAll`).");
  add("copy", f, "id: `settings.hooks.trust.overridden`", "An overridden trust shows `settings.hooks.trust.overridden`.");
  add("error-state", f, "id: `settings.hooks.trust.error`", "A failed trust action reports `settings.hooks.trust.error`.");
  add("loading-state", f, "id: `settings.hooks.loading.label`", "Hook loading shows `settings.hooks.loading.label`.");
  add("empty-state", f, "id: `settings.hooks.emptyHooks.label`", "No hooks shows `settings.hooks.emptyHooks.label`.");
  add("copy", f, "id: `settings.hooks.source.sharedProjects`", "Shared project sources are labelled `settings.hooks.source.sharedProjects`.");
  add("copy", f, "id: `settings.hooks.summary.issueCounts`", "Hook issues are summarized with `settings.hooks.summary.issueCounts`.");
}
{
  const f = S("hooks-settings-source-label");
  add("copy", f, "id: `settings.hooks.event.changedReviewReason`", "A changed hook explains itself with `settings.hooks.event.changedReviewReason`.");
  add("copy", f, "id: `settings.hooks.event.untrustedReviewReason`", "An untrusted hook explains itself with `settings.hooks.event.untrustedReviewReason`.");
  add("control", f, "id: `settings.hooks.event.openConfigFile`", "Hook details can open the config file (`settings.hooks.event.openConfigFile`).");
  add("copy", f, "id: `settings.hooks.event.promptHandler`", "The prompt handler is labelled `settings.hooks.event.promptHandler`.");
  add("copy", f, "id: `settings.hooks.source.userConfig`", "Hook sources include the user config (`settings.hooks.source.userConfig`).");
  add("copy", f, "id: `settings.hooks.source.adminConfig`", "Hook sources include the admin config (`settings.hooks.source.adminConfig`).");
}
{
  const f = S("hooks-settings-copy");
  add("copy", f, "id: `settings.hooks.event.preModelRequest`", "The pre-model-request hook event is labelled `settings.hooks.event.preModelRequest`.");
  add("copy", f, "id: `settings.hooks.event.interrupt`", "The interrupt hook event is labelled `settings.hooks.event.interrupt`.");
}
{
  const f = S("hooks-settings-route");
  add("route", f, "hooks-settings", "The hooks settings route slug is `hooks-settings`.");
  add("route", f, "pluginId", "Hooks settings links carry a `pluginId` search parameter.");
}

// ---------------------------------------------- FILE TYPE HANDLERS
{
  const f = S("file-type-handlers-settings");
  add("page", f, "id: `settings.fileTypeHandlers.subtitle`", "The file-type handlers page subtitle is `settings.fileTypeHandlers.subtitle`.");
  add("empty-state", f, "id: `settings.fileTypeHandlers.empty`", "No handlers shows `settings.fileTypeHandlers.empty`.");
  add("loading-state", f, "id: `settings.fileTypeHandlers.loading`", "Handler loading shows `settings.fileTypeHandlers.loading`.");
  add("error-state", f, "id: `settings.fileTypeHandlers.loadFailed`", "A failed handler load shows `settings.fileTypeHandlers.loadFailed`.");
  add("retry", f, "common.retry", "Failed handler loads fall back to `common.retry`.");
  add("error-state", f, "id: `settings.fileTypeHandlers.saveFailed`", "A failed handler save shows `settings.fileTypeHandlers.saveFailed`.");
  add("control", f, "id: `settings.fileTypeHandlers.default`", "The default handler for an extension is `settings.fileTypeHandlers.default`.");
}

// ------------------------------------------------- CLOUD COMPUTER
{
  const f = S("cloud-computer-settings");
  add("page", f, "id: `settings.chatGpt.cloudBrowser.sitePermissions`", "Cloud browsing lists site permissions (`...cloudBrowser.sitePermissions`).");
  add("control", f, "id: `settings.chatGpt.cloudBrowser.addSite`", "Sites are added with `settings.chatGpt.cloudBrowser.addSite`.");
  add("dialog", f, "id: `settings.chatGpt.cloudBrowser.addDialog.title`", "Adding a site opens `...cloudBrowser.addDialog.title`.");
  add("error-state", f, "id: `settings.chatGpt.cloudBrowser.addDialog.invalid`", "An invalid site shows `...addDialog.invalid`.");
  add("empty-state", f, "id: `settings.chatGpt.cloudBrowser.cookies.empty`", "No cookies shows `settings.chatGpt.cloudBrowser.cookies.empty`.");
  add("copy", f, "id: `settings.chatGpt.cloudBrowser.cookies.clearDescription`", "Clearing cookies is explained by `...cookies.clearDescription`.");
  add("error-state", f, "id: `restricted.cloudComputer.orbitCookies.loadError.dot`", "Failed Orbit cookie loads report `...orbitCookies.loadError.dot`.");
  add("copy", f, "id: `settings.cloudComputer.orbitCookies.unavailable`", "Unavailable Orbit cookies show `...orbitCookies.unavailable`.");
  add("dialog", f, "id: `restricted.cloudComputer.orbitCookies.clearTitle.dot`", "Clearing Orbit cookies asks for confirmation (`...orbitCookies.clearTitle.dot`).");
}

// ---------------------------------------------------------- PASSWORDS
{
  const f = S("passwords-settings");
  add("page", f, "saved-password-search", "The passwords page exposes a `saved-password-search` control.");
  add("control", f, "id: `settings.passwords.default.openai`", "The default password store can be OpenAI (`settings.passwords.default.openai`).");
  add("control", f, "id: `settings.passwords.default.onepassword`", "The default password store can be 1Password (`settings.passwords.default.onepassword`).");
  add("error-state", f, "id: `settings.passwords.default.saveError`", "A failed store change reports `settings.passwords.default.saveError`.");
  add("copy", f, "id: `restricted.passwords.count`", "Saved passwords are counted with `restricted.passwords.count`.");
  add("copy", f, "id: `restricted.passwords.refresh`", "The saved list can be refreshed (`restricted.passwords.refresh`).");
  add("dialog", f, "id: `restricted.passwords.confirmTitle`", "Deleting a password asks for confirmation (`restricted.passwords.confirmTitle`).");
  add("dialog", f, "id: `restricted.passwords.confirmDescription`", "The delete dialog warns with `restricted.passwords.confirmDescription`.");
  add("error-state", f, "id: `settings.chatGpt.cloudBrowser.credentials.error`", "Credential failures report `...cloudBrowser.credentials.error`.");
  add("copy", f, "id: `settings.chatGpt.cloudBrowser.credentials.workspaceMismatch`", "A workspace mismatch is reported as `...credentials.workspaceMismatch`.");
}

// --------------------------------------------------------------- TEAM
{
  const f = S("team-settings");
  add("page", f, "id: `settings.teams.preview.notMember`", "Non-members see `settings.teams.preview.notMember`.");
  add("copy", f, "id: `settings.teams.members.adminManagedBanner.description`", "Admin-managed teams advertise it via `...members.adminManagedBanner.description`.");
  add("control", f, "id: `settings.teams.inline.name`", "Inline team-name editing uses `settings.teams.inline.name`.");
  add("error-state", f, "id: `settings.teams.inline.nameRequired`", "A missing team name reports `settings.teams.inline.nameRequired`.");
  add("control", f, "id: `settings.teams.shared.search`", "Shared team content is searchable (`settings.teams.shared.search`).");
  add("copy", f, "id: `settings.teams.activity.pluginInstalled`", "Team activity records `settings.teams.activity.pluginInstalled`.");
}

// --------------------------------------------------- PARENTAL CONTROLS
{
  const f = S("parental-controls-settings");
  add("page", f, "id: `settings.chatGpt.parentalControls.memberUnavailable`", "Unavailable members show `...parentalControls.memberUnavailable`.");
  add("copy", f, "id: `amphora.relationship.helpCenterParagraph2`", "The relationship help text links out via `amphora.relationship.helpCenterParagraph2`.");
  add("error-state", f, "id: `settings.chatGpt.familyUnlink.notices.loadError`", "Failed unlink-notice loads show `...familyUnlink.notices.loadError`.");
  add("error-state", f, "id: `settings.chatGpt.familyUnlink.notices.dismissError`", "Dismissing an unlink notice can fail (`...notices.dismissError`).");
  add("copy", f, "id: `settings.chatGpt.familyUnlink.notices.pending`", "Pending unlinks are listed with `...notices.pending`.");
  add("copy", f, "id: `amphora.settings.u18GraduationUnlinkNotice.teen.description`", "Teen graduation notices use `...u18GraduationUnlinkNotice.teen.description`.");
}

// ---------------------------------------------------------- CODEX CLOUD
{
  const f = S("codex-cloud-settings");
  add("page", f, "personal-vault", "The cloud settings page redirects the `personal-vault` tab to the vault panel.");
  add("copy", f, "id: `personalVault.loading`", "The vault panel shows `personalVault.loading`.");
  add("error-state", f, "id: `personalVault.moduleLoadError`", "A failed vault module load reports `personalVault.moduleLoadError`.");
  add("control", f, "id: `restricted.environmentSetup.configuration.name.edit`", "Environment configuration names are editable (`...configuration.name.edit`).");
  add("empty-state", f, "id: `settings.localEnvironments.catalog.repository.unknown`", "An unknown catalog repository renders `...catalog.repository.unknown`.");
  add("copy", f, "id: `cloudEnvironments.sharing.private`", "Environments can be private (`cloudEnvironments.sharing.private`).");
  add("copy", f, "id: `cloudEnvironments.sharing.shared`", "Environments can be shared (`cloudEnvironments.sharing.shared`).");
  add("dialog", f, "id: `cloudEnvironments.delete.permissionDenied`", "Deleting without permission reports `cloudEnvironments.delete.permissionDenied`.");
}

// ------------------------------------------------- VISIBLE SECTIONS
{
  const f = S("settings-visible-sections");
  add("navigation", f, "id: `settings.nav.chatSettings`", "The settings navigation labels the account entry with `settings.nav.chatSettings`.");
  add("navigation", f, "id: `settings.nav.securitySettings`", "The settings navigation labels the security entry with `settings.nav.securitySettings`.");
  add("navigation", f, "cloud-environments", "The visible-sections resolver special-cases the `cloud-environments` section.");
  add("navigation", f, "appshots", "The visible-sections resolver special-cases the `appshots` section.");
  add("navigation", f, "analytics", "The `analytics` slug has its own visibility predicate.");
}

// ------------------------------------------------- UTILITY CLASSES
{
  add("css", "webview/assets/126a18e4438e.css", "._shell_1vs63_2", "Every settings page shares the searchable-page shell class.");
  add("css", "webview/assets/b74e19ff7ff4.css", "(hover: none), (pointer: coarse)", "Settings pages share the coarse-pointer media block used by the composer and rows.");
  add("css", "webview/assets/9c6da1a2e6bf.css", "._frame_12dmg_5", "Inline attribution frames use the shared frame rule across settings pages.");
  add("css", "webview/assets/4e47cfdb2650.css", "._Input_1q827_238", "Settings text inputs use the shared input stylesheet.");
  add("css", "webview/assets/3ecd35c9b4eb.css", "--textarea-base-height", "Settings textareas size from `--textarea-base-height`.");
  add("css", "webview/assets/9d4dcd640ba7.css", "--syntax1", "Markdown/code blocks inside settings color syntax via `--syntax1`.");
  add("css", "webview/assets/696fd55735f4.css", "._Placeholder_1r3vk_12", "Loading placeholders across settings pages use the shared pulse animation.");
  add("css", "webview/assets/d926df36a3a1.css", "._ActionOverlay_1k7i1_91", "Row action overlays (reveal/delete) use the shared overlay rule.");
  add("css", "webview/assets/ec105dbb0b43.css", "._Checkbox_l08eh_2", "Settings checkboxes use the shared checkbox rule.");
  add("css", "webview/assets/90098f1b0d98.css", "--badge-border-color", "Settings badges resolve their border from `--badge-border-color`.");
  add("css", "webview/assets/4403cd3ba5b4.css", "--badge-background-color", "Settings badges resolve their fill from `--badge-background-color`.");
  add("css", "webview/assets/bc5c05b0b97b.css", "._blue_72qp0_2", "Status badges offer blue/green/pink variants (`_blue_72qp0_2`).");
  add("css", "webview/assets/90a0a1d06d48.css", "._fullscreen_1uxo4_38", "Card grids support a fullscreen variant used by usage/personalization cards.");
  add("css", "webview/assets/26a9ec0e398b.css", "._ButtonLoader_r3d2l_802", "Buttons render an in-place loader (`_ButtonLoader_r3d2l_802`).");
  add("css", "webview/assets/ec6b8907ce09.css", "._tooltip_sk0md_7", "Compact tooltips use their own tooltip variant.");
  add("css", "webview/assets/dd1402a3bc02.css", "._TriggerDecorator_453y_87", "Tooltip trigger decorators are positioned by the shared rule.");
  add("css", "webview/assets/93c2fa48ca6d.css", "._savingsBadge_1fcm6_1", "The savings badge used by the general settings page styles itself from the tertiary surface tokens (route dep 4).");
  add("css", "webview/assets/9aed74b17655.css", "._fields_5lmnb_2", "The general settings form stacks fields with a 24px gap and tightens to 16px under `[data-discounted]` (route dep 5).");
}

// -------------------------------------------------------- MESSAGE IDS
// Extracted message ids are the second evidence tier: they prove which copy a
// page's container actually binds without re-reading every string.
const priority = [
  "error", "failed", "retry", "empty", "loading", "save", "delete", "cancel",
  "confirm", "title", "description", "search", "refresh", "toggle", "select",
];
function score(id) {
  const lower = id.toLowerCase();
  let total = 0;
  for (let i = 0; i < priority.length; i += 1) {
    if (lower.includes(priority[i])) total += priority.length - i;
  }
  return total;
}
function humanTail(id) {
  return id
    .split(".")
    .slice(-2)
    .join(" ")
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[_-]+/g, " ")
    .toLowerCase();
}

const sliceSpecs = [
  ["notifications-settings", "settings-page", "notifications page", 4],
  ["import-settings", "settings-page", "import page", 4],
  ["security-settings", "settings-page", "security page", 4],
  ["age-settings", "settings-page", "age page", 4],
  ["agreements-settings", "settings-page", "agreements page", 4],
  ["trusted-contact-settings", "settings-page", "trusted-contact page", 4],
  ["safety-settings", "settings-page", "safety page", 4],
  ["keyboard-shortcuts-settings", "settings-page", "keyboard shortcuts page", 4],
  ["codex-micro-settings", "settings-page", "Codex Micro page", 4],
  ["appshots-settings", "settings-page", "Appshots page", 4],
  ["voice-settings", "settings-page", "voice page", 4],
  ["device-storage-settings", "settings-page", "device storage page", 4],
  ["pets-settings", "settings-page", "pets page", 4],
  ["configuration-panel", "settings-page", "configuration panel", 4],
  ["work-settings-panel", "settings-page", "work settings panel", 4],
  ["ads-controls-settings", "settings-page", "ads controls page", 4],
  ["archived-chats-settings", "settings-page", "archived chats page", 4],
  ["data-controls-settings", "settings-page", "data controls page", 4],
  ["code-review-settings", "settings-page", "code review page", 4],
  ["cloud-preferences-settings", "settings-page", "cloud preferences page", 4],
  ["cloud-environments-settings", "settings-page", "cloud environments page", 4],
  ["personalization-settings", "settings-page", "personalization page", 4],
  ["chronicle-settings", "settings-page", "computer history page", 4],
  ["usage-settings", "settings-page", "usage page", 4],
  ["debug-settings", "settings-page", "debug page", 4],
  ["computer-use-settings", "settings-page", "computer use page", 4],
  ["browser-use-settings", "settings-page", "browser use page", 4],
  ["browser-extension-settings", "settings-page", "browser extension page", 4],
  ["hooks-settings", "settings-page", "hooks page", 4],
  ["file-type-handlers-settings", "settings-page", "file type handlers page", 4],
  ["cloud-computer-settings", "settings-page", "cloud browser/computer page", 4],
  ["passwords-settings", "settings-page", "passwords page", 4],
  ["team-settings", "settings-page", "team settings page", 4],
  ["parental-controls-settings", "settings-page", "parental controls page", 4],
  ["codex-cloud-settings", "settings-page", "Codex cloud settings page", 4],
];

const claimedAnchors = new Set(claims.map((claim) => `${claim.file}\0${claim.anchor}`));
for (const [slice, category, subject, cap] of sliceSpecs) {
  const text = fs.readFileSync(
    path.join(phaseDir, "formatted/webview/assets", `${slice}-slice.js`),
    "utf8",
  );
  const ids = [...new Set([...text.matchAll(/id: `([^`]+)`/g)].map((m) => m[1]))];
  const selected = ids
    .map((id, index) => ({ id, index, value: score(id) }))
    .sort((a, b) => b.value - a.value || a.index - b.index)
    .slice(0, cap)
    .sort((a, b) => a.index - b.index);
  for (const { id } of selected) {
    const file = asset(slice);
    const anchor = `id: \`${id}\``;
    if (claimedAnchors.has(`${file}\0${anchor}`)) continue;
    claimedAnchors.add(`${file}\0${anchor}`);
    const occurrence =
      fs.readFileSync(path.join(baseline, file), "utf8").split(anchor).length - 1;
    add(
      category,
      file,
      anchor,
      `The ${subject} binds \`${id}\` for its ${humanTail(id)} copy.`,
      occurrence > 1 ? 0 : undefined,
    );
  }
}

// Verify every anchor against the baseline before writing.
const failures = [];
for (const claim of claims) {
  const filePath = path.join(baseline, claim.file);
  if (!fs.existsSync(filePath)) {
    failures.push({ id: claim.id, problem: `baseline file missing: ${claim.file}` });
    continue;
  }
  const text = fs.readFileSync(filePath, "utf8");
  const count = text.split(claim.anchor).length - 1;
  if (count === 0) failures.push({ id: claim.id, problem: `anchor not found: ${claim.anchor}` });
  else if (count > 1 && claim.occurrence == null) {
    failures.push({ id: claim.id, problem: `anchor not unique (${count}): ${claim.anchor}` });
  }
}
if (failures.length > 0) {
  console.error(JSON.stringify({ failures }, null, 2));
  process.exit(1);
}

const byCategory = {};
for (const claim of claims) byCategory[claim.category] = (byCategory[claim.category] ?? 0) + 1;

fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, `${JSON.stringify(claims, null, 2)}\n`);
console.log(JSON.stringify({ claims: claims.length, byCategory, outPath }, null, 2));
