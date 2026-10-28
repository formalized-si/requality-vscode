"""Check existing release assets before passing any VSIX to a publishing tool. No rebuilds."""
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

TARGETS = ('universal', 'win32-x64', 'win32-arm64', 'linux-x64', 'linux-arm64', 'darwin-x64', 'darwin-arm64')
NS = {'v': 'http://schemas.microsoft.com/developer/vsx-schema/2011'}


def release_assets(metadata, tag):
    if not re.fullmatch(r'v\d+\.\d+\.\d+', tag):
        raise ValueError('Expected a release tag v<major>.<minor>.<patch>')
    version = tag[1:]
    prerelease = int(version.split('.')[1]) % 2 == 1
    if metadata['tagName'] != tag or metadata['isDraft'] or metadata['isPrerelease'] != prerelease:
        raise ValueError('Release tag, draft status or pre-release channel does not match the version')
    expected = {
        f'requality-vscode-{version}' + ('' if target == 'universal' else f'-{target}') + '.vsix': target
        for target in TARGETS
    }
    assets = [a for a in metadata['assets'] if a['name'].endswith('.vsix')]
    if len(assets) != len(expected) or {a['name'] for a in assets} != set(expected):
        raise ValueError('Expected exactly the seven Requality VSIX for this release')
    for asset in assets:
        if not re.fullmatch(r'sha256:[0-9a-f]{64}', asset.get('digest') or ''):
            raise ValueError(f"Missing SHA-256 digest: {asset['name']}")
    return version, prerelease, expected, assets


def verify_release(metadata, tag, directory=None):
    version, prerelease, expected, assets = release_assets(metadata, tag)
    if directory is None:
        return
    directory = Path(directory)
    if {p.name for p in directory.glob('*.vsix')} != set(expected):
        raise ValueError('Downloaded VSIX do not match the release asset list')
    for asset in assets:
        file = directory / asset['name']
        with file.open('rb') as stream:
            digest = 'sha256:' + hashlib.file_digest(stream, 'sha256').hexdigest()
        if digest != asset['digest']:
            raise ValueError(f'SHA-256 mismatch: {file.name}')
        with zipfile.ZipFile(file) as archive:
            package = json.loads(archive.read('extension/package.json'))
            manifest = ET.fromstring(archive.read('extension.vsixmanifest'))
        identity = manifest.find('v:Metadata/v:Identity', NS)
        if (package.get('publisher'), package.get('name'), package.get('version')) != ('formalized-si', 'requality-vscode', version):
            raise ValueError(f'Wrong package identity: {file.name}')
        if identity is None or (identity.get('Publisher'), identity.get('Id'), identity.get('Version')) != ('formalized-si', 'requality-vscode', version):
            raise ValueError(f'Wrong VSIX identity: {file.name}')
        if (identity.get('TargetPlatform') or 'universal') != expected[file.name]:
            raise ValueError(f'Wrong target platform: {file.name}')
        properties = {p.get('Id'): p.get('Value') for p in manifest.findall('v:Metadata/v:Properties/v:Property', NS)}
        if (properties.get('Microsoft.VisualStudio.Code.PreRelease', 'false').lower() == 'true') != prerelease:
            raise ValueError(f'Wrong VSIX pre-release channel: {file.name}')


if __name__ == '__main__':
    try:
        verify_release(json.loads(Path(sys.argv[1]).read_text()), sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
        print('Release VSIX validation passed.')
    except (ValueError, KeyError, OSError, ET.ParseError, zipfile.BadZipFile) as error:
        sys.exit(str(error))
