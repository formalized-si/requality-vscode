# Installs the Requality MCP server (requality-mcp) from the GitHub releases of formalized-si/requality-vscode:
#   irm https://raw.githubusercontent.com/formalized-si/requality-vscode/main/scripts/install-mcp.ps1 | iex
#
# The archive of the platform contains a Java runtime. The newest release that has one is installed; a pre-release
# only when no release has one, or with REQUALITY_MCP_PRERELEASE=1. Environment:
#   REQUALITY_MCP_VERSION       a release, for example 1.5.0
#   REQUALITY_MCP_PRERELEASE    1: the newest release with an archive, also a pre-release
#   REQUALITY_MCP_HOME          the installation folder (default: %LOCALAPPDATA%\Programs\requality-mcp)
#   REQUALITY_MCP_ARCHIVE       a downloaded archive (.zip) to install instead of a download
#   REQUALITY_MCP_INSECURE      1: install also when the release gives no SHA-256 for the archive
#   REQUALITY_MCP_UNVERIFIED    1: install also when the build provenance attestation cannot be checked
#   REQUALITY_MCP_RELEASES_URL  the releases API, for tests
#                               (default: https://api.github.com/repos/formalized-si/requality-vscode/releases)
# The script verifies the build provenance attestation with the GitHub CLI (gh 2.68 or later, signed in to github.com):
# the release workflow of the repository made the archive from the tag of the release. Without gh it stops, unless
# REQUALITY_MCP_UNVERIFIED=1 (the SHA-256, which comes from the same release, is checked).
# A version that a running server uses stays; a later install removes it. The server keeps its caches and change
# histories in %USERPROFILE%\.requality-mcp, apart from the installation.
#
# All the work is in one block: a download that ends early runs nothing, and with "irm ... | iex" the settings and
# variables of the script do not stay in the session. An error stops the script (exit code 1 with powershell -File).
& {
  $ErrorActionPreference = 'Stop'
  # the progress bar of Invoke-WebRequest makes a large download very slow in Windows PowerShell 5.1
  $ProgressPreference = 'SilentlyContinue'
  $repo = 'formalized-si/requality-vscode'
  $rqHome = if ($env:REQUALITY_MCP_HOME) { [System.IO.Path]::GetFullPath($env:REQUALITY_MCP_HOME) } else { Join-Path $env:LOCALAPPDATA 'Programs\requality-mcp' }
  $arch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64' -or $env:PROCESSOR_ARCHITEW6432 -eq 'ARM64') { 'arm64' } else { 'x64' }
  $target = "win32-$arch"
  $staging = $null
  $protocols = $null
  $tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("requality-mcp-" + [guid]::NewGuid())
  New-Item -ItemType Directory -Path $tmp | Out-Null

  # True when the system code page has all the letters of the path: the Java launcher reads its own folder in it
  function Test-CodePage([string] $path) {
    $enc = [System.Text.Encoding]::GetEncoding([System.Globalization.CultureInfo]::CurrentCulture.TextInfo.ANSICodePage)
    return $enc.GetString($enc.GetBytes($path)) -eq $path
  }

  function Find-Asset($releases) {
    $releases | ForEach-Object { $_.assets } | Where-Object { $_.name -like "requality-mcp-*-$target.zip" } | Select-Object -First 1
  }

  # the build provenance attestation of the archive: made by the release workflow of the repository from the tag of
  # the release (the folder of the download link); not checked when gh cannot check it
  function Test-Attestation([string] $file, [string] $url) {
    # gh writes to the error stream: in Windows PowerShell 5.1 that stops the script when $ErrorActionPreference is Stop
    $ErrorActionPreference = 'Continue'
    $why = $null
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
      $why = 'no GitHub CLI (gh)'
    } else {
      $help = (& gh attestation verify --help 2>&1 | ForEach-Object { "$_" }) -join "`n"
      if ($LASTEXITCODE -ne 0 -or -not $help.Contains('--source-ref')) {
        $why = 'it needs gh 2.68 or later'
      } else {
        & gh auth status --hostname github.com *> $null
        if ($LASTEXITCODE -ne 0) { $why = 'gh is not signed in to github.com' }
      }
    }
    if ($why) {
      # the SHA-256 comes from the same release: only the attestation shows that the release workflow made the archive
      if ($env:REQUALITY_MCP_UNVERIFIED -eq '1') {
        Write-Warning "The build provenance attestation is not checked ($why; REQUALITY_MCP_UNVERIFIED=1)."
        return
      }
      throw ("The build provenance attestation cannot be checked: $why. Install the GitHub CLI 2.68 or later and run " +
        'gh auth login, or set REQUALITY_MCP_UNVERIFIED=1 to install without this check (the SHA-256 is checked).')
    }
    $parts = $url.Split('/')
    $tag = $parts[$parts.Length - 2]
    $out = (& gh attestation verify $file -R $repo --signer-workflow "$repo/.github/workflows/release.yml" `
        --source-ref "refs/tags/$tag" 2>&1 | ForEach-Object { "$_" }) -join "`n"
    if ($LASTEXITCODE -ne 0) {
      Write-Host $out
      throw "The archive has no valid build provenance attestation of the release workflow of $repo for $tag."
    }
    Write-Host 'Build provenance attestation is correct.'
  }

  try {
    if ($env:REQUALITY_MCP_ARCHIVE) {
      $archive = [System.IO.Path]::GetFullPath($env:REQUALITY_MCP_ARCHIVE)
      if (-not (Test-Path $archive)) { throw "No file $archive" }
    } else {
      # Windows PowerShell 5.1 can offer only TLS 1.0 and 1.1, which GitHub refuses
      if ($PSVersionTable.PSEdition -ne 'Core') {
        $protocols = [Net.ServicePointManager]::SecurityProtocol
        [Net.ServicePointManager]::SecurityProtocol = $protocols -bor [Net.SecurityProtocolType]::Tls12
      }
      $api = if ($env:REQUALITY_MCP_RELEASES_URL) { $env:REQUALITY_MCP_RELEASES_URL.TrimEnd('/') } else { "https://api.github.com/repos/$repo/releases" }
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
      } elseif ($env:REQUALITY_MCP_INSECURE -eq '1') {
        Write-Warning 'The release gives no SHA-256 for the archive; it is not checked (REQUALITY_MCP_INSECURE=1).'
      } else {
        throw 'The release gives no SHA-256 for the archive. Set REQUALITY_MCP_INSECURE=1 to install it without the check.'
      }
      Test-Attestation $archive $asset.browser_download_url
    }
    New-Item -ItemType Directory -Force -Path $rqHome | Out-Null
    if (-not (Test-CodePage $rqHome)) {
      # the launcher uses the short (8.3) name of the folder, when the volume has short names
      $short = (New-Object -ComObject Scripting.FileSystemObject).GetFolder($rqHome).ShortPath
      if (-not (Test-CodePage $short)) {
        Write-Warning ("The Java runtime cannot start from a folder with letters outside the code page of the system: $rqHome. " +
          'If the server does not start, set REQUALITY_MCP_HOME to another folder, for example C:\requality-mcp.')
      }
    }
    # the new version is unpacked and started in the installation folder
    $staging = Join-Path $rqHome ('.staging-' + [guid]::NewGuid())
    Expand-Archive -Path $archive -DestinationPath $staging
    $dir = Get-ChildItem -Directory $staging | Where-Object { $_.Name -like 'requality-mcp-*' } | Select-Object -First 1
    if (-not $dir) { throw 'The archive has no requality-mcp folder.' }
    $name = $dir.Name
    # the new version must start before it replaces the installed one
    $version = & cmd /c "`"$($dir.FullName)\bin\requality-mcp.cmd`" --version"
    if ($LASTEXITCODE -ne 0) {
      throw "$name does not start. It needs Java 17 or later when it has no jre\, and a folder whose name has only letters of the code page of the system."
    }
    # the marker of a folder this script installed: only such folders are replaced and removed
    Set-Content -Path (Join-Path $dir.FullName '.requality-mcp-install') -Value $version -Encoding ASCII
    $dest = Join-Path $rqHome $name
    if ((Test-Path $dest) -and -not (Test-Path (Join-Path $dest '.requality-mcp-install'))) {
      throw "$dest exists and is not an installation of this script."
    }
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
    # the other versions this script installed go, when no server runs from them. A folder is renamed first: a running
    # server holds its files, so the rename fails and the folder stays whole (a removal would delete only a part)
    Get-ChildItem -Directory $rqHome | Where-Object {
      $_.Name -like 'requality-mcp-*' -and $_.Name -ne $name -and
      ($_.Name -like '*.old-*' -or (Test-Path (Join-Path $_.FullName '.requality-mcp-install')))
    } | ForEach-Object {
      $folder = $_
      $gone = $folder.FullName
      if ($folder.Name -notlike '*.old-*') {
        $leaf = $folder.Name + '.old-' + [guid]::NewGuid()
        $gone = Join-Path $rqHome $leaf
        try { Rename-Item -LiteralPath $folder.FullName -NewName $leaf -ErrorAction Stop } catch {
          Write-Host "$($folder.Name) stays: a running server uses it. A later install removes it."
          return
        }
      }
      Remove-Item -LiteralPath $gone -Recurse -Force -ErrorAction SilentlyContinue
    }
    Write-Host "Installed $version in $dest."
    Write-Host "Command: $cmd"
    Write-Host ''
    Write-Host 'Connect Claude Code (the folder contains your Requality catalogs; a .cmd command starts through cmd /c):'
    Write-Host "  claude mcp add requality -- cmd /c `"$cmd`" C:\path\to\catalogs"
    Write-Host "Uninstall: Remove-Item -Recurse `"$dest`", `"$cmd`""
  } finally {
    if ($null -ne $protocols) { [Net.ServicePointManager]::SecurityProtocol = $protocols }
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
    if ($staging) { Remove-Item -Recurse -Force $staging -ErrorAction SilentlyContinue }
  }
}
