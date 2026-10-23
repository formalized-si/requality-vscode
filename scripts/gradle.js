// Runs the Gradle wrapper of the server with the arguments (gradlew.bat on Windows, where npm runs scripts with cmd).
const { spawnSync } = require('child_process');
const path = require('path');

const windows = process.platform === 'win32';
const result = spawnSync(windows ? 'gradlew.bat' : './gradlew', process.argv.slice(2), {
  cwd: path.join(__dirname, '..', 'server'),
  stdio: 'inherit',
  shell: windows,
});
if (result.error) {
  console.error(result.error.message);
}
process.exit(result.status ?? 1);
