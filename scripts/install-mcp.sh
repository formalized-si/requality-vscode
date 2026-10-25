#!/bin/sh
# Installs the Requality MCP server (requality-mcp) from the GitHub releases of formalized-si/requality-vscode:
#   curl -fsSL https://raw.githubusercontent.com/formalized-si/requality-vscode/main/scripts/install-mcp.sh | sh
#
# The archive of the platform contains a Java runtime. Environment:
#   REQUALITY_MCP_VERSION  a release, for example 1.5.0 (default: the newest release, pre-releases too)
#   REQUALITY_MCP_HOME     the installation folder (default: ~/.requality-mcp)
#   REQUALITY_MCP_BIN      the folder of the command link (default: ~/.local/bin)
#   REQUALITY_MCP_ARCHIVE  a downloaded archive (.tar.gz or .zip) to install instead of a download
set -eu
repo=formalized-si/requality-vscode
home=${REQUALITY_MCP_HOME:-$HOME/.requality-mcp}
bin=${REQUALITY_MCP_BIN:-$HOME/.local/bin}

case "$(uname -s)" in
  Darwin) os=darwin ;;
  Linux) os=linux ;;
  *) echo "This script is for macOS and Linux. On Windows use install-mcp.ps1." >&2; exit 1 ;;
esac
case "$(uname -m)" in
  x86_64 | amd64) arch=x64 ;;
  arm64 | aarch64) arch=arm64 ;;
  *) echo "No archive for the machine $(uname -m): use requality-mcp-<version>.zip and Java 17 or later." >&2; exit 1 ;;
esac
target=$os-$arch
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

if [ -n "${REQUALITY_MCP_ARCHIVE:-}" ]; then
  archive=$REQUALITY_MCP_ARCHIVE
else
  if [ -n "${REQUALITY_MCP_VERSION:-}" ]; then
    api="https://api.github.com/repos/$repo/releases/tags/v${REQUALITY_MCP_VERSION#v}"
  else
    api="https://api.github.com/repos/$repo/releases?per_page=20"
  fi
  curl -fsSL "$api" -o "$tmp/releases.json"
  name="requality-mcp-[0-9.]*-$target.tar.gz"
  url=$(grep -o "\"browser_download_url\": *\"[^\"]*/$name\"" "$tmp/releases.json" | head -n 1 | sed 's/.*"\(https[^"]*\)"$/\1/')
  if [ -z "$url" ]; then
    echo "No requality-mcp archive for $target in the releases of $repo." >&2
    exit 1
  fi
  archive="$tmp/$(basename "$url")"
  echo "Downloading $url"
  curl -fL --progress-bar -o "$archive" "$url"
  # the SHA-256 that GitHub gives for the asset ("digest": "sha256:..."), when there is one
  # (one line for each JSON object: the digest and the download link of an asset are in the same object)
  digest=$(tr -d '\n' < "$tmp/releases.json" | tr '}' '\n' | grep -F "$(basename "$url")\"" \
    | grep -o '"digest": *"sha256:[0-9a-f]*"' | head -n 1 | sed 's/.*sha256:\([0-9a-f]*\)"/\1/' || true)
  if [ -n "$digest" ]; then
    if command -v shasum > /dev/null 2>&1; then actual=$(shasum -a 256 "$archive" | cut -d ' ' -f 1)
    else actual=$(sha256sum "$archive" | cut -d ' ' -f 1); fi
    if [ "$actual" != "$digest" ]; then
      echo "The SHA-256 of the archive is $actual, the release gives $digest." >&2
      exit 1
    fi
    echo "SHA-256 is correct."
  fi
fi

mkdir -p "$tmp/x" "$home" "$bin"
case "$archive" in
  *.zip) unzip -q "$archive" -d "$tmp/x" ;;
  *) tar -xzf "$archive" -C "$tmp/x" ;;
esac
dir=$(find "$tmp/x" -mindepth 1 -maxdepth 1 -type d -name 'requality-mcp-*' | head -n 1)
if [ -z "$dir" ]; then
  echo "The archive has no requality-mcp folder." >&2
  exit 1
fi
name=$(basename "$dir")
rm -rf "${home:?}/$name"
mv "$dir" "$home/$name"
chmod +x "$home/$name/bin/requality-mcp"
ln -sfn "$home/$name" "$home/current"
ln -sf "$home/current/bin/requality-mcp" "$bin/requality-mcp"

echo "Installed $name in $home/$name."
echo "Command: $bin/requality-mcp"
case ":$PATH:" in
  *":$bin:"*) ;;
  *) echo "$bin is not on the PATH: use the full path of the command." ;;
esac
echo
echo "Connect Claude Code (the folder contains your Requality catalogs):"
echo "  claude mcp add requality -- $bin/requality-mcp /path/to/catalogs"
