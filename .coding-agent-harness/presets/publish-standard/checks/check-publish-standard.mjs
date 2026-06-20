#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const context = JSON.parse(fs.readFileSync(process.env.HARNESS_PRESET_CONTEXT, "utf8"));
const release = String(context.inputs.release || "").trim();
const governanceRoot = context.paths?.governanceRoot;
if (!governanceRoot) {
  console.error("publish-standard check requires structure-aware context.paths from the preset runner");
  process.exit(2);
}

const releaseRoot = path.join(context.targetRoot, governanceRoot, "releases", release);
const required = ["INDEX.md", "public-changelog.md", "technical-summary.md", "publish-checklist.md", "pack-report.json", "task-aggregate.json", "public-redaction-report.json"];
const missing = required.filter((file) => !fs.existsSync(path.join(releaseRoot, file)));
if (missing.length) {
  console.error(`publish standard package missing files: ${missing.join(", ")}`);
  process.exit(2);
}

const publicChangelog = fs.readFileSync(path.join(releaseRoot, "public-changelog.md"), "utf8");
for (const heading of ["功能新增", "功能优化", "问题修复", "稳定性提升", "文档与模板更新", "重要说明"]) {
  if (!publicChangelog.includes(`## ${heading}`)) {
    console.error(`publish standard public changelog missing section: ${heading}`);
    process.exit(3);
  }
}
if (/projection-first|facade|route through repository/i.test(publicChangelog)) {
  console.error("publish standard public changelog contains internal implementation jargon");
  process.exit(3);
}

const checklist = fs.readFileSync(path.join(releaseRoot, "publish-checklist.md"), "utf8");
for (const command of ["npm run check", "npm run prepublishOnly", "npm run pack:dry-run"]) {
  if (!checklist.includes(command)) {
    console.error(`publish standard checklist missing gate: ${command}`);
    process.exit(3);
  }
}
if (!checklist.includes("does not execute `npm publish`")) {
  console.error("publish standard checklist must preserve the owner publish boundary");
  process.exit(3);
}

const packReport = JSON.parse(fs.readFileSync(path.join(releaseRoot, "pack-report.json"), "utf8"));
if (packReport.status !== "pending-owner-run") {
  console.error("publish standard pack report must default to pending-owner-run");
  process.exit(3);
}
if (!Array.isArray(packReport.forbiddenPaths) || !packReport.forbiddenPaths.includes(".harness-private/")) {
  console.error("publish standard pack report must document forbidden private paths");
  process.exit(3);
}

const redaction = JSON.parse(fs.readFileSync(path.join(releaseRoot, "public-redaction-report.json"), "utf8"));
if (redaction.status !== "pass") {
  console.error("publish standard public redaction report is not passing");
  process.exit(3);
}

fs.writeFileSync(context.materializationManifestPath, `${JSON.stringify({
  schemaVersion: "preset-materialization/v1",
  status: "pass",
  writes: [],
}, null, 2)}\n`);
