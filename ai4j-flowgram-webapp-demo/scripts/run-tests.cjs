const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const projectRoot = path.resolve(__dirname, '..');
const srcRoot = path.join(projectRoot, 'src');

const compilerOptions = {
  module: ts.ModuleKind.CommonJS,
  target: ts.ScriptTarget.ES2020,
  jsx: ts.JsxEmit.ReactJSX,
  esModuleInterop: true,
  moduleResolution: ts.ModuleResolutionKind.NodeJs,
  resolveJsonModule: true,
  skipLibCheck: true,
};

const previousTsHandler = require.extensions['.ts'];
const previousTsxHandler = require.extensions['.tsx'];

const compileTypeScript = (module, filename) => {
  const source = fs.readFileSync(filename, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions,
    fileName: filename,
    reportDiagnostics: true,
  });

  const diagnostics = output.diagnostics || [];
  if (diagnostics.length > 0) {
    const formatted = ts.formatDiagnosticsWithColorAndContext(diagnostics, {
      getCanonicalFileName: (fileName) => fileName,
      getCurrentDirectory: () => projectRoot,
      getNewLine: () => '\n',
    });
    throw new Error(formatted);
  }

  module._compile(output.outputText, filename);
};

require.extensions['.ts'] = compileTypeScript;
require.extensions['.tsx'] = compileTypeScript;

const walk = (directory) => {
  if (!fs.existsSync(directory)) {
    return [];
  }

  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = path.join(directory, entry.name);
    return entry.isDirectory() ? walk(entryPath) : [entryPath];
  });
};

const testFiles = walk(srcRoot)
  .filter((filePath) => /\.test\.tsx?$/.test(filePath))
  .sort();

if (testFiles.length === 0) {
  console.error('No test files found under src/**/*.test.ts(x).');
  process.exitCode = 1;
} else {
  let failed = 0;

  testFiles.forEach((filePath) => {
    const relativePath = path.relative(projectRoot, filePath).replace(/\\/g, '/');
    try {
      require(filePath);
      console.log(`PASS ${relativePath}`);
    } catch (error) {
      failed += 1;
      console.error(`FAIL ${relativePath}`);
      console.error(error && error.stack ? error.stack : error);
    }
  });

  if (failed > 0) {
    process.exitCode = 1;
  } else {
    console.log(`Executed ${testFiles.length} test file(s).`);
  }
}

require.extensions['.ts'] = previousTsHandler;
require.extensions['.tsx'] = previousTsxHandler;
