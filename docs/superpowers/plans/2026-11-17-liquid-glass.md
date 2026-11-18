# Approved Magpie Android 2.0 implementation

User approved the complete plan and requested implementation. Style choices: light translucent liquid glass (not all-content blur), complete clickable Android prototype, health and consumption balanced, full refraction33+ and graceful28–32 fallback. No new approval gate.

## Architecture and acceptance

Native Java17 Views/manual SDK pipeline; preserve min28/target35, package/signature/files/core/foreground service and bundled pinned Go/Bun. Four retained roots plus explicit native detail stack and canonical advanced destinations. Reuse working low-level core/auth/security/data layers; fully rewrite UI composition and navigation.

Screens: direct protocol/model/group connection, real test/copy/JSON, models/groups/providers search and full details, real source/group edit, plugins installed/discovery/install/progress/config/update/disable/custompages/library/remote Agent/session, stats overview/trend/breakdown/monthheatmap/requests/detail/export, global workspace/gateway/LAN/keys/privacy/backup/diagnostics/license.

Glass: shared isolated native backdrop, AGSL RuntimeShader33+ rounded refraction and highlights; blur31–32, readable tint28–30. Labels above effects, no CPU screenshot every frame/recursivecapture. System/app reduce motion/transparency, no idle animation. Solid body/chart surfaces, neutral silver/white/graphite, no purple, no nestedcardgrids; body15–16sp, gutters20dp,48dp targets.

Statistics source GET/api/usage/requests totals/series/by/speeds (not visible-row sums); GET/api/usage/heatmap, CSV streamed via SAF. All-token=input+output+cacheRead+cacheWrite, knownUSD and unpriced distinct. Finalerrors vs failedattempts distinct; failed=1 includes successful retry; TTFT is not total latency. No invented P95. Content fetched only on explicit reveal, not persisted.

All responses scoped to workspace/origin/keygeneration/timezone/filters, lifecycle guarded; preserve same-scope stale data visibly, never old workspace overwrites. Draft secrets not persisted. Preserve native/WebView sameorigin/auth/dirty/export/chooser/SSL/renderer-crash defenses. Remote-only entries explain requirements rather than force workspace switching.

## Tasks and ownership

1. Parent: contracts, Ui/ScreenHost/MainActivity/ConnectScreen/navigation/CoreApi; route/API tests; integrates modules and all builds.
2. Renderer: GlassFrame(Activity).setScene(View)/setDock(View)/setGlassEnabled(boolean)/refreshOptions(); GlassPanel/Shader33/Blur31/GlassPolicy/GlassOptions. Options use existing host prefs reduceTransparency/reduceMotion/glassEnabled. No Ui changes.
3. Catalog: CatalogScreen(ScreenHost), ModelDetailScreen/GroupDetailScreen and own helpers/tests. Models/Groups/Providers, favorites/queries, real GET/providers/groups, ProviderSetupActivity request17 and advanced edit routes.
4. Plugins: PluginsScreen(ScreenHost), PluginSnapshot/PluginDetailScreen and own helpers/tests. Required POST /api/plugins/add,/upgrade,/off,/builtin; parent extends CoreApi whitelist. Uncertain mutations never replay.
5. Insights: InsightsScreen(ScreenHost), LedgerSnapshot/LedgerScreen/RequestDetailScreen/InsightChart/MonthHeatmap and export/helper/tests. Overview/Analysis/Calendar/Requests, real drilldown/CSV/ondemandcontent.
6. Advanced: ManagementActivity/ToolsActivity/assets mobile.css/mobile.js and focusedbrowser tests only; unified tokens/header/parent, full advanced reachability, preserve late onPageFinished nativeerror safeguard. Add reduceTransparency/reduceMotion settings in host prefs.
7. Parent: installed normal/largefont and all flows capture, real local-core/Bun/provider/plugin/protocol/stat/export tests, API guards, independent review, final fixes, same-signature upgrade, ARM64/x86 preview2.0.0-preview1/code14 publication to existing GitHub.

## Verification and progress

Meaningful behavior tests before implementation; no screenshot-only claim of real functionality. Existing14JVM+API28encoder baseline passed Nov17 before restart. Run anew on durable baseline. Native installed layout/API tests do not certify physical frame time; disclose physical-device verification gaps.

Progress Nov18: temporary source lost on environment restart; authoritative published baseline restored to durable feature clone. This file and all new code stay on persistent storage and are committed/backed up incrementally.
