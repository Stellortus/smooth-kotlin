import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { buildOnJitPack, createRelease, validateVersion } from './release.mjs';

const projectRoot = fileURLToPath(new URL('../../', import.meta.url));

test('Gradle Wrapper is executable in Git', () => {
  const entry = execFileSync('git', ['ls-files', '--stage', '--', 'gradlew'], {
    cwd: projectRoot, encoding: 'utf8',
  });
  assert.match(entry, /^100755 [a-f0-9]+ 0\tgradlew\r?\n$/,
    'Run git update-index --chmod=+x gradlew and commit the mode change');
});

test('Gradle Wrapper uses LF line endings', () => {
  const wrapper = readFileSync(new URL('../../gradlew', import.meta.url), 'utf8');
  assert.ok(wrapper.startsWith('#!/bin/sh\n'), 'The shebang must end with LF');
  assert.ok(!wrapper.includes('\r'), 'gradlew must not contain CRLF line endings');
});

const version = [2, 3, 4].join('.');
const sha = 'a'.repeat(40);
const repository = 'example/new-library';
const options = { repository, version, sha, assets: ['lib.jar', 'lib-sources.jar'] };
const ok = value => ({ status: 0, stdout: JSON.stringify(value), stderr: '' });
const missing = { status: 1, stdout: '', stderr: 'gh: Not Found (HTTP 404)' };

// Match complete command sequences so a retry cannot silently create or replace another release.
function commands(responses) {
  const pending = [...responses];
  return {
    run(command, args) {
      assert.equal(command, 'gh');
      assert.ok(pending.length, 'Unexpected remote command');
      const [expected, result] = pending.shift();
      assert.deepEqual(args, expected);
      return result;
    },
    done() { assert.equal(pending.length, 0); },
  };
}
const releaseQuery = ['api', `repos/${repository}/releases/tags/${version}`];
const tagQuery = ['api', `repos/${repository}/git/ref/tags/${version}`];
const createArgs = ['release', 'create', version, '--repo', repository, '--target', sha,
  '--title', version, '--generate-notes'];

test('only stable numeric versions are accepted', () => {
  validateVersion(version);
  for (const invalid of [undefined, '', 'unspecified', 'v' + version, version + '-SNAPSHOT',
    '01.2.3', '1.2', version + '\n']) {
    assert.throws(() => validateVersion(invalid));
  }
});

test('first release targets the tested commit and includes both JARs', () => {
  const mock = commands([
    [releaseQuery, missing], [tagQuery, missing],
    [[...createArgs, ...options.assets], { status: 0, stdout: 'https://example/release\n' }],
  ]);
  assert.equal(createRelease(options, mock.run), 'https://example/release');
  mock.done();
});

test('an existing release is preserved even after further commits', () => {
  const mock = commands([[releaseQuery, ok({ html_url: 'https://example/existing' })]]);
  assert.equal(createRelease({ ...options, sha: 'b'.repeat(40) }, mock.run), 'https://example/existing');
  mock.done();
});

test('an annotated tag at the tested commit can recover a missing release', () => {
  const tagSha = 'c'.repeat(40);
  const mock = commands([
    [releaseQuery, missing], [tagQuery, ok({ object: { type: 'tag', sha: tagSha } })],
    [['api', `repos/${repository}/git/tags/${tagSha}`], ok({ object: { type: 'commit', sha } })],
    [[...createArgs, '--verify-tag', ...options.assets], { status: 0, stdout: 'recovered' }],
  ]);
  assert.equal(createRelease(options, mock.run), 'recovered');
  mock.done();
});

test('a conflicting tag cannot be moved', () => {
  const mock = commands([
    [releaseQuery, missing], [tagQuery, ok({ object: { type: 'commit', sha: 'b'.repeat(40) } })],
  ]);
  assert.throws(() => createRelease(options, mock.run), /another commit/);
  mock.done();
});

test('GitHub errors are not treated as absent releases', () => {
  const mock = commands([[releaseQuery, { status: 1, stderr: 'gh: Forbidden (HTTP 403)' }]]);
  assert.throws(() => createRelease(options, mock.run), /Forbidden/);
  mock.done();
});

test('missing artifacts prevent release creation', () => {
  const mock = commands([[releaseQuery, missing], [tagQuery, missing]]);
  assert.throws(() => createRelease({ ...options, assets: ['lib.jar'] }, mock.run), /JAR/);
  mock.done();
});

test('draft releases require manual resolution', () => {
  const mock = commands([[releaseQuery, ok({ draft: true })]]);
  assert.throws(() => createRelease(options, mock.run), /draft or prerelease/);
  mock.done();
});

test('JitPack retries HTTP failures and network timeouts until a real POM is available', async () => {
  let requests = 0;
  let sleeps = 0;
  const pom = await buildOnJitPack(repository, version, {
    request: async url => {
      assert.equal(url, `https://jitpack.io/com/github/${repository}/${version}/new-library-${version}.pom`);
      requests++;
      if (requests === 1) throw new Error('Timed out');
      if (requests === 2) return { ok: false, status: 503, text: async () => 'Building' };
      if (requests === 3) return { ok: true, status: 200, text: async () => '<html lang="en">Wait</html>' };
      return { ok: true, text: async () => '<?xml version="1.0"?><project xmlns="maven" />' };
    },
    sleep: async ms => { assert.equal(ms, 15_000); sleeps++; },
  });
  assert.ok(pom.endsWith('.pom'));
  assert.equal(requests, 4);
  assert.equal(sleeps, 3);
});

test('JitPack failure is bounded and reports a recovery path', async () => {
  let requests = 0;
  await assert.rejects(buildOnJitPack(repository, version, {
    request: async () => { requests++; return { ok: false, status: 404, text: async () => 'Not ready' }; },
    sleep: async () => {}, attempts: 2,
  }), /GitHub release is preserved; rerun/);
  assert.equal(requests, 2);
});
