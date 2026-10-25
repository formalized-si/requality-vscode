# Installs the Requality MCP server (requality-mcp) from the GitHub releases of formalized-si/requality-vscode:
#   irm https://raw.githubusercontent.com/formalized-si/requality-vscode/main/scripts/install-mcp.ps1 | iex
#
# The archive of the platform contains a Java runtime. Environment:
#   REQUALITY_MCP_VERSION  a release, for example 1.5.0 (default: the newest release, pre-releases too)
#   REQUALITY_MCP_HOME     the installation folder (default: %USERPROFILE%\.requality-mcp)
#   REQUALITY_MCP_ARCHIVE  a downloaded archive (.zip) to install instead of a download
$ErrorActionPreference = 'Stop'
$repo = 'formalized-si/requality-vscode'
$rqHome = if ($env:REQUALITY_MCP_HOME) { $env:REQUALITY_MCP_HOME } else { Join-Path $env:USERPROFILE '.requality-mcp' }
$arch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'arm64' } else { 'x64' }
$target = "win32-$arch"
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("requality-mcp-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
  if ($env:REQUALITY_MCP_ARCHIVE) {
    $archive = $env:REQUALITY_MCP_ARCHIVE
  } else {
    $api = if ($env:REQUALITY_MCP_VERSION) {
      "https://api.github.com/repos/$repo/releases/tags/v$($env:REQUALITY_MCP_VERSION.TrimStart('v'))"
    } else {
      "https://api.github.com/repos/$repo/releases?per_page=20"
    }
    $releases = @(Invoke-RestMethod -Uri $api -Headers @{ 'User-Agent' = 'requality-mcp-installer' })
    $asset = $releases | ForEach-Object { $_.assets } | Where-Object { $_.name -like "requality-mcp-*-$target.zip" } | Select-Object -First 1
    if (-not $asset) { throw "No requality-mcp archive for $target in the releases of $repo." }
    $archive = Join-Path $tmp $asset.name
    Write-Host "Downloading $($asset.browser_download_url)"
    Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $archive -UseBasicParsing
    # the SHA-256 that GitHub gives for the asset, when there is one
    if ($asset.digest -and $asset.digest.StartsWith('sha256:')) {
      $actual = (Get-FileHash -Algorithm SHA256 $archive).Hash.ToLowerInvariant()
      if ($actual -ne $asset.digest.Substring(7)) { throw "The SHA-256 of the archive is $actual, the release gives $($asset.digest)." }
      Write-Host 'SHA-256 is correct.'
    }
  }
  $x = Join-Path $tmp 'x'
  Expand-Archive -Path $archive -DestinationPath $x
  $dir = Get-ChildItem -Directory $x | Where-Object { $_.Name -like 'requality-mcp-*' } | Select-Object -First 1
  if (-not $dir) { throw 'The archive has no requality-mcp folder.' }
  New-Item -ItemType Directory -Force -Path $rqHome | Out-Null
  $dest = Join-Path $rqHome $dir.Name
  if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
  Move-Item $dir.FullName $dest
  # a command in the installation folder that starts the installed version
  $cmd = Join-Path $rqHome 'requality-mcp.cmd'
  Set-Content -Path $cmd -Encoding ASCII -Value "@echo off`r`n`"$dest\bin\requality-mcp.cmd`" %*`r`nexit /b %ERRORLEVEL%"
  Write-Host "Installed $($dir.Name) in $dest."
  Write-Host "Command: $cmd"
  Write-Host ''
  Write-Host 'Connect Claude Code (the folder contains your Requality catalogs):'
  Write-Host "  claude mcp add requality -- `"$cmd`" C:\path\to\catalogs"
} finally {
  Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}
