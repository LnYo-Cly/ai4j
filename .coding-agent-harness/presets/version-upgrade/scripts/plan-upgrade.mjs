#!/usr/bin/env node

import {
  buildUpgradePlan,
  loadPresetContext,
  loadUpgradeManifest,
  taskUpgradeDir,
  upgradePlanMarkdown,
  writeMaterializationManifest,
  writeOutput,
} from "./upgrade-common.mjs";

try {
  const context = loadPresetContext();
  const manifest = loadUpgradeManifest(context);
  const plan = buildUpgradePlan(context, manifest);
  const outputDir = "version-upgrade";
  const destinationDir = taskUpgradeDir(context);

  writeOutput(context.outputRoot, `${outputDir}/upgrade-plan.json`, plan);
  writeOutput(context.outputRoot, `${outputDir}/upgrade-plan.md`, upgradePlanMarkdown(plan));
  writeOutput(context.outputRoot, `${outputDir}/upgrade-manifest.json`, manifest);
  writeMaterializationManifest(context, [
    { source: `${outputDir}/upgrade-plan.json`, destination: `${destinationDir}/upgrade-plan.json`, type: "json" },
    { source: `${outputDir}/upgrade-plan.md`, destination: `${destinationDir}/upgrade-plan.md`, type: "text" },
    { source: `${outputDir}/upgrade-manifest.json`, destination: `${destinationDir}/upgrade-manifest.json`, type: "json" },
  ]);
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(2);
}
