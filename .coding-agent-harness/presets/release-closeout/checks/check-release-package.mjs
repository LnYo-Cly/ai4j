#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const context = JSON.parse(fs.readFileSync(process.env.HARNESS_PRESET_CONTEXT, "utf8"));
const release = String(context.inputs.release || "").trim();
const governanceRoot = context.paths?.governanceRoot;
if (!governanceRoot) {
  console.error("release-closeout check requires structure-aware context.paths from the preset runner");
  process.exit(2);
}
const releaseRoot = path.join(context.targetRoot, governanceRoot, "releases", release);
const required = ["INDEX.md", "task-aggregate.json", "task-archive-plan.md", "public-summary.md", "public-redaction-report.json"];
const missing = required.filter((file) => !fs.existsSync(path.join(releaseRoot, file)));
if (missing.length) {
  console.error(`release package missing files: ${missing.join(", ")}`);
  process.exit(2);
}
const redaction = JSON.parse(fs.readFileSync(path.join(releaseRoot, "public-redaction-report.json"), "utf8"));
if (redaction.status !== "pass") {
  console.error("release public redaction report is not passing");
  process.exit(3);
}
fs.writeFileSync(context.materializationManifestPath, `${JSON.stringify({
  schemaVersion: "preset-materialization/v1",
  status: "pass",
  writes: [],
}, null, 2)}\n`);
