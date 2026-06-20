#!/usr/bin/env node

import {
  loadPresetContext,
  readPlanFromTarget,
  safeEvidenceMarkdown,
  taskUpgradeDir,
  writeMaterializationManifest,
  writeOutput,
} from "../scripts/upgrade-common.mjs";

try {
  const context = loadPresetContext();
  const plan = readPlanFromTarget(context);
  const appliedAt = new Date().toISOString();
  const evidence = {
    schemaVersion: "version-upgrade-safe-actions/v1",
    generatedAt: appliedAt,
    status: "ok",
    mode: "report-only",
    appliedActions: plan.safeActions.map((action) => ({
      ...action,
      status: "applied",
      appliedAt,
      result: "recorded",
    })),
    manualConfirmations: plan.manualConfirmations,
    blockedActions: plan.blockedActions,
  };
  const outputDir = "version-upgrade";
  const destinationDir = taskUpgradeDir(context);

  writeOutput(context.outputRoot, `${outputDir}/safe-actions-applied.json`, evidence);
  writeOutput(context.outputRoot, `${outputDir}/safe-actions-applied.md`, safeEvidenceMarkdown(evidence));
  writeMaterializationManifest(context, [
    { source: `${outputDir}/safe-actions-applied.json`, destination: `${destinationDir}/safe-actions-applied.json`, type: "json" },
    { source: `${outputDir}/safe-actions-applied.md`, destination: `${destinationDir}/safe-actions-applied.md`, type: "text" },
  ]);
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(2);
}
