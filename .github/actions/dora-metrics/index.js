const { appendFileSync, writeFileSync } = require('node:fs');

const input = (name, fallback) => process.env[`INPUT_${name.toUpperCase()}`] || fallback;

const repository = input('repository');
const token = input('token');
const environment = input('environment', 'production');
const workflow = input('workflow', 'ci.yaml');
const branch = input('branch', 'main');
const windowDays = Number(input('window-days', '90'));
const reportPath = input('report-path', 'dora-report.md');

const HOUR = 3600_000;
const DAY = 24 * HOUR;
const since = new Date(Date.now() - windowDays * DAY);

const bands = {
  'deployment-frequency': [
    ['Elite', (v) => v >= 7],
    ['High', (v) => v >= 1],
    ['Medium', (v) => v >= 1 / 4.345],
    ['Low', () => true],
  ],
  'lead-time-hours': [
    ['Elite', (v) => v < 24],
    ['High', (v) => v < 7 * 24],
    ['Medium', (v) => v < 30 * 24],
    ['Low', () => true],
  ],
  'change-failure-rate': [
    ['Elite', (v) => v <= 5],
    ['High', (v) => v <= 10],
    ['Medium', (v) => v <= 15],
    ['Low', () => true],
  ],
  'recovery-time-hours': [
    ['Elite', (v) => v < 1],
    ['High', (v) => v < 24],
    ['Medium', (v) => v < 7 * 24],
    ['Low', () => true],
  ],
};

const rank = ['Elite', 'High', 'Medium', 'Low'];

function classify(metric, value) {
  if (value === null) return '—';
  return bands[metric].find(([, test]) => test(value))[0];
}

async function api(path, { paginate = false } = {}) {
  const collected = [];
  let url = `https://api.github.com${path}`;

  for (;;) {
    const response = await fetch(url, {
      headers: {
        accept: 'application/vnd.github+json',
        authorization: `Bearer ${token}`,
        'x-github-api-version': '2022-11-28',
        'user-agent': 'trackly-dora-metrics',
      },
    });

    if (!response.ok) {
      throw new Error(`GET ${url} -> ${response.status} ${await response.text()}`);
    }

    const body = await response.json();
    if (!paginate) return body;

    const page = Array.isArray(body) ? body : body.workflow_runs || [];
    collected.push(...page);

    const next = (response.headers.get('link') || '')
      .split(',')
      .map((part) => part.match(/<([^>]+)>;\s*rel="next"/))
      .find(Boolean);

    if (!next || page.length === 0) return collected;
    url = next[1];
  }
}

function median(values) {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
}

const round = (value, digits = 2) => (value === null ? null : Number(value.toFixed(digits)));

async function releases() {
  const deployments = await api(
    `/repos/${repository}/deployments?environment=${environment}&per_page=100`,
    { paginate: true },
  );

  const inWindow = deployments.filter((d) => new Date(d.created_at) >= since);
  const commitDates = new Map();
  const results = [];

  for (const deployment of inWindow) {
    const statuses = await api(`/repos/${repository}/deployments/${deployment.id}/statuses?per_page=100`);

    // `inactive` is not an outcome: GitHub stamps it on a deployment that a later one superseded, so a
    // release that succeeded weeks ago carries it too. Only the three real terminal states are read.
    const outcome = statuses.find((s) => ['success', 'failure', 'error'].includes(s.state));

    if (!outcome) continue;

    if (!commitDates.has(deployment.sha)) {
      const commit = await api(`/repos/${repository}/commits/${deployment.sha}`);
      commitDates.set(deployment.sha, new Date(commit.commit.author.date));
    }

    results.push({
      sha: deployment.sha,
      succeeded: outcome.state === 'success',
      authored: commitDates.get(deployment.sha),
      released: new Date(outcome.created_at),
      requested: new Date(deployment.created_at),
      startedAt: statuses.find((s) => s.state === 'in_progress')?.created_at,
    });
  }

  return results;
}

async function mainlineOutages() {
  const runs = await api(
    `/repos/${repository}/actions/workflows/${workflow}/runs` +
      `?branch=${branch}&event=push&status=completed&per_page=100`,
    { paginate: true },
  );

  const ordered = runs
    .filter((run) => new Date(run.created_at) >= since)
    .filter((run) => ['success', 'failure'].includes(run.conclusion))
    .sort((a, b) => new Date(a.created_at) - new Date(b.created_at));

  const outages = [];
  let brokeAt = null;

  for (const run of ordered) {
    if (run.conclusion === 'failure' && brokeAt === null) {
      brokeAt = new Date(run.updated_at);
    } else if (run.conclusion === 'success' && brokeAt !== null) {
      outages.push({ from: brokeAt, to: new Date(run.updated_at) });
      brokeAt = null;
    }
  }

  return { outages, unresolved: brokeAt, total: ordered.length };
}

function report(metrics, detail) {
  const row = (name, value, unit, metric) => {
    const band = classify(metric, value);
    const shown = value === null ? 'n/a' : `${value} ${unit}`;
    return `| ${name} | ${shown} | ${band} |`;
  };

  const lines = [
    `## DORA metrics — ${repository}`,
    '',
    `Window: last ${windowDays} days (from ${since.toISOString().slice(0, 10)}). ` +
      `Releases counted: ${detail.releaseCount} to \`${environment}\`. ` +
      `Mainline runs examined: ${detail.runCount}.`,
    '',
    '| Metric | Measured | DORA band |',
    '|---|---|---|',
    row('Deployment frequency', metrics['deployment-frequency'], 'per week', 'deployment-frequency'),
    row('Lead time for changes', metrics['lead-time-hours'], 'h (median)', 'lead-time-hours'),
    row('Change failure rate', metrics['change-failure-rate'], '%', 'change-failure-rate'),
    row('Failed-deployment recovery time', metrics['recovery-time-hours'], 'h (median)', 'recovery-time-hours'),
    '',
    `**Overall band: ${metrics['performance-band']}** — the lowest of the four, because a delivery`,
    'capability is bounded by its weakest metric.',
    '',
  ];

  if (detail.pipelineMedian !== null) {
    lines.push(
      `Of the median ${metrics['lead-time-hours']} h lead time, ${detail.pipelineMedian} h is the ` +
        'pipeline itself (commit pushed to traffic shifted); the remainder is review and the ' +
        'manual approval gate on the production environment.',
      '',
    );
  }

  if (detail.approvalMedian !== null) {
    lines.push(`Median wait on the production approval gate: ${detail.approvalMedian} h.`, '');
  }

  if (detail.unresolved) {
    lines.push(
      `> The mainline is currently red — it broke at ${detail.unresolved.toISOString()} and has not ` +
        'gone green since. That outage is excluded from the recovery-time median.',
      '',
    );
  }

  lines.push(
    '<details><summary>Individual releases</summary>',
    '',
    '| Commit | Authored | Released | Lead time | Outcome |',
    '|---|---|---|---|---|',
    ...detail.releases.map(
      (r) =>
        `| \`${r.sha.slice(0, 7)}\` | ${r.authored.toISOString().slice(0, 16).replace('T', ' ')} | ` +
        `${r.released.toISOString().slice(0, 16).replace('T', ' ')} | ` +
        `${round((r.released - r.authored) / HOUR)} h | ${r.succeeded ? 'success' : 'failure'} |`,
    ),
    '',
    '</details>',
    '',
  );

  return lines.join('\n');
}

async function main() {
  const all = await releases();
  const successful = all.filter((r) => r.succeeded);
  const { outages, unresolved, total: runCount } = await mainlineOutages();

  const leadTimes = successful.map((r) => (r.released - r.authored) / HOUR);
  const pipelineTimes = successful
    .filter((r) => r.startedAt)
    .map((r) => (r.released - new Date(r.startedAt)) / HOUR);
  const approvalWaits = successful
    .filter((r) => r.startedAt)
    .map((r) => (new Date(r.startedAt) - r.requested) / HOUR);

  const metrics = {
    'deployment-frequency': round(successful.length / (windowDays / 7)),
    'lead-time-hours': round(median(leadTimes)),
    'change-failure-rate': all.length ? round(((all.length - successful.length) / all.length) * 100) : null,
    'recovery-time-hours': round(median(outages.map((o) => (o.to - o.from) / HOUR))),
  };

  metrics['performance-band'] =
    rank[
      Math.max(
        ...Object.keys(bands).map((metric) => {
          const band = classify(metric, metrics[metric]);
          return band === '—' ? 0 : rank.indexOf(band);
        }),
      )
    ];

  const markdown = report(metrics, {
    releases: successful,
    releaseCount: all.length,
    runCount,
    unresolved,
    pipelineMedian: round(median(pipelineTimes)),
    approvalMedian: round(median(approvalWaits)),
  });

  writeFileSync(reportPath, markdown);

  const outputs = { ...metrics, report: reportPath };
  if (process.env.GITHUB_OUTPUT) {
    appendFileSync(
      process.env.GITHUB_OUTPUT,
      Object.entries(outputs)
        .map(([key, value]) => `${key}=${value ?? ''}`)
        .join('\n') + '\n',
    );
  }
  if (process.env.GITHUB_STEP_SUMMARY) {
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, markdown);
  }

  process.stdout.write(markdown);
}

main().catch((error) => {
  process.stdout.write(`::error::${error.message}\n`);
  process.exitCode = 1;
});
