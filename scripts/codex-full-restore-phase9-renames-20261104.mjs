#!/usr/bin/env node
/**
 * Phase 9 (plugins + account) rename specifications.
 *
 * Writes one <slice>.json rename file per bounded slice under
 * 09-semantic/renames/. Each entry binds one minified top-level
 * FunctionDeclaration in the formatted slice to a semantic name, with the
 * declaration anchor used by codex-full-restore-semantic-20261103.mjs.
 *
 * Usage:
 *   node scripts/codex-full-restore-phase9-renames-20261104.mjs [--out <dir>]
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const outIndex = process.argv.indexOf("--out");
const outDir =
  outIndex >= 0
    ? path.resolve(process.argv[outIndex + 1])
    : path.join(
        root,
        "references/codex-26.1027.61615-24141/full-restore/09-semantic/renames",
      );

const A = "webview/assets";
const map = {
  "account-menu.items-slice": {
    source: `${A}/profile-dropdown-items-8e551671a439.js`,
    renames: [
      ["ya", "AccountWorkspaceSwitcher", "Workspace switcher rows in the account menu; switches workspace, lists sessions and exposes workspace-switching progress/errors."],
      ["Ea", "ProfileStreakBadge", "Streak badge shown next to the profile identity in the account menu."],
      ["Ra", "WhatsNewMenu", "What's-new entry and submenu inside the account menu."],
      ["Ua", "SidebarHelpMenu", "Help entry and submenu in the account/sidebar menu."],
      ["go", "AddTeammatesMenuItem", "Account-menu item that opens the add-teammates flow."],
    ],
  },
  "account-menu.menu-slice": {
    source: `${A}/profile-dropdown-items-8e551671a439.js`,
    renames: [
      ["bo", "AccountMenu", "Profile dropdown root: account identity, workspace switching, auth-method rows, upgrade entry, copy user id and auth-switch errors."],
      ["uo", "AccountMenuItems", "Account menu action items: admin console, workspace settings, profile, settings page and log out."],
    ],
  },
  "account-settings.deletion-slice": {
    source: `${A}/account-settings-e17477910122.js`,
    renames: [
      ["Dn", "DeleteAccountDialog", "Delete-account dialog requiring typed DELETE, matching contact email/phone, warnings and refresh-login recovery."],
      ["Xn", "AccountNameField", "Inline account name field in the account settings page."],
      ["Zn", "BuilderProfileField", "Builder profile field in account settings."],
      ["Qn", "AccountInlineEditDialog", "Generic inline edit dialog used by account settings fields."],
      ["ar", "AccountStatusUnavailable", "Unavailable state for the account status section."],
      ["hr", "AccountPolicyLabel", "Policy label rendered by account status details."],
      ["Rr", "initAccountStatusModule", "Initializer for the account status module runtime."],
    ],
  },
  "account-settings.page-slice": {
    source: `${A}/account-settings-e17477910122.js`,
    renames: [
      ["An", "mapAccountDeletionError", "Maps account deletion failures to SCIM, business-owner or generic messages."],
      ["dr", "AccountStandingBadge", "Account standing badge shown in the account settings header."],
      ["fr", "AccountStatusActionDetails", "Account status action details: policy links, related content and appeal actions."],
      ["yr", "AccountStatusSection", "Account status section with title, description and standing details."],
      ["Mr", "AccountEmailVerificationSection", "Email verification section with reauth errors, verified state and email OTP flow."],
      ["zr", "AccountUsernameField", "Username field with add/retry states in account settings."],
      ["Gr", "ChatGptAccountSettings", "Account settings page exported as ChatGptAccountSettings; composes name, builder profile, email verification, username, status and deletion."],
    ],
  },
  "add-plugins-modal.team-slice": {
    source: `${A}/add-plugins-modal-a49c5b889f5a.js`,
    renames: [
      ["Ke", "TeamPluginsTab", "Team plugins tab in the add-plugins modal."],
      ["rt", "TeamConnectionsTab", "Team connections tab in the add-plugins modal."],
      ["dt", "TeamAddPluginsModal", "Team add-plugins modal exported to settings, hosting the plugins and connections tabs."],
    ],
  },
  "app-primary.plugin-entry-slice": {
    source: `${A}/app-primary-75d53eef2374.js`,
    renames: [
      ["ubn", "openPluginDirectory", "Sidebar handler that navigates to /plugins (plugin directory) or /skills, carrying host id and plugin directory entrypoint state."],
    ],
  },
  "app-runtime.logout-confirmation-slice": {
    source: `${A}/app-runtime-b179428fcc1e.js`,
    renames: [
      ["Dq", "LogOutConfirmationDialog", "Codex log-out confirmation dialog with codex.logout.confirmation.* message ids."],
      ["kq", "ProfileDropdownLogOutConfirmation", "Log-out confirmation specific to the profile dropdown, with codex.profileDropdown.logOutConfirmation.* ids."],
    ],
  },
  "billing-settings.page-slice": {
    source: `${A}/billing-settings-60ad13dec361.js`,
    renames: [
      ["or", "BillingInformationSection", "Billing information section of the consumer billing settings page."],
      ["ur", "BillingInformationEditor", "Editor for billing information fields."],
      ["Tr", "BankMandateBanner", "Bank mandate banner shown in billing settings."],
      ["jr", "PaymentMethodsSection", "Payment methods section in billing settings."],
      ["Rr", "CancelPlanSection", "Cancel-plan section in billing settings."],
      ["Qr", "TransactionHistoryDialog", "Transaction history dialog."],
      ["$r", "RecentTransactionsSection", "Recent transactions section of the billing page."],
      ["si", "WalletBalanceSection", "Wallet balance section of the billing page."],
      ["fi", "ConsumerBillingSettings", "Consumer billing settings page exported as ConsumerBillingSettings."],
      ["pi", "ConsumerBillingDetails", "Consumer billing details summary used by the billing page."],
    ],
  },
  "browser-logout.page-slice": {
    source: `${A}/logout-page-9ab3534af819.js`,
    renames: [
      ["Qe", "BrowserLogoutImpactList", "Lists the apps and impact of signing out of the browser session."],
      ["$e", "BrowserLogoutPage", "Browser-session log-out page with impact loading, rate-limit notice, failure and stay-logged-in actions."],
    ],
  },
  "browser-plugin-settings.page-slice": {
    source: `${A}/routes-c955ba8f50d3.js`,
    renames: [
      ["eo", "PluginRemoveDialog", "Confirmation dialog for removing a plugin from its settings page."],
      ["Lo", "BrowserPluginSettingsPage", "Browser plugin settings page: permissions, sync, connect/reconnect/disconnect and admin-disabled states."],
      ["Ko", "PluginRemoveSection", "Remove-plugin section of the plugin settings page."],
      ["Qo", "PluginHooksSection", "Plugin hooks section listing hook uses and hook settings."],
      ["cs", "PluginInformationSection", "Plugin information section: about, category, developer, version, website, privacy and terms."],
      ["bs", "PluginTemplateConnectionRow", "Row for a template connection in browser plugin settings."],
      ["xs", "AddTemplateConnectionButton", "Button that adds a template connection."],
    ],
  },
  "login-route.bedrock-slice": {
    source: `${A}/login-route-814949a1c03e.js`,
    renames: [
      ["qn", "BedrockLoginForm", "Amazon Bedrock login form: credentials, profile, region and environment fields with validation."],
    ],
  },
  "login-route.page-slice": {
    source: `${A}/login-route-814949a1c03e.js`,
    renames: [
      ["gn", "LoginSnakeGame", "Snake easter-egg animation rendered on the login surface; owns refs and an audio context."],
      ["An", "AuthRedirectBridge", "Handles auth redirect source/target/connector parameters for the login route."],
      ["Nr", "LoginRouteContent", "Login route content: ChatGPT and API-key sign-in, Bedrock, browser session profiles, copy-sign-in-link, pending and fallback states."],
      ["Pr", "BrowserSessionContinueAsPrompt", "Prompt to continue as a detected browser session profile, including continue-as-with-email."],
      ["Gr", "LoginRoute", "Login route wrapper exported as LoginRoute; handles workspace recovery before rendering LoginRouteContent."],
    ],
  },
  "login-route.status-slice": {
    source: `${A}/login-route-814949a1c03e.js`,
    renames: [
      ["Er", "LoginFallbackMethods", "Fallback sign-in methods: ChatGPT, Google, Apple, email, terms/privacy and API key."],
      ["zr", "LoginStatusView", "Login status view: sign-in failure, Bedrock unavailable/profile credentials, link copied, finishing and loading states."],
    ],
  },
  "manage-plugins-page.redirect-slice": {
    source: `${A}/manage-plugins-page-2376741217c8.js`,
    renames: [
      ["c", "ManagePluginsPage", "Legacy /skills/manage/plugins route; redirects to /settings/plugins-settings when plugin routes are eligible, else /skills."],
    ],
  },
  "mcp-settings.page-slice": {
    source: `${A}/mcp-settings-347422a00db2.js`,
    renames: [
      ["d", "McpSettings", "MCP settings route wrapper with title slug mcp-settings and subtitle \"Connect external tools and data sources\"; renders plugins settings in manage-only mode."],
    ],
  },
  "plugin-card-status-action.status-slice": {
    source: `${A}/plugin-card-status-action-c759a8c4aec7.js`,
    renames: [
      ["x", "PluginCardStatusAction", "Status action shown on a plugin card (install/enable/connected state)."],
      ["O", "initPluginCardStatusAction", "Initializer for the plugin card status action runtime."],
    ],
  },
  "plugin-detail-actions.actions-slice": {
    source: `${A}/plugin-detail-actions-39d180f4a164.js`,
    renames: [
      ["Mt", "PluginManagementMenu", "Plugin management menu: download, upload, delete and manage-for-workspace actions."],
      ["Lt", "PluginDesktopHandoffDialog", "Dialog that hands a plugin action off to the desktop app."],
      ["Kt", "PluginUpgradeCallToAction", "Upgrade call to action for setup/install/Plus/Pro plans."],
      ["Qt", "PluginDetailActions", "Plugin detail action bar: install, enable, edit, settings, more actions, uninstall, upgrade and desktop-only states."],
      ["$t", "UninstallPluginDialog", "Uninstall confirmation dialog that warns which accounts are disconnected."],
    ],
  },
  "plugin-detail-page.page-slice": {
    source: `${A}/plugin-detail-page-e8ebc85716e6.js`,
    renames: [
      ["A", "PluginDetailPage", "Plugin detail page: disconnected app, admin-disabled editing, share link/menu, configured-app template, edit-with-creator and migration/share error states."],
    ],
  },
  "plugin-detail-page.settings-wrapper-slice": {
    source: `${A}/plugin-detail-page-e8ebc85716e6.js`,
    renames: [
      ["_a", "PluginMigrationBanner", "Plugin migration banner with edit banner and dismiss-copy-warnings actions."],
      ["Pa", "PluginSettingsDeepLink", "Rewrites /skills/manage/plugins/ deep links to /plugins/ and sets breadcrumb=settings."],
    ],
  },
  "plugin-detail-view.view-slice": {
    source: `${A}/plugin-detail-view-7240e318ab04.js`,
    renames: [
      ["wn", "PluginDependenciesList", "Plugin dependency list with expand/collapse and overflow handling."],
      ["An", "PluginDependencyStatusLabel", "Status label for a plugin dependency."],
      ["Pn", "PluginDependenciesSection", "Section wrapping plugin dependencies."],
      ["Gn", "PluginInstancesSection", "Plugin instances section with title, loading, empty, error, retry and load-more states."],
      ["$n", "PluginDetailView", "Plugin detail view with loading/error/missing states and the sections nav."],
      ["dr", "PluginIncludesSection", "Plugin includes section: app setup, apps, MCP servers and scheduled tasks."],
      ["Pr", "initPluginDetailView", "Initializer for the plugin detail view runtime."],
    ],
  },
  "plugin-install-modal.modal-slice": {
    source: `${A}/plugin-install-modal-fa4cf76c97b0.js`,
    renames: [
      ["an", "PluginInstallModal", "Install modal wrapper with telemetry and consent handling."],
      ["mn", "PluginInstallDialogContent", "Install dialog content with installing/install actions and admin-disabled state."],
      ["xn", "PluginRequiredAppsSection", "Section listing apps required by the plugin."],
      ["Sn", "PluginRequiredAppsActions", "Actions for required apps in the install dialog."],
      ["Cn", "PluginInstallAboutSection", "About section of the install dialog."],
      ["Tn", "PluginInstallIncludesSection", "Includes section of the install dialog."],
      ["In", "PluginBrowserExtensionActions", "Browser-extension actions in the install dialog."],
      ["Qn", "initPluginInstallModal", "Initializer for the plugin install modal runtime."],
    ],
  },
  "plugin-settings-routes.routes-slice": {
    source: `${A}/routes-c955ba8f50d3.js`,
    renames: [
      ["Mi", "PluginAppSettingsRoute", "Route wrapper for per-app plugin settings."],
      ["Ni", "PluginAppSettingsPage", "Per-app plugin settings page."],
      ["qi", "PluginPermissionsPage", "Plugin permissions settings page."],
      ["gs", "PluginSettingsRouteHost", "Route host that resolves host id for plugin settings routes."],
      ["_s", "PluginSettingsPage", "Plugin settings page; delegates to browser plugin settings sections."],
      ["Os", "PluginDetailSettingsRoutes", "Route table for /settings/plugins-settings/hosts/:hostId/plugins/:pluginId with permissions, apps, sync and skills routes."],
    ],
  },
  "plugins-cards-grid.cards-slice": {
    source: `${A}/plugins-cards-grid-11b2d38dfc40.js`,
    renames: [
      ["Zt", "AddMarketplaceResultHandler", "Handles add-marketplace results and forces a plugin reload."],
      ["cn", "AddMarketplaceDialog", "Dialog for adding a marketplace."],
      ["xn", "PluginCardsGrid", "Grid of plugin cards with loading, load-error and cover states."],
      ["Sn", "PluginCard", "Plugin card: copy share link, connect, disable and actions menu."],
      ["On", "initPluginCardsGrid", "Initializer for the plugin cards grid runtime."],
    ],
  },
  "plugins-route-eligibility.hook-slice": {
    source: `${A}/plugins-route-eligibility-d5e3d5a0dd1f.js`,
    renames: [
      ["p", "usePluginRouteEligibility", "Hook deciding whether plugin routes are eligible for the current host/feature state."],
    ],
  },
  "plugins-settings.marketplace-slice": {
    source: `${A}/plugins-settings-7a89487c0c4c.js`,
    renames: [
      ["Ma", "PluginsLoadError", "Load-error state for the plugins settings surface."],
      ["Na", "PluginsLoadingState", "Loading state for the plugins settings surface."],
      ["Pa", "PluginList", "Plugin list with app rows and empty state."],
      ["za", "initMarketplaceIcons", "Initializer for marketplace icons."],
      ["Ba", "MarketplaceListSection", "Marketplace list section with loading, error, empty, plugin count, upgrade and remove."],
      ["Va", "MarketplaceUpgradeTooltipLabel", "Tooltip label for marketplace upgrade."],
      ["Ha", "MarketplaceRemoveTooltipLabel", "Tooltip label for marketplace removal."],
      ["Ua", "MarketplaceLoadErrors", "Partial marketplace load errors."],
      ["qa", "MarketplaceDetailsDialog", "Marketplace details dialog."],
      ["Ja", "MarketplaceRemoveDialog", "Marketplace removal confirmation dialog."],
      ["so", "PluginsUnsupportedHost", "Unsupported-host state for plugins."],
      ["lo", "MarketplaceManagementList", "Marketplace management list."],
    ],
  },
  "plugins-settings.mcp-slice": {
    source: `${A}/plugins-settings-7a89487c0c4c.js`,
    renames: [
      ["Ji", "AppsDataSettingsSection", "Apps data settings section with clear-data action."],
      ["ra", "McpServerDetailEditor", "MCP server detail editor: stdio/http transport, command/args/env/cwd, bearer/headers, save and uninstall."],
      ["ga", "AddMcpServerPanel", "Add-MCP-server panel with restart-app, no-writable-config, from-plugins and empty states."],
      ["wa", "McpServerAuthControls", "MCP server auth controls: client registration auto/cimd/dcr, authentication starting/login/enable."],
    ],
  },
  "plugins-settings.page-slice": {
    source: `${A}/plugins-settings-7a89487c0c4c.js`,
    renames: [
      ["$i", "EditableListRow", "Editable list row used by plugin settings lists."],
      ["ha", "MyServersSectionLabel", "My-servers section label in MCP settings."],
      ["Ta", "AddMcpServerButton", "Add-MCP-server button."],
      ["$a", "PluginsManagePage", "Plugins manage page with host id, manage-only mode, MCP server creation and manage tabs plugins/apps/mcps/skills/marketplace."],
      ["uo", "PluginsManageTabs", "Manage tabs for plugins/apps/mcps/skills."],
      ["_o", "PluginsSettings", "Plugins settings section page with title, subtitle, browse-directory action and add-MCP-server action."],
      ["So", "initPluginsSettings", "Initializer for the plugins settings runtime."],
    ],
  },
  "plugins-skills-tabs.directory-slice": {
    source: `${A}/plugins-skills-tabs-c731ebf7d521.js`,
    renames: [
      ["Hi", "PluginDirectoryHostDropdown", "Host dropdown for the plugin directory (local/chatgpt)."],
      ["Gi", "PluginDirectoryTabHost", "Host component for plugin directory tabs."],
      ["va", "ImportedConnectorsSection", "Section for imported connectors in the plugin directory."],
      ["Wa", "PluginOwnershipSections", "Created-by-you and shared-with-you plugin sections with empty state."],
      ["ro", "PluginDirectoryList", "Plugin directory list."],
      ["co", "ConnectedPluginsOverflow", "Overflow control for connected plugins."],
      ["Jo", "PluginRequestDialog", "Plugin request dialog with success and rate-limit states."],
      ["Cs", "PluginDirectoryContent", "Plugin directory content with browse intro, directory tabs and plugin search."],
      ["Ms", "PluginDirectoryTabs", "Directory tabs: plugins, skills with new suggestions and browse toggle."],
    ],
  },
  "profile-page.route-slice": {
    source: `${A}/profile-page-8da992e8d64d.js`,
    renames: [
      ["oe", "ProfilePageContent", "Profile page content with workspace people search."],
      ["pe", "ProfilePage", "Profile page exported as ProfilePage; composes layout and content."],
      ["Y", "ProfilePageLayout", "Layout wrapper for the profile page."],
    ],
  },
  "profile-settings.page-slice": {
    source: `${A}/profile-settings-7a7c4a515c30.js`,
    renames: [
      ["x", "ProfileSettings", "Profile settings page."],
      ["S", "ProfileSettingsLayout", "Layout wrapper for profile settings."],
    ],
  },
  "remote-connections-page.redirect-slice": {
    source: `${A}/remote-connections-page-f5440e6cc85f.js`,
    renames: [
      ["s", "RemoteConnectionsPage", "Legacy /remote-connections route; redirects to /settings/connections when the capability exists, else /."],
    ],
  },
  "remote-connections.clients-slice": {
    source: `${A}/remote-connections-settings-4dd0ca54883c.js`,
    renames: [
      ["Li", "RemoteControlClientsSection", "Remote-control clients section: devices, add shortcut and auth-required/generic load errors with retry."],
      ["La", "ConnectionUpdateNotice", "Connection update notice with restart notice and update-required inline state."],
      ["lo", "RemoteDeviceConnectionsSection", "Remote device connections section with allow-connections and update-error states."],
    ],
  },
  "remote-connections.dialogs-slice": {
    source: `${A}/remote-connections-settings-4dd0ca54883c.js`,
    renames: [
      ["Yi", "AddSshConnectionDialog", "Add/discover connection dialog: discovery empty/refresh, add manually, save and toggle selection."],
      ["ro", "RemoteControlDeviceCard", "Remote-control device card with signed-in-device subtitle."],
      ["So", "AuthorizeRemoteControlDialog", "Dialog authorizing remote control of this device."],
      ["ko", "ConnectionDiscoveryDialog", "Dialog for discovering connections on the network."],
      ["zo", "PairingCodeDialog", "Pairing-code dialog for remote connections."],
    ],
  },
  "remote-connections.page-slice": {
    source: `${A}/remote-connections-settings-4dd0ca54883c.js`,
    renames: [
      ["Qo", "RemoteConnectionsSettings", "Remote connections settings page exported as RemoteConnectionsSettings; tabs access-other-devices, control-this-Mac, SSH and WSL with auth-required, refresh, save, rename, delete and add-remote-project states."],
    ],
  },
  "remote-connections.table-slice": {
    source: `${A}/remote-connections-settings-4dd0ca54883c.js`,
    renames: [
      ["xa", "RemoteConnectionRow", "Connection table row with rename input, save/cancel, delete, offline-only, details menu and update-required state."],
      ["Ua", "ConnectionDetailsCard", "Connection details card: delete, settings menu, restart, manage remote-control devices, add remote project and auto-connect."],
      ["Qa", "RemoteConnectionsTable", "Remote connections table with device-list error, empty state and heading."],
      ["co", "RemoteConnectionSummaryRow", "Connection summary row with SSH devices, add shortcut, loading, load error and empty state."],
    ],
  },
  "select-workspace.page-slice": {
    source: `${A}/select-workspace-page-4996bb400437.js`,
    renames: [
      ["Ye", "SelectWorkspacePage", "Select-workspace page with consent error and auto-continue event handling."],
      ["Y", "SelectWorkspaceHeader", "Select-workspace header with title and subtitle."],
      ["ot", "OpenWorkspaceFolderButton", "Button that opens a workspace folder picker."],
      ["st", "WorkspaceRootList", "Workspace root list with loading, list label, select-all, empty and continue states."],
      ["ct", "WorkspaceSkipButton", "Skip-workspace button with skipping and error states."],
    ],
  },
  "settings-plugin-controls.controls-slice": {
    source: `${A}/settings-plugin-controls-cbca110a69d1.js`,
    renames: [
      ["yt", "PluginControlList", "Plugin control list with loading and empty states."],
      ["wt", "PluginControlRow", "Plugin control row: enable/disable, install/uninstall and more actions."],
    ],
  },
  "skills-page.plugins-page-slice": {
    source: `${A}/skills-page-96d0a75209f5.js`,
    renames: [
      ["At", "PluginDirectoryBrowseTab", "Plugin browse tab of the plugin directory page."],
      ["jt", "SkillsBrowseTab", "Skills browse tab of the plugin directory page."],
      ["It", "SkillsPage", "Skills page exported as SkillsPage; renders the router."],
      ["Lt", "SkillsPageRouter", "Chooses the plugin directory page when the plugin feature is enabled, else the skills content."],
      ["Rt", "PluginDirectoryPage", "Plugin directory page hosting the skills/plugins tabs."],
      ["zt", "SkillsPageContent", "Skills page content container."],
      ["Wt", "SkillsPageEmptyState", "Skills page loading/empty state."],
      ["Kt", "SkillsPageGrid", "Skills page list/grid with error state."],
      ["Jt", "initSkillsPage", "Initializer for the skills page runtime."],
    ],
  },
};

fs.mkdirSync(outDir, { recursive: true });
let files = 0;
let bindings = 0;
for (const [sliceName, spec] of Object.entries(map)) {
  const entries = spec.renames.map(([old, newName, evidence]) => ({
    old,
    new: newName,
    declAnchor: `function ${old}(`,
    originalPath: spec.source,
    evidence,
    confidence: "high",
  }));
  fs.writeFileSync(
    path.join(outDir, `${sliceName}.json`),
    `${JSON.stringify(entries, null, 2)}\n`,
  );
  files += 1;
  bindings += entries.length;
}
console.log(JSON.stringify({ outDir, files, bindings }, null, 2));
