#!/usr/bin/env node

import {
  loadPresetContext,
  readPlanFromTarget,
  readSafeEvidenceFromTarget,
  taskUpgradeDir,
  verifyMarkdown,
  writeMaterializationManifest,
  writeOutput,
} from "../scripts/upgrade-common.mjs";

try {
  const context = loadPresetContext();
  const plan = readPlanFromTarget(context);
  const safeEvidence = readSafeEvidenceFromTarget(context);
  const appliedIds = new Set((safeEvidence?.appliedActions || []).map((action) => action.id));
  const missingSafeActions = plan.safeActions.filter((action) => !appliedIds.has(action.id));
  const unresolvedManualConfirmations = plan.manualConfirmations.filter((item) => item.status !== "confirmed");
  const blockedActions = plan.blockedActions;
  const status = missingSafeActions.length || unresolvedManualConfirmations.length || blockedActions.length ? "blocked" : "pass";
  const verification = {
    schemaVersion: "version-upgrade-verify/v1",
    generatedAt: new Date().toISOString(),
    status,
    summary: {
      safeActions: plan.safeActions.length,
      appliedSafeActions: appliedIds.size,
      unresolvedManualConfirmations: unresolvedManualConfirmations.length,
      blockedActions: blockedActions.length,
    },
    missingSafeActions,
    unresolvedManualConfirmations,
    blockedActions,
    verify: plan.verify,
  };
  const outputDir = "version-upgrade";
  const destinationDir = taskUpgradeDir(context);

  writeOutput(context.outputRoot, `${outputDir}/upgrade-verify.json`, verification);
  writeOutput(context.outputRoot, `${outputDir}/upgrade-verify.md`, verifyMarkdown(verification));
  writeMaterializationManifest(context, [
    { source: `${outputDir}/upgrade-verify.json`, destination: `${destinationDir}/upgrade-verify.json`, type: "json" },
    { source: `${outputDir}/upgrade-verify.md`, destination: `${destinationDir}/upgrade-verify.md`, type: "text" },
  ], status);
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(2);
}
