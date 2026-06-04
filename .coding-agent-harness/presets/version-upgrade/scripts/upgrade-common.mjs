import fs from "node:fs";
import path from "node:path";

export function loadPresetContext() {
  const contextPath = process.env.HARNESS_PRESET_CONTEXT;
  if (!contextPath) throw new Error("HARNESS_PRESET_CONTEXT is required");
  return JSON.parse(fs.readFileSync(contextPath, "utf8"));
}

export function releaseEdge(context) {
  const fromVersion = safeVersion(context.creationInputs?.fromVersion || context.inputs?.fromVersion);
  const toVersion = safeVersion(context.creationInputs?.toVersion || context.inputs?.toVersion);
  if (!fromVersion || !toVersion) throw new Error("version-upgrade requires --from-version and --to-version");
  return { fromVersion, toVersion, edgeId: `${fromVersion}-to-${toVersion}` };
}

export function loadUpgradeManifest(context) {
  const edge = releaseEdge(context);
  const manifestPath = path.resolve(new URL("..", import.meta.url).pathname, "releases", `${edge.edgeId}.yaml`);
  if (!fs.existsSync(manifestPath)) {
    throw new Error(`No version-upgrade manifest for ${edge.fromVersion} -> ${edge.toVersion}`);
  }
  const manifest = parseManifestYaml(fs.readFileSync(manifestPath, "utf8"));
  if (manifest.schemaVersion !== "version-upgrade-manifest/v1") {
    throw new Error(`Unsupported version-upgrade manifest schema: ${manifest.schemaVersion || "(missing)"}`);
  }
  if (manifest.fromVersion !== edge.fromVersion || manifest.toVersion !== edge.toVersion) {
    throw new Error(`Manifest edge mismatch: expected ${edge.fromVersion} -> ${edge.toVersion}`);
  }
  return {
    ...manifest,
    manifestFile: `presets/version-upgrade/releases/${edge.edgeId}.yaml`,
  };
}

export function buildUpgradePlan(context, manifest) {
  return {
    schemaVersion: "version-upgrade-plan/v1",
    generatedAt: new Date().toISOString(),
    preset: {
      id: context.preset?.id || "version-upgrade",
      version: context.preset?.version || "",
      source: context.preset?.source || "",
    },
    task: {
      id: context.task?.id || "",
      dir: context.task?.dir || "",
    },
    summary: {
      fromVersion: manifest.fromVersion,
      toVersion: manifest.toVersion,
      manifestFile: manifest.manifestFile,
      description: manifest.summary || "",
      safeActions: manifest.safeActions.length,
      manualConfirmations: manifest.manualConfirmations.length,
      blockedActions: manifest.blockedActions.length,
    },
    safeActions: manifest.safeActions.map((action) => ({ ...action, classification: "safe", status: "pending" })),
    manualConfirmations: manifest.manualConfirmations.map((confirmation) => ({ ...confirmation, classification: "manual", status: "unresolved" })),
    blockedActions: manifest.blockedActions.map((action) => ({ ...action, classification: "blocked", status: "blocked" })),
    verify: manifest.verify.map((check) => ({ ...check, status: "pending" })),
  };
}

export function taskUpgradeDir(context) {
  const taskDir = normalizeTargetRelative(context.task?.dir || "", "task dir");
  return `${taskDir}/artifacts/version-upgrade`;
}

export function taskUpgradeAbsDir(context) {
  return path.join(context.targetRoot, taskUpgradeDir(context));
}

export function readPlanFromTarget(context) {
  const planPath = path.join(taskUpgradeAbsDir(context), "upgrade-plan.json");
  if (!fs.existsSync(planPath)) throw new Error("version-upgrade plan evidence is missing; run plan first");
  return JSON.parse(fs.readFileSync(planPath, "utf8"));
}

export function readSafeEvidenceFromTarget(context) {
  const evidencePath = path.join(taskUpgradeAbsDir(context), "safe-actions-applied.json");
  if (!fs.existsSync(evidencePath)) return null;
  return JSON.parse(fs.readFileSync(evidencePath, "utf8"));
}

export function writeOutput(outputRoot, relativePath, content) {
  const destination = path.join(outputRoot, relativePath);
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.writeFileSync(destination, typeof content === "string" ? ensureTrailingNewline(content) : `${JSON.stringify(content, null, 2)}\n`);
}

export function writeMaterializationManifest(context, writes, status = "ok") {
  fs.writeFileSync(context.materializationManifestPath, `${JSON.stringify({
    schemaVersion: "preset-materialization/v1",
    status,
    writes,
  }, null, 2)}\n`);
}

export function upgradePlanMarkdown(plan) {
  return `# Version Upgrade Plan

Upgrade edge: ${plan.summary.fromVersion} -> ${plan.summary.toVersion}

Manifest: \`${plan.summary.manifestFile}\`

## Safe Actions

${plan.safeActions.length ? plan.safeActions.map((action) => `- ${action.id}: ${action.title}`).join("\n") : "- none"}

## Manual Confirmations

${plan.manualConfirmations.length ? plan.manualConfirmations.map((item) => `- ${item.id}: ${item.title}`).join("\n") : "- none"}

## Blocked Actions

${plan.blockedActions.length ? plan.blockedActions.map((action) => `- ${action.id}: ${action.reason || action.title}`).join("\n") : "- none"}
`;
}

export function safeEvidenceMarkdown(evidence) {
  return `# Safe Actions Applied

Applied safe actions: ${evidence.appliedActions.length}

${evidence.appliedActions.length ? evidence.appliedActions.map((action) => `- ${action.id}: ${action.title}`).join("\n") : "- none"}

Manual confirmations are preserved for human review: ${evidence.manualConfirmations.length}

Blocked actions are preserved as non-automation boundaries: ${evidence.blockedActions.length}
`;
}

export function verifyMarkdown(verification) {
  return `# Version Upgrade Verification

Status: ${verification.status}

## Unresolved Manual Confirmations

${verification.unresolvedManualConfirmations.length ? verification.unresolvedManualConfirmations.map((item) => `- ${item.id}: ${item.title}`).join("\n") : "- none"}

## Blocked Actions

${verification.blockedActions.length ? verification.blockedActions.map((item) => `- ${item.id}: ${item.reason || item.title}`).join("\n") : "- none"}
`;
}

function parseManifestYaml(source) {
  const root = {
    safeActions: [],
    manualConfirmations: [],
    blockedActions: [],
    verify: [],
  };
  let currentList = "";
  let currentItem = null;
  for (const rawLine of String(source).split(/\r?\n/)) {
    if (!rawLine.trim() || rawLine.trimStart().startsWith("#")) continue;
    if (/^[A-Za-z0-9_-]+:\s*$/.test(rawLine.trim())) {
      currentList = rawLine.trim().slice(0, -1);
      if (!Array.isArray(root[currentList])) root[currentList] = [];
      currentItem = null;
      continue;
    }
    const itemMatch = rawLine.match(/^\s{2}-\s+([A-Za-z0-9_-]+):\s*(.*)$/);
    if (itemMatch && currentList) {
      currentItem = { [itemMatch[1]]: parseScalar(itemMatch[2]) };
      root[currentList].push(currentItem);
      continue;
    }
    const itemFieldMatch = rawLine.match(/^\s{4}([A-Za-z0-9_-]+):\s*(.*)$/);
    if (itemFieldMatch && currentItem) {
      currentItem[itemFieldMatch[1]] = parseScalar(itemFieldMatch[2]);
      continue;
    }
    const rootMatch = rawLine.match(/^([A-Za-z0-9_-]+):\s*(.*)$/);
    if (rootMatch) {
      currentList = "";
      currentItem = null;
      root[rootMatch[1]] = parseScalar(rootMatch[2]);
      continue;
    }
    throw new Error(`Unsupported version-upgrade manifest line: ${rawLine}`);
  }
  return root;
}

function parseScalar(rawValue) {
  const value = String(rawValue || "").trim();
  if (value.startsWith("[") && value.endsWith("]")) {
    const body = value.slice(1, -1).trim();
    return body ? body.split(",").map((item) => unquote(item.trim())) : [];
  }
  return unquote(value);
}

function unquote(value) {
  return String(value || "").replace(/^['"]|['"]$/g, "");
}

function safeVersion(value) {
  const version = String(value || "").trim();
  if (!/^[0-9]+(?:\.[0-9]+){0,3}(?:-[A-Za-z0-9.-]+)?$/.test(version)) return "";
  return version;
}

function normalizeTargetRelative(value, label) {
  const raw = String(value || "").trim().replace(/^\/+/, "");
  const normalized = raw.split(path.sep).join("/");
  if (!normalized || normalized === "." || normalized === ".." || normalized.startsWith("../") || normalized.includes("/../") || path.isAbsolute(raw)) {
    throw new Error(`${label} escapes target root: ${value}`);
  }
  return normalized;
}

function ensureTrailingNewline(content) {
  return content.endsWith("\n") ? content : `${content}\n`;
}
