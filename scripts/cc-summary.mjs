import fs from 'node:fs';

const out = [];

function readJson(path) {
  try {
    return JSON.parse(fs.readFileSync(path, 'utf8'));
  } catch (error) {
    return null;
  }
}

const sbom = readJson('reports/sbom-cyclonedx.json');
const licenses = readJson('reports/deps-licenses-summary.json') || [];
const jscpd = readJson('reports/jscpd/jscpd-report.json');

out.push('# Code Compliance Summary\n');

if (sbom) {
  const count = Array.isArray(sbom.components) ? sbom.components.length : 0;
  out.push(`- SBOM components: **${count}**`);
} else {
  out.push('- SBOM components: data unavailable');
}

if (Array.isArray(licenses) && licenses.length > 0) {
  out.push('- Top licenses:');
  licenses.slice(0, 10).forEach((entry) => {
    const name = entry.license || 'unknown';
    out.push(`  - ${name}: ${entry.count}`);
  });
} else {
  out.push('- Licenses summary: none');
}

if (jscpd?.statistics?.total) {
  const total = jscpd.statistics.total;
  const duplicates = typeof total.duplicatedLines === 'number' ? total.duplicatedLines : 'unknown';
  const percentage = total.percentage ?? 'unknown';
  const clones = Array.isArray(jscpd.clones) ? jscpd.clones.length : 0;
  out.push(`- jscpd: duplicated lines **${duplicates}** (${percentage}%), clones: **${clones}**`);
} else {
  out.push('- jscpd: no report');
}

fs.writeFileSync('reports/code-compliance-summary.md', `${out.join('\n')}\n`);
console.log('[summary] reports/code-compliance-summary.md written');
