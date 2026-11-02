import { spawnSync } from 'node:child_process';
import { appendFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export function validateVersion(version) {
  if (!/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(version ?? '')) {
    throw new Error('version must be a stable major.minor.patch version in gradle.properties');
  }
}

function runCommand(command, args) {
  const result = spawnSync(command, args, { encoding: 'utf8', timeout: 120_000 });
  if (result.error) throw result.error;
  return result;
}

function checkResult(result) {
  if (result.status !== 0) {
    throw new Error(result.stderr || result.stdout || 'Command failed');
  }
  return result.stdout;
}

function api(path, run) {
  const result = run('gh', ['api', path]);
  // Authentication, rate limits and network errors must not be mistaken for a missing release.
  if (result.status !== 0 && result.stderr.includes('(HTTP 404)')) return null;
  return JSON.parse(checkResult(result));
}

export function createRelease({ repository, version, sha, assets }, run = runCommand) {
  validateVersion(version);
  if (!/^[\w.-]+\/[\w.-]+$/.test(repository ?? '') || !/^[a-f0-9]{40}$/.test(sha ?? '')) {
    throw new Error('A GitHub repository and full commit SHA are required');
  }
  const base = `repos/${repository}`;
  const existing = api(`${base}/releases/tags/${version}`, run);
  if (existing) {
    if (existing.draft || existing.prerelease) {
      throw new Error(`Release ${version} already exists as a draft or prerelease; resolve it manually`);
    }
    console.log(`Release ${version} already exists; keeping its tag and artifacts.`);
    return existing.html_url;
  }

  const tag = api(`${base}/git/ref/tags/${version}`, run);
  if (tag) {
    let target = tag.object;
    while (target.type === 'tag') {
      target = api(`${base}/git/tags/${target.sha}`, run).object;
    }
    if (target.type !== 'commit' || target.sha !== sha) {
      throw new Error(`Tag ${version} points to another commit; choose a new version instead of moving the tag`);
    }
  }
  if (assets.length < 2 || !assets.some(path => path.endsWith('-sources.jar')) ||
      !assets.some(path => path.endsWith('.jar') && !path.endsWith('-sources.jar'))) {
    throw new Error('The main JAR and sources JAR are required before creating a release');
  }
  const args = ['release', 'create', version, '--repo', repository, '--target', sha,
    '--title', version, '--generate-notes'];
  if (tag) args.push('--verify-tag');
  args.push(...assets);
  return checkResult(run('gh', args)).trim();
}

export async function buildOnJitPack(repository, version, {
  request = fetch,
  sleep = ms => new Promise(resolve => setTimeout(resolve, ms)),
  attempts = 8,
} = {}) {
  validateVersion(version);
  const [owner, name] = repository.split('/');
  const base = `https://jitpack.io/com/github/${owner}/${name}/${version}`;
  const pom = `${base}/${name}-${version}.pom`;
  let reason;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    console.log(`Checking JitPack (${attempt}/${attempts}): ${pom}`);
    try {
      const response = await request(pom, { signal: AbortSignal.timeout(90_000) });
      const body = await response.text();
      if (response.ok && /<(?:\w+:)?project[\s>]/.test(body)) return pom;
      reason = `HTTP ${response.status}: ${body.slice(0, 200)}`;
    } catch (error) {
      reason = error.message;
    }
    if (attempt < attempts) await sleep(15_000);
  }
  throw new Error(`JitPack did not produce a POM: ${reason}. Build log: ${base}/build.log. ` +
    'The GitHub release is preserved; rerun the workflow to retry.');
}

async function main() {
  const repository = process.env.GITHUB_REPOSITORY;
  const version = process.env.VERSION;
  const assets = readdirSync('build/libs').filter(name => name.endsWith('.jar'))
    .map(name => `build/libs/${name}`);
  const releaseUrl = createRelease({ repository, version, sha: process.env.GITHUB_SHA, assets });
  console.log(`GitHub release: ${releaseUrl}`);
  const pomUrl = await buildOnJitPack(repository, version);
  console.log(`JitPack ready: ${pomUrl}`);
  if (process.env.GITHUB_STEP_SUMMARY) {
    appendFileSync(process.env.GITHUB_STEP_SUMMARY,
      `GitHub Release: ${releaseUrl}\n\nJitPack: ${pomUrl}\n`);
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  main().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
