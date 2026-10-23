#!/usr/bin/env python3
"""Publish the already verified 0.6.2 artifact. Never build or re-sign an APK."""
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

REPO = 'fuhbe/Ytdlp-G-Android'
TAG = 'v0.6.2'
SOURCE = '4893e319b620436d8089ef43dde05c3ada52c9df'
RUN = 39878394553
APK_SHA = '735b4ac44a0aad52816df1f2ebcf3a637fb901ad690ee5b395ffe468a0349387'
SOURCE_SHA = '509a42d4719034e7d1836bbf7e4e1fb9653eada225ffd9b05c60424c4395fe48'
CERT = '3e1df7424b2cb4589de10dc26ae20afe68eded24acc82b6a0298de2fdb98798b'
ARTIFACTS = {
    'main': (12469622631, 'Ytdlp-G-Android-Minimal', '6d4ef4c7bb009a416cad5d8542fed5c610567542effb56abcae59b03d707612a'),
    'reports': (12469223108, 'Minimal-verification', '397216d0437f1a36cbc6b21ced850718ddde0ebed17a4712e3bd605262b07efc'),
}
ROOT = Path(__file__).resolve().parents[2]


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read_json(path):
    return json.loads(path.read_text())


def archive(path, destination):
    with zipfile.ZipFile(path) as z:
        require(z.testzip() is None, 'Artifact ZIP CRC failure')
        for name in z.namelist():
            p = Path(name)
            require(not p.is_absolute() and '..' not in p.parts, 'Unsafe archive path')
        z.extractall(destination)


def unique(root, pattern):
    files = list(root.rglob(pattern))
    require(len(files) == 1, 'Missing or ambiguous report: ' + pattern)
    return files[0]


def prepare(inputs, output):
    require(os.environ.get('GITHUB_REPOSITORY', REPO) == REPO, 'Unexpected repository')
    run = read_json(inputs / 'run.json')
    require(run['id'] == RUN and run['conclusion'] == 'success' and run['head_sha'] == SOURCE, 'Unverified build run')
    require(run['head_repository']['full_name'] == REPO and run['head_branch'] == 'feature/minimal-liquid-glass', 'Unexpected source branch/repository')
    for name, (artifact_id, artifact_name, sha) in ARTIFACTS.items():
        meta = read_json(inputs / (name + '.json'))
        require(meta['id'] == artifact_id and meta['name'] == artifact_name and not meta['expired'], 'Wrong or expired artifact')
        require(meta['workflow_run']['id'] == RUN and meta['workflow_run']['head_sha'] == SOURCE, 'Artifact provenance mismatch')
        require(meta['digest'] == 'sha256:' + sha and digest(inputs / (name + '.zip')) == sha, 'Outer artifact digest mismatch')
        archive(inputs / (name + '.zip'), inputs / name)
    main, reports = inputs / 'main', inputs / 'reports'
    for name, sha in [('Ytdlp-G.apk', APK_SHA), ('Ytdlp-G-source.zip', SOURCE_SHA)]:
        require(digest(main / name) == sha, 'Verified package checksum mismatch: ' + name)
    require((main / 'Ytdlp-G.apk').stat().st_size == 56230904, 'APK size mismatch')
    count = 0
    for p in reports.rglob('TEST-*.xml'):
        suite = ET.parse(p).getroot()
        require(int(suite.attrib['failures']) == 0 and int(suite.attrib['errors']) == 0, 'Failed automated test suite')
        require(int(suite.attrib.get('skipped', 0)) == 0, 'Unexpected skipped release test')
        count += int(suite.attrib['tests'])
    require(count == 57, 'Unexpected automated test count')
    dex = read_json(unique(reports, 'dex-bootstrap.json'))
    require(dex['pass'] and len(dex['checks']) == 13, 'APK DEX bootstrap regression')
    controls = read_json(unique(reports, 'zip-bootstrap-regression.json'))['checks']
    require(controls[0]['unshrunk'] == 'PASS' and controls[1]['without-rule'] == 'EXPECTED FAILURE reproduced' and controls[2]['with-rule'] == 'PASS', 'Archive regression control failure')
    with zipfile.ZipFile(main / 'Ytdlp-G-source.zip') as source:
        require(source.testzip() is None, 'Corresponding source ZIP CRC failure')
        gradle = source.read('minimal/app/build.gradle.kts').decode()
        require('versionName = "0.6.2"' in gradle and 'versionCode = 62' in gradle, 'Source version mismatch')
    with zipfile.ZipFile(main / 'Ytdlp-G.apk') as apk:
        require(apk.testzip() is None, 'APK ZIP CRC failure')
        for name in ['YTDLnis-GPL-3.0.txt', 'YTDLnis-NOTICE.txt', 'Python.txt', 'quickjs.txt', 'yt-dlp.txt', 'GPL-3.0.txt']:
            require(len(apk.read('assets/licenses/' + name)) > 0, 'Missing bundled license')
    output.mkdir(parents=True, exist_ok=True)
    for name in ['Ytdlp-G.apk', 'Ytdlp-G-source.zip', 'SHA256SUMS.txt', 'LICENSE.txt']:
        shutil.copy2(main / name, output / name)
    shutil.copy2(ROOT / 'docs/branding/icon.png', output / 'Ytdlp-G-icon.png')
    with zipfile.ZipFile(output / 'Ytdlp-G-screenshots.zip', 'w', zipfile.ZIP_DEFLATED) as z:
        pictures = sorted((ROOT / 'docs/screenshots').glob('*.png'))
        require(len(pictures) == 4, 'Missing screenshot renders')
        for path in pictures + [ROOT / 'docs/screenshots/README.md']:
            entry = zipfile.ZipInfo(path.name, date_time=(2026, 10, 23, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            z.writestr(entry, path.read_bytes())
    info = dict(version='0.6.2', tag=TAG, source_commit=SOURCE, source_run=RUN,
                apk_sha256=APK_SHA, apk_bytes=56230904, signing_certificate_sha256=CERT,
                automated_tests=57, screenshot_type='Actual Compose UI renders with sample metadata; not live download captures',
                user_report='User reported a successful video download/conversion retry on Redmi 10 Prime, Android 12',
                native_instrumentation='4 scenarios built, not executed by this workflow',
                artifact_digests={name: row[2] for name, row in ARTIFACTS.items()},
                build_performed=False, resign_performed=False)
    (output / 'release-info.json').write_text(json.dumps(info, indent=2) + '\n')
    presentation = os.environ.get('GITHUB_SHA', 'local-preview')
    notes = (ROOT / 'docs/releases/v0.6.2.md').read_text().replace('/Ytdlp-G-Android/main/', '/Ytdlp-G-Android/' + presentation + '/')
    require('{{PRESENTATION_SHA}}' not in notes, 'Unresolved release presentation URL')
    (output / 'release-notes.md').write_text(notes)
    print('PASS: exact existing APK/source, artifact provenance, 57 tests, 13 DEX checks and archive controls')


def check_apk(inputs, output):
    require(digest(output / 'Ytdlp-G.apk') == APK_SHA, 'APK changed after verification')
    certs = (output / 'apk-signature.txt').read_text()
    require('Signer #1 certificate SHA-256 digest: ' + CERT in certs, 'APK signing certificate mismatch')
    package = (output / 'apk-package.txt').read_text()
    for fragment in ["name='com.fuhbe.ytdlpg.minimal'", "versionCode='62'", "versionName='0.6.2'", "application-label:'Ytdlp-G'", "'arm64-v8a'"]:
        require(fragment in package, 'APK package metadata mismatch: ' + fragment)
    require("minSdkVersion:'26'" in package or "sdkVersion:'26'" in package, 'Android minimum mismatch')
    print('PASS: original signature, package, label, version, Android minimum and ARM64 ABI')


def api(method, path, data=None, allow404=False):
    require(os.environ.get('GITHUB_REPOSITORY') == REPO, 'Unexpected publishing repository')
    token = os.environ['GH_TOKEN']
    request = urllib.request.Request('https://api.github.com/repos/' + REPO + '/' + path,
        data=None if data is None else json.dumps(data).encode(), method=method,
        headers={'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json',
                 'X-GitHub-Api-Version': '2022-11-28', 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        if allow404 and error.code == 404:
            return None
        raise RuntimeError(f'GitHub {method} {path.split("?")[0]} returned HTTP {error.code}') from None


def publish(inputs, output):
    check_apk(inputs, output)
    presentation = os.environ['GITHUB_SHA']
    def verify_documentation_commit(sha):
        require(re.fullmatch('[0-9a-f]{40}', sha) is not None, 'Invalid release commit')
        comparison = api('GET', 'compare/' + SOURCE + '...' + sha)
        require(comparison['status'] in ('ahead', 'identical') and comparison['merge_base_commit']['sha'] == SOURCE, 'Release commit diverges from verified source')
        require(len(comparison['files']) < 300, 'Release comparison may be truncated')
        for item in comparison['files']:
            path = item['filename']
            require(path == 'README.md' or path.startswith('docs/') or path in (
                '.github/workflows/publish-minimal-release.yml',
                'minimal/DELIVERY_REPORT.md', 'minimal/regression/0.6.2-delivery.json'),
                'Runtime source changed since verified build: ' + path)
            require(item['status'] != 'renamed' or item.get('previous_filename', '').startswith('docs/'), 'Unexpected renamed source')
    verify_documentation_commit(presentation)
    ref = api('GET', 'git/ref/tags/' + TAG, allow404=True)
    if ref is None:
        ref = api('POST', 'git/refs', {'ref': 'refs/tags/' + TAG, 'sha': presentation})
    require(ref['object']['type'] == 'commit', 'Expected a lightweight release tag')
    verify_documentation_commit(ref['object']['sha'])
    tag_target = ref['object']['sha']
    release = api('GET', 'releases/tags/' + TAG, allow404=True)
    notes = (output / 'release-notes.md').read_text()
    if release is None:
        release = api('POST', 'releases', {'tag_name': TAG, 'target_commitish': tag_target,
            'name': 'Ytdlp-G 0.6.2 - Liquid Glass for Android', 'body': notes,
            'draft': True, 'prerelease': False})
    require(release['tag_name'] == TAG, 'Unexpected release')
    expected = ['Ytdlp-G.apk', 'Ytdlp-G-source.zip', 'SHA256SUMS.txt', 'LICENSE.txt',
                'Ytdlp-G-icon.png', 'Ytdlp-G-screenshots.zip', 'release-info.json']
    existing = {item['name']: item for item in release['assets']}
    for name in expected:
        file = output / name
        if name in existing:
            item = existing[name]
            if item.get('digest'):
                require(item['digest'] == 'sha256:' + digest(file), 'Existing release asset differs: ' + name)
            else:
                target = output / 'existing' / name
                target.parent.mkdir(exist_ok=True)
                with target.open('wb') as stream:
                    subprocess.run(['gh', 'api', f'repos/{REPO}/releases/assets/{item["id"]}', '-H', 'Accept: application/octet-stream'], stdout=stream, check=True)
                require(digest(target) == digest(file), 'Existing release asset differs: ' + name)
        else:
            subprocess.run(['gh', 'release', 'upload', TAG, str(file), '--repo', REPO], check=True)
    release = api('GET', 'releases/' + str(release['id']))
    assets = {a['name']: a for a in release['assets']}
    for name in expected:
        require(name in assets and assets[name]['state'] == 'uploaded', 'Release asset missing or unfinished')
        require(assets[name]['size'] == (output / name).stat().st_size, 'Release asset size mismatch')
        if assets[name].get('digest'):
            require(assets[name]['digest'] == 'sha256:' + digest(output / name), 'Uploaded asset checksum mismatch')
    release = api('PATCH', 'releases/' + str(release['id']), {
        'name': 'Ytdlp-G 0.6.2 - Liquid Glass for Android', 'body': notes,
        'draft': False, 'prerelease': False, 'make_latest': 'true'})
    latest = api('GET', 'releases/latest')
    require(latest['id'] == release['id'] and not latest['draft'] and not latest['prerelease'], 'Release not published as latest')
    result = dict(release_url=release['html_url'], tag=TAG, tag_commit=tag_target, source_commit=SOURCE,
                  apk_sha256=APK_SHA, assets=[{'name': a['name'], 'size': a['size'], 'digest': a.get('digest'), 'url': a['browser_download_url']} for a in latest['assets']],
                  build_performed=False, resign_performed=False)
    (output / 'publication.json').write_text(json.dumps(result, indent=2) + '\n')
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a') as stream:
            stream.write(f'## Published {TAG}\n\n{release["html_url"]}\n\nExact verified APK reused; no rebuild or re-signing.\n\nSHA256: `{APK_SHA}`\n')
    print('PASS: latest public release and all unchanged assets verified: ' + release['html_url'])


if __name__ == '__main__':
    command, inputs, output = sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3])
    {'prepare': prepare, 'check-apk': check_apk, 'publish': publish}[command](inputs, output)
