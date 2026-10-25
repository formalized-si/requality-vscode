#!/bin/sh
# Installs the Requality MCP server (requality-mcp) from the GitHub releases of formalized-si/requality-vscode:
#   curl -fsSL https://raw.githubusercontent.com/formalized-si/requality-vscode/main/scripts/install-mcp.sh | sh
#
# The archive of the platform contains a Java runtime. The newest release that has one is installed; a pre-release
# only when no release has one, or with REQUALITY_MCP_PRERELEASE=1. Environment:
#   REQUALITY_MCP_VERSION     a release, for example 1.5.0
#   REQUALITY_MCP_PRERELEASE  1: the newest release with an archive, also a pre-release
#   REQUALITY_MCP_HOME        the installation folder (default: ~/.local/share/requality-mcp)
#   REQUALITY_MCP_BIN         the folder of the command link (default: ~/.local/bin)
#   REQUALITY_MCP_ARCHIVE     a downloaded archive (.tar.gz or .zip) to install instead of a download
# The server keeps its caches and change histories in ~/.requality-mcp, apart from the installation: an uninstall
# (rm -rf of the installation folder and the command link) keeps them.
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
    | sed 's/.*"\(https[^"]*\)"$/\1/' || true
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
  trap 'rm -rf "$tmp"' EXIT

  if [ -n "${REQUALITY_MCP_ARCHIVE:-}" ]; then
    archive=$(absolute "$REQUALITY_MCP_ARCHIVE")
    [ -f "$archive" ] || fail "no file $archive"
  else
    api=https://api.github.com/repos/$repo/releases
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
    else
      echo "Warning: the release gives no SHA-256 for the archive; it is not checked." >&2
    fi
  fi

  mkdir -p "$tmp/x" "$home" "$bin"
  case "$archive" in
    *.zip) unzip -q "$archive" -d "$tmp/x" ;;
    *) tar -xzf "$archive" -C "$tmp/x" ;;
  esac
  dir=$(find "$tmp/x" -mindepth 1 -maxdepth 1 -type d -name 'requality-mcp-*' | head -n 1)
  [ -n "$dir" ] || fail "the archive has no requality-mcp folder"
  name=$(basename "$dir")
  chmod +x "$dir/bin/requality-mcp"
  # the new version must start before it replaces the installed one
  version=$("$dir/bin/requality-mcp" --version) || fail "$name does not start (it needs Java 17 or later when it has no jre/)"
  rm -rf "${home:?}/$name.new"
  mv "$dir" "$home/$name.new"
  rm -rf "${home:?}/$name"
  mv "$home/$name.new" "$home/$name"
  ln -sfn "$home/$name" "$home/current"
  ln -sf "$home/current/bin/requality-mcp" "$bin/requality-mcp"
  # the other versions go
  for old in "$home"/requality-mcp-*; do
    [ -d "$old" ] && [ "$old" != "$home/$name" ] && rm -rf "$old"
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
  echo "Uninstall: rm -rf '$home' '$bin/requality-mcp'"
}

main "$@"
