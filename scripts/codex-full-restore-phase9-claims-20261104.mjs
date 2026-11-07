#!/usr/bin/env node
/**
 * Phase 9 (plugins + account) UI claim index.
 *
 * Produces 09-semantic/sources/ui-claims.json. Claims are either curated
 * behavioral statements anchored on exact baseline text, or message-id
 * bindings discovered in the bounded slices. Every anchor is checked against
 * the baseline file before the file is written.
 *
 * Usage:
 *   node scripts/codex-full-restore-phase9-claims-20261104.mjs [--out <file>]
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const phaseDir = path.join(
  root,
  "references/codex-26.1027.61615-24141/full-restore/09-semantic",
);
const baseline = path.join(
  root,
  "references/codex-26.1027.61615-24141/full-restore/02-baseline/formatted",
);
const outIndex = process.argv.indexOf("--out");
const outPath =
  outIndex >= 0
    ? path.resolve(process.argv[outIndex + 1])
    : path.join(phaseDir, "sources/ui-claims.json");

const claims = [];
let seq = 0;
function add(category, file, anchor, claim, occurrence) {
  seq += 1;
  const entry = {
    id: `p9-${String(seq).padStart(3, "0")}-${category}`,
    category,
    file,
    anchor,
    claim,
    confidence: "high",
  };
  if (occurrence != null) entry.occurrence = occurrence;
  claims.push(entry);
}

const AR = "webview/assets/app-runtime-b179428fcc1e.js";
const AP = "webview/assets/app-primary-75d53eef2374.js";
const MP = "webview/assets/manage-plugins-page-2376741217c8.js";
const RCP = "webview/assets/remote-connections-page-f5440e6cc85f.js";
const MCP = "webview/assets/mcp-settings-347422a00db2.js";
const SLR = "webview/assets/settings-lazy-routes-8d1f593a29a7.js";
const RTS = "webview/assets/routes-c955ba8f50d3.js";
const PS = "webview/assets/plugins-settings-7a89487c0c4c.js";
const PDA = "webview/assets/plugin-detail-actions-39d180f4a164.js";
const PIM = "webview/assets/plugin-install-modal-fa4cf76c97b0.js";
const RCS = "webview/assets/remote-connections-settings-4dd0ca54883c.js";
const AS = "webview/assets/account-settings-e17477910122.js";
const LR = "webview/assets/login-route-814949a1c03e.js";
const SW = "webview/assets/select-workspace-page-4996bb400437.js";
const PST = "webview/assets/profile-settings-7a7c4a515c30.js";
const PRE = "webview/assets/plugins-route-eligibility-d5e3d5a0dd1f.js";
const PDP = "webview/assets/plugin-detail-page-e8ebc85716e6.js";

// Route entry and ownership.
add("route-entry", AR, "(0, Q.jsx)(H, { path: `/login`, element: (0, Q.jsx)(JM, {}) }),", "The authenticated-shell route tree maps `/login` to the login route component.");
add("route-entry", AR, "(0, Q.jsx)(H, { path: `/welcome`, element: (0, Q.jsx)(QM, {}) }),", "The route tree maps `/welcome` to a dedicated welcome component.");
add("route-entry", AR, "path: `/select-workspace`,", "The route tree maps `/select-workspace` to the workspace-selection page.");
add("route-entry", AR, "path: `/plugins`,", "The route tree mounts the plugin directory at `/plugins`.");
add("route-entry", AR, "path: `/plugins/:pluginId/app/:toolName`,", "A plugin app tool has its own `/plugins/:pluginId/app/:toolName` route.");
add("route-entry", AR, "path: `/remote-connections`,", "The route tree still registers `/remote-connections` as a route.");
add("route-entry", AP, "item: n ? `plugins` : `skills`", "The sidebar marks `plugins` as the active item when the plugin-directory entrypoint is enabled, otherwise `skills`.");
add("route-entry", AP, "t(n ? `/plugins` : `/skills`, {", "The same sidebar handler navigates to `/plugins` or `/skills` depending on that flag.");
add("route-entry", MP, "pathname: `/settings/plugins-settings`,", "The legacy manage-plugins page redirects to `/settings/plugins-settings` when plugin routes are eligible.");
add("route-entry", MP, "to: `/skills`", "The legacy manage-plugins page falls back to `/skills` when plugin routes are not eligible.");
add("route-entry", RCP, "to: `/settings/connections`", "The legacy `/remote-connections` page redirects to `/settings/connections` when the capability is present.");
add("route-entry", SLR, "profile: i(", "Settings registry binds the `profile` section to the profile-settings chunk.");
add("route-entry", SLR, "account: i(", "Settings registry binds the `account` section to the ChatGPT account-settings chunk.");
add("route-entry", SLR, "connections: i(", "Settings registry binds the `connections` section to the remote-connections settings chunk.");
add("route-entry", SLR, "`./plugins-settings-322a6fca639e.js`", "The plugins-settings section is lazy-loaded from its own chunk.");
add("route-entry", SLR, '\"plugins-settings\": i(', "The settings registry key for the plugins surface is `plugins-settings`.");
add("route-entry", RTS, "`/settings/plugins-settings/hosts/:hostId/plugins/:pluginId`", "Plugin settings routes are hosted under `/settings/plugins-settings/hosts/:hostId/plugins/:pluginId`.");
add("route-entry", PDP, "`/skills/manage/plugins/`", "The plugin settings deep link rewrites the legacy `/skills/manage/plugins/` prefix to `/plugins/`.");
add("route-entry", PDP, "`breadcrumb`, `settings`", "The same deep link marks the route with `breadcrumb=settings`.");
add("route-entry", MCP, "slug: `mcp-settings`", "The MCP settings page identifies itself with the `mcp-settings` slug.");
add("route-entry", PRE, "initialHostId ?? `local`", "Plugin route eligibility defaults the host id to `local` when the navigation state does not provide one.");

// Account menu and account settings.
add("account-menu", AS, "defaultMessage: `DELETE`", "Deleting an account requires typing the keyword `DELETE`.");
add("account-menu", PDA, "disconnectsAllAccounts: Ce,", "The uninstall dialog receives whether the plugin is connected to all accounts.");
add("account-settings", AS, "id: `settings.chatGpt.account.deletion.confirmation.login_expired.description`", "Account deletion has a dedicated login-expired recovery message.");

// Login.
add("login", LR, "allowApiKey: n,", "The login route receives an `allowApiKey` flag.");
add("login", LR, "allowChatGpt: r,", "The login route receives an `allowChatGpt` flag.");
add("login", LR, "isOtherSignInMethodsVisible: u,", "The login route receives whether other sign-in methods are visible.");


// Select workspace.
add("select-workspace", SW, "i(`electron-onboarding-skip-workspace-result`, Se));", "Skipping workspace selection records the `electron-onboarding-skip-workspace-result` telemetry event.");
add("select-workspace", SW, "selectAllState: ve !== `indeterminate` && ve,", "The workspace list derives its select-all state from the tri-state selection.");
add("select-workspace", SW, "j(`skip`),", "The skip path dispatches a `skip` action before continuing.");

// Plugin surfaces.
add("plugin-install", PIM, "variant: `consent`", "The install modal renders a consent-variant blurb before installation.");
add("plugin-install", PIM, "id: `plugins.detail.disabledByAdminButton`", "The install modal shows an admin-disabled install button state.");
add("plugin-detail", PDA, "disconnectsAllAccounts: n,", "The uninstall dialog receives the disconnects-all-accounts flag from the detail actions.");
add("plugin-detail", PDA, "id: `plugins.detail.uninstall.disconnectsAllAccounts`", "The uninstall dialog warns when uninstalling disconnects all accounts.");
add("plugin-marketplace", PS, "defaultMessage: `Only Git marketplaces can be upgraded`", "Marketplace upgrade is disabled for non-Git marketplaces with that explanation.");
add("plugin-marketplace", PS, "defaultMessage: `Built-in marketplaces cannot be removed`", "Built-in marketplaces cannot be removed.");
add("plugin-marketplace", PS, "defaultMessage: `Only configured marketplaces can be removed`", "Only configured marketplaces expose removal.");
add("plugin-marketplace", PS, "defaultMessage: `Uninstall & remove`", "The marketplace removal dialog can uninstall installed plugins and remove the marketplace in one action.");
add("plugin-marketplace", PS, "id: `skills.appsPage.marketplace.removeDialog.confirm`", "Marketplace removal has a separate confirm action.");

// MCP settings.
add("mcp-settings", PS, "`mcpServer/oauth/login`", "MCP OAuth login calls the `mcpServer/oauth/login` RPC.");
add("mcp-settings", PS, "clientRegistration: t } : {})", "MCP OAuth login passes a non-automatic client-registration strategy only when one was selected.");
add("mcp-settings", PS, "supportsOauthClientRegistration: de,", "MCP server auth controls receive whether OAuth client registration is supported.");
add("mcp-settings", PS, "defaultMessage: `CIMD`", "Client ID metadata document is exposed as the `CIMD` registration strategy.");
add("mcp-settings", PS, "defaultMessage: `DCR`", "Dynamic client registration is exposed as the `DCR` registration strategy.");
add("mcp-settings", PS, "g === `cimd`", "The registration-strategy selector switches on `cimd`.");
add("mcp-settings", PS, "g === `dcr`", "The registration-strategy selector switches on `dcr`.");

// Remote connections.
add("remote-connections", RCS, "bt = G ? `access-other-devices` : `ssh`,", "The default remote-connections tab is `access-other-devices` when supported, otherwise `ssh`.");
add("remote-connections", RCS, "xt = l === `wsl` && Me?.length === 0,", "The WSL tab has an empty-workspace condition when the WSL list is empty.");
add("remote-connections", RCS, "id: `settings.remoteConnections.tabs.controlThisMac`", "Remote connections exposes a `controlThisMac` tab.");
add("remote-connections", RCS, "id: `ssh`", "The SSH tab is identified by the `ssh` id.");

// Profile.
add("profile", PST, "redirectToUsername: !1,", "Profile settings render the shared profile content without redirecting to a username route.");

// Plugin directory.
add("plugin-directory", "webview/assets/plugins-skills-tabs-c731ebf7d521.js", "id: `skills.appsPage.browseTabs.plugins`", "The plugin directory has a plugins browse tab.");
add("plugin-directory", "webview/assets/plugins-skills-tabs-c731ebf7d521.js", "id: `skills.appsPage.browseTabs.skillsWithNewSuggestions`", "The skills browse tab can advertise new suggestions.");

// Additional account, plugin-settings and MCP route facts.
add("account-menu", "webview/assets/profile-dropdown-items-8e551671a439.js", "id: `codex.profileDropdown.apiKeyAuth`", "The profile dropdown exposes an API-key authentication method.");
add("account-menu", "webview/assets/profile-dropdown-items-8e551671a439.js", "id: `codex.profileDropdown.amazonBedrockAuth`", "The profile dropdown exposes a Bedrock authentication method.");
add("account-menu", "webview/assets/profile-dropdown-items-8e551671a439.js", "id: `codex.profileDropdown.copilotAuth`", "The profile dropdown exposes a Copilot authentication method.");
add("plugin-settings", PS, "id: `skills.appsPage.manageTab.apps`", "The plugins manage surface has an `apps` tab.");
add("plugin-settings", PS, "id: `skills.appsPage.manageTab.mcps`", "The plugins manage surface has an `mcps` tab.");
add("plugin-settings", PS, "id: `skills.appsPage.manageTab.skills`", "The plugins manage surface has a `skills` tab.");
add("route-entry", SLR, '\"mcp-settings\": i(', "The settings registry also exposes an `mcp-settings` section.");
add("route-entry", SLR, "`./mcp-settings-347422a00db2.js`", "The MCP settings section lazy-loads its own chunk.");

// CSS evidence copied from the baseline stylesheets.
const cssClaims = [
  ["webview/assets/fd9b7fd80db1.css", ".text-codex-description", "Plugin and account secondary descriptions use the `text-codex-description` utility."],
  ["webview/assets/fd9b7fd80db1.css", ".text-secondary", "Plugin settings labels and account-menu secondary text use the `text-secondary` utility."],
  ["webview/assets/fd9b7fd80db1.css", ".text-tertiary", "Empty and tertiary states in the plugin directory use the `text-tertiary` utility."],
  ["webview/assets/fd9b7fd80db1.css", ".text-danger", "Account deletion, plugin load and connection errors use the `text-danger` utility."],
  ["webview/assets/fd9b7fd80db1.css", ".bg-surface", "Plugin cards, menus and settings panels use the `bg-surface` utility."],
  ["webview/assets/fd9b7fd80db1.css", ".border-default", "Plugin and connection rows use the `border-default` utility for their separators."],
  ["webview/assets/fd9b7fd80db1.css", ".rounded-2xl", "Plugin cards and account dialogs use the `rounded-2xl` corner utility."],
  ["webview/assets/fd9b7fd80db1.css", ".truncate", "Plugin names, account emails and connection labels truncate with the `truncate` utility."],
  ["webview/assets/9cc8b2b90a35.css", "--detail-page-section-gap:", "Plugin detail and profile pages space their sections with the `--detail-page-section-gap` token."],
  ["webview/assets/9cc8b2b90a35.css", "--detail-page-inline-inset:", "Plugin detail and profile pages use the `--detail-page-inline-inset` token for inline padding."],
  ["webview/assets/9cc8b2b90a35.css", "--profile-illustration-surface:", "The profile illustration surface resolves through the `--profile-illustration-surface` token."],
  ["webview/assets/9cc8b2b90a35.css", "--profile-illustration-label:", "Profile illustration labels resolve through the `--profile-illustration-label` token."],
];
for (const [file, anchor, claim] of cssClaims) add("css", file, anchor, claim);

// Message-id bindings discovered in the slices.
const sliceSpecs = [
  ["account-menu.items", "account-menu", "the account workspace submenu", 6],
  ["account-menu.menu", "account-menu", "the profile dropdown account menu", 8],
  ["account-settings.deletion", "account-settings", "the delete-account dialog", 8],
  ["account-settings.page", "account-settings", "the ChatGPT account settings page", 8],
  ["add-plugins-modal.team", "teams-plugins", "the team add-plugins modal", 6],
  ["app-runtime.logout-confirmation", "account-menu", "the log-out confirmation dialog", 6],
  ["billing-settings.page", "billing-settings", "the consumer billing settings page", 8],
  ["browser-logout.page", "account-settings", "the browser log-out page", 6],
  ["browser-plugin-settings.page", "plugin-settings", "the browser plugin settings page", 8],
  ["login-route.bedrock", "login", "the Bedrock login form", 6],
  ["login-route.page", "login", "the login route", 8],
  ["login-route.status", "login", "the login status and fallback view", 6],
  ["mcp-settings.page", "mcp-settings", "the MCP settings wrapper", 1],
  ["plugin-card-status-action.status", "plugin-directory", "the plugin card status action", 5],
  ["plugin-detail-actions.actions", "plugin-detail", "the plugin detail action bar", 8],
  ["plugin-detail-page.page", "plugin-detail", "the plugin detail page", 8],
  ["plugin-detail-page.settings-wrapper", "plugin-settings", "the plugin settings deep-link wrapper", 4],
  ["plugin-detail-view.view", "plugin-detail", "the plugin detail view", 8],
  ["plugin-install-modal.modal", "plugin-install", "the plugin install dialog", 8],
  ["plugin-settings-routes.routes", "plugin-settings", "the plugin settings route table", 1],
  ["plugins-cards-grid.cards", "plugin-directory", "the plugin cards grid", 8],
  ["plugins-settings.marketplace", "plugin-marketplace", "the marketplace settings section", 8],
  ["plugins-settings.mcp", "mcp-settings", "the MCP server settings", 8],
  ["plugins-settings.page", "plugin-settings", "the plugins settings page", 8],
  ["plugins-skills-tabs.directory", "plugin-directory", "the plugin directory", 8],
  ["profile-page.route", "profile", "the profile page", 4],
  ["remote-connections.clients", "remote-connections", "the remote-control clients section", 8],
  ["remote-connections.dialogs", "remote-connections", "the remote-connection dialogs", 8],
  ["remote-connections.page", "remote-connections", "the remote connections settings page", 8],
  ["remote-connections.table", "remote-connections", "the remote connections table", 8],
  ["select-workspace.page", "select-workspace", "the select-workspace page", 6],
  ["settings-plugin-controls.controls", "plugin-controls", "the plugin control row", 9],
  ["skills-page.plugins-page", "plugin-directory", "the skills/plugin directory page", 8],
];

const priority = [
  "empty", "error", "failed", "failure", "title", "description", "confirm",
  "delete", "remove", "install", "uninstall", "disconnect", "connect", "permission",
  "auth", "login", "logout", "sync", "upgrade", "skip", "retry", "loading",
  "warning", "success", "share", "account", "billing", "usage", "plan", "payment",
  "wallet", "workspace", "label", "button", "action",
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
  const tail = id.split(".").slice(-2).join(" ");
  return tail
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[_-]+/g, " ")
    .toLowerCase();
}
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
    const file = `webview/assets/${sliceSource(slice)}`;
    const anchor = `id: \`${id}\``;
    if (claimedAnchors.has(`${file}\0${anchor}`)) continue;
    claimedAnchors.add(`${file}\0${anchor}`);
    add(
      category,
      file,
      anchor,
      `The ${subject} binds \`${id}\` for its ${humanTail(id)} copy.`,
    );
  }
}

function sliceSource(slice) {
  const manifest = JSON.parse(
    fs.readFileSync(path.join(phaseDir, "reports", `${slice}-slice.json`), "utf8"),
  );
  return path.basename(manifest.source.path);
}

// Verify every anchor against the baseline before writing.
const failures = [];
for (const claim of claims) {
  const text = fs.readFileSync(path.join(baseline, claim.file), "utf8");
  const count = text.split(claim.anchor).length - 1;
  if (count === 0) failures.push(`${claim.id}: anchor not found: ${claim.anchor}`);
  else if (count > 1 && claim.occurrence == null) {
    claim.occurrence = 0;
  }
}
if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exit(1);
}

fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, `${JSON.stringify(claims, null, 2)}\n`);
const byCategory = {};
for (const claim of claims) byCategory[claim.category] = (byCategory[claim.category] ?? 0) + 1;
console.log(JSON.stringify({ outPath, claims: claims.length, byCategory }, null, 2));
