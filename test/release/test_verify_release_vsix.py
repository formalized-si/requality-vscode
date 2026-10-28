"""Publish safety checks, using tiny ZIP fixtures rather than building the extension."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('verify', Path(__file__).parents[2] / 'scripts/verify-release-vsix.py')
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)


class ReleaseValidation(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.metadata = {'tagName': 'v1.5.0', 'isDraft': False, 'isPrerelease': True, 'assets': []}
        for target in verify.TARGETS:
            name = 'requality-vscode-1.5.0' + ('' if target == 'universal' else '-' + target) + '.vsix'
            asset = {'name': name}
            self.metadata['assets'].append(asset)
            self.write_vsix(asset, target=target)

    def write_vsix(self, asset, target='universal', version='1.5.0', prerelease='true'):
        file = self.directory / asset['name']
        with zipfile.ZipFile(file, 'w') as archive:
            archive.writestr('extension/package.json', json.dumps({'publisher': 'formalized-si', 'name': 'requality-vscode', 'version': version}))
            archive.writestr('extension.vsixmanifest', f'''<PackageManifest xmlns="{verify.NS['v']}"><Metadata>
                <Identity Publisher="formalized-si" Id="requality-vscode" Version="{version}" TargetPlatform="{target}"/>
                <Properties><Property Id="Microsoft.VisualStudio.Code.PreRelease" Value="{prerelease}"/></Properties>
                </Metadata></PackageManifest>''')
        asset['digest'] = 'sha256:' + hashlib.sha256(file.read_bytes()).hexdigest()

    def check(self):
        verify.verify_release(self.metadata, 'v1.5.0', self.directory)

    def test_valid_release(self):
        self.check()

    def test_stable_release(self):
        self.metadata.update(tagName='v1.6.0', isPrerelease=False)
        for asset, target in zip(self.metadata['assets'], verify.TARGETS):
            (self.directory / asset['name']).unlink()
            asset['name'] = asset['name'].replace('1.5.0', '1.6.0')
            self.write_vsix(asset, target=target, version='1.6.0', prerelease='false')
        verify.verify_release(self.metadata, 'v1.6.0', self.directory)

    def test_wrong_tag_or_channel_or_draft(self):
        for key, value in [('tagName', 'v1.5.1'), ('isPrerelease', False), ('isDraft', True)]:
            with self.subTest(key=key):
                metadata = copy.deepcopy(self.metadata)
                metadata[key] = value
                with self.assertRaises(ValueError):
                    verify.verify_release(metadata, 'v1.5.0')

    def test_missing_or_duplicate_asset(self):
        for assets in [self.metadata['assets'][:-1], self.metadata['assets'] + self.metadata['assets'][:1]]:
            with self.assertRaisesRegex(ValueError, 'seven'):
                verify.verify_release({**self.metadata, 'assets': assets}, 'v1.5.0')

    def test_digest_is_required(self):
        self.metadata['assets'][0]['digest'] = None
        with self.assertRaisesRegex(ValueError, 'Missing SHA-256'):
            self.check()

    def test_corrupted_download(self):
        (self.directory / self.metadata['assets'][0]['name']).write_bytes(b'corrupt')
        with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
            self.check()

    def test_wrong_package_identity(self):
        self.write_vsix(self.metadata['assets'][0], version='1.4.0')
        with self.assertRaisesRegex(ValueError, 'package identity'):
            self.check()

    def test_wrong_target(self):
        self.write_vsix(self.metadata['assets'][0], target='win32-x64')
        with self.assertRaisesRegex(ValueError, 'target platform'):
            self.check()

    def test_wrong_package_channel(self):
        self.write_vsix(self.metadata['assets'][0], prerelease='false')
        with self.assertRaisesRegex(ValueError, 'pre-release channel'):
            self.check()


if __name__ == '__main__':
    unittest.main()
