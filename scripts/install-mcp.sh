#!/bin/sh
# Installs the Requality MCP server (requality-mcp) from the GitHub releases of formalized-si/requality-vscode:
#   curl -fsSL https://raw.githubusercontent.com/formalized-si/requality-vscode/main/scripts/install-mcp.sh \
#     -o install-mcp.sh && sh install-mcp.sh
# ("curl ... | sh" also works, but when the download fails it runs nothing and still ends with exit code 0.)
#
# The archive of the platform contains a Java runtime. The newest release that has one is installed; a pre-release
# only when no release has one, or with REQUALITY_MCP_PRERELEASE=1. Environment:
#   REQUALITY_MCP_VERSION       a release, for example 1.5.0
#   REQUALITY_MCP_PRERELEASE    1: the newest release with an archive, also a pre-release
#   REQUALITY_MCP_HOME          the installation folder (default: ~/.local/share/requality-mcp)
#   REQUALITY_MCP_BIN           the folder of the command link (default: ~/.local/bin)
#   REQUALITY_MCP_ARCHIVE       a downloaded archive (.tar.gz or .zip) to install instead of a download
#   REQUALITY_MCP_INSECURE      1: install also when the release gives no SHA-256 for the archive
#   REQUALITY_MCP_UNVERIFIED    1: install also when the build provenance attestation cannot be checked
#   REQUALITY_MCP_RELEASES_URL  the releases API, for tests
#                               (default: https://api.github.com/repos/formalized-si/requality-vscode/releases)
# The script verifies the build provenance attestation with the GitHub CLI (gh 2.68 or later, signed in to github.com):
# the release workflow of the repository made the archive from the tag of the release. Without gh it stops, unless
# REQUALITY_MCP_UNVERIFIED=1 (the SHA-256, which comes from the same release, is checked).
# A version that a running server uses stays; a later install removes it. The server keeps its caches and change
# histories in ~/.requality-mcp, apart from the installation.
#
# All the work is in main, called on the last line: a download that ends early runs nothing.
set -eu

repo=formalized-si/requality-vscode

fail() {
  echo "requality-mcp install: $*" >&2
  exit 1
}

# an absolute path (a relative one is taken from the current folder)
absolute() {
  case "$1" in
    /*) printf '%s\n' "$1" ;;
    *) printf '%s/%s\n' "$(pwd)" "$1" ;;
  esac
}

sha256() {
  if command -v shasum > /dev/null 2>&1; then shasum -a 256 "$1" | cut -d ' ' -f 1
  elif command -v sha256sum > /dev/null 2>&1; then sha256sum "$1" | cut -d ' ' -f 1
  else fail "no shasum or sha256sum to check the download"; fi
}

# the download link of the archive of the target in a JSON of releases, or nothing
asset_url() {
  grep -o "\"browser_download_url\": *\"[^\"]*/requality-mcp-[0-9.]*-$2.tar.gz\"" "$1" | head -n 1 \
    | sed 's/.*"\(https*:[^"]*\)"$/\1/' || true
}

# the build provenance attestation of the archive: made by the release workflow of the repository from the tag of the
# release (the folder of the download link); not checked when gh cannot check it
attestation() {
  why=
  if ! command -v gh > /dev/null 2>&1; then
    why="no GitHub CLI (gh)"
  elif ! gh attestation verify --help 2> /dev/null | grep -q -e '--source-ref'; then
    why="it needs gh 2.68 or later"
  elif ! gh auth status --hostname github.com > /dev/null 2>&1; then
    why="gh is not signed in to github.com"
  fi
  if [ -n "$why" ]; then
    # the SHA-256 comes from the same release: only the attestation shows that the release workflow made the archive
    if [ "${REQUALITY_MCP_UNVERIFIED:-}" = 1 ]; then
      echo "Warning: the build provenance attestation is not checked ($why; REQUALITY_MCP_UNVERIFIED=1)." >&2
      return 0
    fi
    fail "the build provenance attestation cannot be checked: $why. Install the GitHub CLI 2.68 or later and run" \
      "gh auth login, or set REQUALITY_MCP_UNVERIFIED=1 to install without this check (the SHA-256 is checked)"
  else
    tag=$(basename "$(dirname "$2")")
    if out=$(gh attestation verify "$1" -R "$repo" --signer-workflow "$repo/.github/workflows/release.yml" \
      --source-ref "refs/tags/$tag" 2>&1); then
      echo "Build provenance attestation is correct."
    else
      printf '%s\n' "$out" >&2
      fail "the archive has no valid build provenance attestation of the release workflow of $repo for $tag"
    fi
  fi
}

# the command lines of the running processes, one in a line
processes() {
  ps -A -ww -o args= 2> /dev/null && return 0
  # no ps (a small container): the command lines of Linux
  [ -d /proc/self ] || return 1
  for f in /proc/[0-9]*/cmdline; do
    { tr '\0' ' ' < "$f" && echo; } 2> /dev/null || true
  done
  return 0
}

# 0 when a running Java has the folder in its command line (a server of an MCP client runs from it), and when the
# processes cannot be read
in_use() {
  processes > "$tmp/processes" || return 0
  while IFS= read -r line; do
    case "$line" in
      *java*"$1/"* | *"$1/"*java*) return 0 ;;
    esac
  done < "$tmp/processes"
  return 1
}

main() {
  home=$(absolute "${REQUALITY_MCP_HOME:-$HOME/.local/share/requality-mcp}")
  bin=$(absolute "${REQUALITY_MCP_BIN:-$HOME/.local/bin}")

  case "$(uname -s)" in
    Darwin) os=darwin ;;
    Linux) os=linux ;;
    *) fail "this script is for macOS and Linux; on Windows use install-mcp.ps1" ;;
  esac
  case "$(uname -m)" in
    x86_64 | amd64) arch=x64 ;;
    arm64 | aarch64) arch=arm64 ;;
    *) fail "no archive for the machine $(uname -m): use requality-mcp-<version>.zip with Java 17 or later" ;;
  esac
  # a shell under Rosetta reports x86_64 on an Apple silicon Mac
  if [ "$os" = darwin ] && [ "$arch" = x64 ] && [ "$(sysctl -n sysctl.proc_translated 2> /dev/null || echo 0)" = 1 ]; then
    arch=arm64
  fi
  # the Java runtime of the archives is built for glibc
  if [ "$os" = linux ] && { ls /lib/ld-musl-* > /dev/null 2>&1 || ldd --version 2>&1 | grep -qi musl; }; then
    fail "this Linux uses musl (Alpine): use requality-mcp-<version>.zip with a Java 17 or later of the system"
  fi
  target=$os-$arch
  tmp=$(mktemp -d)
  staging="$home/.staging-$$"
  trap 'rm -rf "$tmp" "$staging"' EXIT

  if [ -n "${REQUALITY_MCP_ARCHIVE:-}" ]; then
    archive=$(absolute "$REQUALITY_MCP_ARCHIVE")
    [ -f "$archive" ] || fail "no file $archive"
  else
    api=${REQUALITY_MCP_RELEASES_URL:-https://api.github.com/repos/$repo/releases}
    api=${api%/}
    url=
    if [ -n "${REQUALITY_MCP_VERSION:-}" ]; then
      curl -fsSL "$api/tags/v${REQUALITY_MCP_VERSION#v}" -o "$tmp/releases.json"
      url=$(asset_url "$tmp/releases.json" "$target")
    else
      if [ "${REQUALITY_MCP_PRERELEASE:-}" != 1 ]; then
        # the newest release that is not a pre-release
        if curl -fsSL "$api/latest" -o "$tmp/releases.json" 2> /dev/null; then
          url=$(asset_url "$tmp/releases.json" "$target")
        fi
      fi
      if [ -z "$url" ]; then
        curl -fsSL "$api?per_page=30" -o "$tmp/releases.json"
        url=$(asset_url "$tmp/releases.json" "$target")
        [ -z "$url" ] || [ "${REQUALITY_MCP_PRERELEASE:-}" = 1 ] \
          || echo "The newest release has no requality-mcp archive for $target: installing the newest release that has one (it can be a pre-release)."
      fi
    fi
    [ -n "$url" ] || fail "no requality-mcp archive for $target in the releases of $repo"
    archive="$tmp/$(basename "$url")"
    echo "Downloading $url"
    curl -fL --progress-bar -o "$archive" "$url"
    # the SHA-256 that GitHub gives for the asset ("digest": "sha256:..."), in the same JSON object as its link
    digest=$(tr -d '\n' < "$tmp/releases.json" | tr '}' '\n' | grep -F "$(basename "$url")\"" \
      | grep -o '"digest": *"sha256:[0-9a-f]*"' | head -n 1 | sed 's/.*sha256:\([0-9a-f]*\)"/\1/' || true)
    if [ -n "$digest" ]; then
      actual=$(sha256 "$archive")
      [ "$actual" = "$digest" ] || fail "the SHA-256 of the archive is $actual, the release gives $digest"
      echo "SHA-256 is correct."
    elif [ "${REQUALITY_MCP_INSECURE:-}" = 1 ]; then
      echo "Warning: the release gives no SHA-256 for the archive; it is not checked (REQUALITY_MCP_INSECURE=1)." >&2
    else
      fail "the release gives no SHA-256 for the archive; set REQUALITY_MCP_INSECURE=1 to install it without the check"
    fi
    attestation "$archive" "$url"
  fi

  mkdir -p "$home" "$bin"
  # the physical folder: a server runs from the physical folder of its version (see bin/requality-mcp)
  home=$(cd -P -- "$home" && pwd -P)
  # the new version is unpacked and started in the installation folder (not in /tmp, which can forbid running programs)
  staging="$home/.staging-$$"
  rm -rf "$staging"
  mkdir -p "$staging"
  case "$archive" in
    *.zip) unzip -q "$archive" -d "$staging" ;;
    *) tar -xzf "$archive" -C "$staging" ;;
  esac
  dir=$(find "$staging" -mindepth 1 -maxdepth 1 -type d -name 'requality-mcp-*' | head -n 1)
  [ -n "$dir" ] || fail "the archive has no requality-mcp folder"
  name=$(basename "$dir")
  chmod +x "$dir/bin/requality-mcp"
  # the new version must start before it replaces the installed one
  if ! version=$("$dir/bin/requality-mcp" --version); then
    fail "$name does not start (it needs Java 17 or later when it has no jre/)"
  fi
  # the marker of a folder this script installed: only such folders are replaced and removed
  echo "$version" > "$dir/.requality-mcp-install"
  if [ -e "$home/$name" ] && [ ! -f "$home/$name/.requality-mcp-install" ]; then
    fail "$home/$name exists and is not an installation of this script"
  fi
  if [ -e "$home/$name" ] && in_use "$home/$name"; then
    fail "$name is running (an MCP client uses it): close the client, then install again"
  fi
  rm -rf "${home:?}/$name"
  mv "$dir" "$home/$name"
  rm -rf "$staging"
  ln -sfn "$home/$name" "$home/current"
  ln -sf "$home/current/bin/requality-mcp" "$bin/requality-mcp"
  # the other versions this script installed go, when no server runs from them
  for old in "$home"/requality-mcp-*; do
    if [ -f "$old/.requality-mcp-install" ] && [ "$old" != "$home/$name" ]; then
      if in_use "$old"; then
        echo "$(basename "$old") stays: a running server uses it. A later install removes it."
      else
        rm -rf "$old"
      fi
    fi
  done

  echo "Installed $version in $home/$name."
  echo "Command: $bin/requality-mcp"
  case ":$PATH:" in
    *":$bin:"*) ;;
    *) echo "$bin is not on the PATH: use the full path of the command." ;;
  esac
  echo
  echo "Connect Claude Code (the folder contains your Requality catalogs):"
  echo "  claude mcp add requality -- '$bin/requality-mcp' '/path/to/catalogs'"
  echo "Uninstall: rm -rf '$home/$name' '$home/current' '$bin/requality-mcp'"
}

main "$@"
