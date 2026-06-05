#!/usr/bin/env bash
# Launch an isolated shell with the local jbang build on PATH and completions loaded.
# Usage: dev-shell.sh <zsh|bash|fish> <path-to-jbang-bin>
set -euo pipefail

SHELL_TYPE="${1:?Usage: dev-shell.sh <zsh|bash|fish> <jbang-bin-dir>}"
JBANG_BIN="${2:?Usage: dev-shell.sh <zsh|bash|fish> <jbang-bin-dir>}"

dir=$(mktemp -d)
trap 'rm -rf "$dir"' EXIT

case "$SHELL_TYPE" in
zsh)
  "$JBANG_BIN/jbang" completion -s zsh > "$dir/_jbang"
  cat > "$dir/.zshrc" <<EOF
autoload -Uz compinit
fpath=("$dir" \$fpath)
compinit -d "$dir/.zcompdump"
export PATH="$JBANG_BIN:\$PATH"
export PS1="(jbang-dev) %~ %# "
EOF
  echo "Entering zsh with jbang completions. Exit with 'exit'."
  ZDOTDIR="$dir" exec zsh -i
  ;;
bash)
  "$JBANG_BIN/jbang" completion -s bash > "$dir/jbang-completion.bash"
  cat > "$dir/.bashrc" <<EOF
source "$dir/jbang-completion.bash"
export PATH="$JBANG_BIN:\$PATH"
export PS1="(jbang-dev) \w \$ "
EOF
  echo "Entering bash with jbang completions. Exit with 'exit'."
  exec bash --rcfile "$dir/.bashrc"
  ;;
fish)
  mkdir -p "$dir/fish/completions" "$dir/fish/conf.d"
  "$JBANG_BIN/jbang" completion -s fish > "$dir/fish/completions/jbang.fish"
  cat > "$dir/fish/conf.d/jbang.fish" <<EOF
set -gx PATH "$JBANG_BIN" \$PATH
function fish_prompt; echo "(jbang-dev) "(prompt_pwd)" > "; end
EOF
  echo "Entering fish with jbang completions. Exit with 'exit'."
  XDG_CONFIG_HOME="$dir" exec fish -i
  ;;
*)
  echo "Unknown shell: $SHELL_TYPE. Use zsh, bash, or fish."
  exit 1
  ;;
esac
