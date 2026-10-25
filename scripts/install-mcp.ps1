# Installs the Requality MCP server (requality-mcp) from the GitHub releases of formalized-si/requality-vscode:
#   irm https://raw.githubusercontent.com/formalized-si/requality-vscode/main/scripts/install-mcp.ps1 | iex
#
# The archive of the platform contains a Java runtime. The newest release that has one is installed; a pre-release
# only when no release has one, or with REQUALITY_MCP_PRERELEASE=1. Environment:
#   REQUALITY_MCP_VERSION     a release, for example 1.5.0
#   REQUALITY_MCP_PRERELEASE  1: the newest release with an archive, also a pre-release
#   REQUALITY_MCP_HOME        the installation folder (default: %LOCALAPPDATA%\Programs\requality-mcp)
#   REQUALITY_MCP_ARCHIVE     a downloaded archive (.zip) to install instead of a download
# The server keeps its caches and change histories in %USERPROFILE%\.requality-mcp, apart from the installation: an
# uninstall (removing the installation folder) keeps them.
$ErrorActionPreference = 'Stop'
# the progress bar of Invoke-WebRequest makes a large download very slow in Windows PowerShell 5.1
$ProgressPreference = 'SilentlyContinue'
$repo = 'formalized-si/requality-vscode'
$rqHome = if ($env:REQUALITY_MCP_HOME) { [System.IO.Path]::GetFullPath($env:REQUALITY_MCP_HOME) } else { Join-Path $env:LOCALAPPDATA 'Programs\requality-mcp' }
$arch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64' -or $env:PROCESSOR_ARCHITEW6432 -eq 'ARM64') { 'arm64' } else { 'x64' }
$target = "win32-$arch"
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("requality-mcp-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $tmp | Out-Null

function Find-Asset($releases) {
  $releases | ForEach-Object { $_.assets } | Where-Object { $_.name -like "requality-mcp-*-$target.zip" } | Select-Object -First 1
}

try {
  if ($env:REQUALITY_MCP_ARCHIVE) {
    $archive = [System.IO.Path]::GetFullPath($env:REQUALITY_MCP_ARCHIVE)
    if (-not (Test-Path $archive)) { throw "No file $archive" }
  } else {
    $api = "https://api.github.com/repos/$repo/releases"
    $headers = @{ 'User-Agent' = 'requality-mcp-installer' }
    $asset = $null
    if ($env:REQUALITY_MCP_VERSION) {
      $asset = Find-Asset @(Invoke-RestMethod -Uri "$api/tags/v$($env:REQUALITY_MCP_VERSION.TrimStart('v'))" -Headers $headers)
    } else {
      if ($env:REQUALITY_MCP_PRERELEASE -ne '1') {
        # the newest release that is not a pre-release
        try { $asset = Find-Asset @(Invoke-RestMethod -Uri "$api/latest" -Headers $headers) } catch { $asset = $null }
      }
      if (-not $asset) {
        $asset = Find-Asset @(Invoke-RestMethod -Uri "$api`?per_page=30" -Headers $headers)
        if ($asset -and $env:REQUALITY_MCP_PRERELEASE -ne '1') {
          Write-Host "The newest release has no requality-mcp archive for ${target}: installing the newest release that has one (it can be a pre-release)."
        }
      }
    }
    if (-not $asset) { throw "No requality-mcp archive for $target in the releases of $repo." }
    $archive = Join-Path $tmp $asset.name
    Write-Host "Downloading $($asset.browser_download_url)"
    Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $archive -UseBasicParsing
    # the SHA-256 that GitHub gives for the asset, when there is one
    if ($asset.digest -and $asset.digest.StartsWith('sha256:')) {
      $actual = (Get-FileHash -Algorithm SHA256 $archive).Hash.ToLowerInvariant()
      if ($actual -ne $asset.digest.Substring(7)) { throw "The SHA-256 of the archive is $actual, the release gives $($asset.digest)." }
      Write-Host 'SHA-256 is correct.'
    } else {
      Write-Warning 'The release gives no SHA-256 for the archive; it is not checked.'
    }
  }
  $x = Join-Path $tmp 'x'
  Expand-Archive -Path $archive -DestinationPath $x
  $dir = Get-ChildItem -Directory $x | Where-Object { $_.Name -like 'requality-mcp-*' } | Select-Object -First 1
  if (-not $dir) { throw 'The archive has no requality-mcp folder.' }
  $name = $dir.Name
  # the new version must start before it replaces the installed one
  $version = & cmd /c "`"$($dir.FullName)\bin\requality-mcp.cmd`" --version"
  if ($LASTEXITCODE -ne 0) { throw "$name does not start (it needs Java 17 or later when it has no jre\)." }
  New-Item -ItemType Directory -Force -Path $rqHome | Out-Null
  $dest = Join-Path $rqHome $name
  $staged = "$dest.new"
  if (Test-Path $staged) { Remove-Item -Recurse -Force $staged }
  Move-Item $dir.FullName $staged
  if (Test-Path $dest) {
    # a running server holds its files: the rename fails and the installed version stays whole
    $old = "$dest.old-" + [guid]::NewGuid()
    try { Rename-Item $dest $old } catch {
      Remove-Item -Recurse -Force $staged -ErrorAction SilentlyContinue
      throw "$name is running (an MCP client uses it): close the client, then install again."
    }
    Remove-Item -Recurse -Force $old -ErrorAction SilentlyContinue
  }
  Rename-Item $staged $dest
  # a command in the installation folder that starts the installed version; %~dp0 is the folder of the command, so the
  # file is ASCII also when the folder name is not
  $cmd = Join-Path $rqHome 'requality-mcp.cmd'
  Set-Content -Path $cmd -Encoding ASCII -Value "@echo off`r`n`"%~dp0$name\bin\requality-mcp.cmd`" %*`r`nexit /b %ERRORLEVEL%"
  # the other versions go, when no server runs them
  Get-ChildItem -Directory $rqHome | Where-Object { $_.Name -like 'requality-mcp-*' -and $_.Name -ne $name } |
    ForEach-Object { Remove-Item -Recurse -Force $_.FullName -ErrorAction SilentlyContinue }
  Write-Host "Installed $version in $dest."
  Write-Host "Command: $cmd"
  Write-Host ''
  Write-Host 'Connect Claude Code (the folder contains your Requality catalogs; a .cmd command starts through cmd /c):'
  Write-Host "  claude mcp add requality -- cmd /c `"$cmd`" C:\path\to\catalogs"
  Write-Host "Uninstall: Remove-Item -Recurse `"$rqHome`""
} finally {
  Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}
