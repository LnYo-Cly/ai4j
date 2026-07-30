/* Detect orphan docs (in docs/ but not referenced in sidebars.ts). */
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const sidebarsPath = path.join(root, 'sidebars.ts');
const docsDir = path.join(root, 'docs');

const sidebarsContent = fs.readFileSync(sidebarsPath, 'utf8');
const idMatches = sidebarsContent.match(/'([a-z][a-z0-9-/]*)'/g) || [];
const ids = new Set(idMatches.map(function (s) { return s.slice(1, -1); }));
const sidebarIds = new Set();
ids.forEach(function (i) { if (i.indexOf('/') !== -1) sidebarIds.add(i); });
['intro', 'faq', 'glossary', 'contributing'].forEach(function (top) {
    if (ids.has(top)) sidebarIds.add(top);
});

const docsFiles = new Set();
function walk(dir) {
    fs.readdirSync(dir).forEach(function (fn) {
        const full = path.join(dir, fn);
        const st = fs.statSync(full);
        if (st.isDirectory()) {
            walk(full);
        } else if (fn.endsWith('.md') || fn.endsWith('.mdx')) {
            let rel = path.relative(docsDir, full).split(path.sep).join('/');
            if (rel.endsWith('.mdx')) rel = rel.slice(0, -4);
            else rel = rel.slice(0, -3);
            docsFiles.add(rel);
        }
    });
}
walk(docsDir);

const orphans = Array.from(docsFiles).filter(function (x) { return !sidebarIds.has(x); }).sort();
const missing = Array.from(sidebarIds).filter(function (x) { return !docsFiles.has(x); }).sort();

console.log('TOTAL docs files:', docsFiles.size);
console.log('TOTAL sidebar ids:', sidebarIds.size);
console.log('ORPHANS (in docs/, not in sidebars):', orphans.length);
orphans.forEach(function (o) { console.log('  ' + o); });
console.log('MISSING (in sidebars, not in docs/):', missing.length);
missing.forEach(function (m) { console.log('  ' + m); });

if (orphans.length || missing.length) {
    process.exitCode = 1;
}
