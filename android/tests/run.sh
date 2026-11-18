#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}"
json_jar="${TEST_JSON_JAR:-/workspace/toolchains/json-20240303.jar}"
if [[ ! -f "$json_jar" ]]; then
  echo 'Set TEST_JSON_JAR to org.json:json:20240303 (JVM tests only; not an app dependency).' >&2
  exit 1
fi
mkdir -p android/build/tests
"${java_bin}javac" -encoding UTF-8 -cp "$json_jar" -d android/build/tests \
  android/src/ai/magpie/android/{NavigationModel,GatewayProbe,UsageModels,CatalogSelection,ModelFavorites,CoreConfig,CoreProcess,CoreApi,ProviderDraft,ProviderOutcome,ClientConfig,UsageSnapshot,UsageHeatmap,WorkspaceSnapshot,CatalogData,CatalogLoad,GlassPolicy,Appearance,PluginSnapshot,PluginOperation,LedgerSnapshot,LedgerQuery,LedgerCsv}.java \
  android/tests/{NavigationModelTest,GatewayProbeTest,UsageModelsTest,CatalogSelectionTest,CoreConfigTest,CoreProcessTest,CoreApiTest,CoreApiPostTest,ProviderDraftTest,ProviderOutcomeTest,ClientConfigTest,UsageSnapshotTest,UsageHeatmapTest,WorkspaceSnapshotTest,CatalogDataTest,CatalogLoadTest,GlassPolicyTest,AppearanceTest,PluginSnapshotTest,LedgerSnapshotTest,LedgerCsvTest}.java
for test_name in NavigationModelTest GatewayProbeTest UsageModelsTest CatalogSelectionTest CoreConfigTest CoreProcessTest CoreApiTest CoreApiPostTest ProviderDraftTest ProviderOutcomeTest ClientConfigTest UsageSnapshotTest UsageHeatmapTest WorkspaceSnapshotTest CatalogDataTest CatalogLoadTest GlassPolicyTest AppearanceTest PluginSnapshotTest LedgerSnapshotTest LedgerCsvTest; do
  "${java_bin}java" -cp "android/build/tests:$json_jar" "ai.magpie.android.$test_name"
done
python3 android/tests/android_api_test.py android/build/tests
if [[ "${1:-}" == --unit-only ]]; then exit 0; fi
if [[ -z "${CORE_SOURCE_DIR:-}" ]]; then bash android/fetch-core.sh; fi
node --test android/tests/mobile.test.cjs android/tests/responsive.test.cjs
python3 android/tests/integration.py
"${java_bin}java" -cp "android/build/tests:$json_jar" ai.magpie.android.UsageSnapshotTest \
  android/build/evidence/usage-core-{today,7d,30d,all}.json
"${java_bin}java" -cp "android/build/tests:$json_jar" ai.magpie.android.UsageHeatmapTest android/build/evidence/usage-heatmap-core.json
