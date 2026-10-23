// vscode:prepublish: builds the server and the extension before vsce packages them, unless the release script
// (scripts/package-platforms.js) built them already for all the packages.
const { execSync } = require('child_process');

if (!process.env.REQUALITY_PREBUILT) {
  execSync('npm run build', { stdio: 'inherit' });
}
