#!/bin/sh
# 特設サイトの配布物 (https://transformer-dojo.opsbr.dev/) を transformer-dojo/ に展開する
set -eu

site=https://transformer-dojo.opsbr.dev
case "$(uname -s)" in
  Linux) os=linux ;;
  Darwin) os=macos ;;
  *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac
case "$(uname -m)" in
  x86_64) cpu=x86_64 ;;
  arm64 | aarch64) cpu=arm64 ;;
  *) echo "unsupported CPU: $(uname -m)" >&2; exit 1 ;;
esac

cd "$(dirname "$0")"
if [ -x transformer-dojo/dojo ]; then
  echo "transformer-dojo/ is already set up" >&2
  exit 0
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
curl -fL -o "$tmp/dojo.tgz" "$site/$os-$cpu.tgz"
mkdir -p transformer-dojo
# work/ は自分のコード。配布物の .git は使わない (このリポジトリが work/ を追跡する)
tar -xzf "$tmp/dojo.tgz" -C transformer-dojo --strip-components=1 --exclude='./.git' --exclude='transformer-dojo/.git' --exclude='transformer-dojo/work'
mkdir -p transformer-dojo/work
