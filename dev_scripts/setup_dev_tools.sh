#!/usr/bin/env bash
# Sets up Babashka, bbin, and clojure-mcp-light tools for local development.
# Safe to re-run — skips steps that are already complete.
set -euo pipefail

CLOJURE_MCP_LIGHT_TAG="v0.2.2"
# bbin's internal deps.clj bootstrapper needs this specific version of Clojure tools.
# We pre-fetch via curl to avoid Java SSL cert issues that block the in-process download.
DEPS_CLJ_VERSION="1.12.4.1618"

# ── Babashka ──────────────────────────────────────────────────────────────────
if ! command -v bb &>/dev/null; then
  echo "Installing Babashka..."
  tmp=$(mktemp)
  curl -sLo "$tmp" https://raw.githubusercontent.com/babashka/babashka/master/install
  chmod +x "$tmp"
  sudo "$tmp"
  rm "$tmp"
else
  echo "Babashka already installed: $(bb --version)"
fi

# ── bbin ──────────────────────────────────────────────────────────────────────
if [ ! -f "$HOME/.local/bin/bbin" ]; then
  echo "Installing bbin..."
  mkdir -p "$HOME/.local/bin"
  curl -sLo "$HOME/.local/bin/bbin" https://raw.githubusercontent.com/babashka/bbin/main/bbin
  chmod +x "$HOME/.local/bin/bbin"
else
  echo "bbin already installed"
fi

export PATH="$HOME/.local/bin:$PATH"

# ── Clojure tools pre-fetch (bbin bootstrap workaround) ──────────────────────
DEPS_CLJ_DIR="$HOME/.deps.clj/$DEPS_CLJ_VERSION/ClojureTools"
if [ ! -f "$DEPS_CLJ_DIR/clojure-tools-$DEPS_CLJ_VERSION.jar" ]; then
  echo "Pre-fetching Clojure tools for bbin ($DEPS_CLJ_VERSION)..."
  mkdir -p "$DEPS_CLJ_DIR"
  curl -sLo "$DEPS_CLJ_DIR/clojure-tools.zip" \
    "https://github.com/clojure/brew-install/releases/download/$DEPS_CLJ_VERSION/clojure-tools.zip"
fi

# ── clojure-mcp-light ─────────────────────────────────────────────────────────
install_if_missing() {
  local name="$1"; shift
  if [ ! -f "$HOME/.local/bin/$name" ]; then
    echo "Installing $name..."
    bbin install "https://github.com/bhauman/clojure-mcp-light.git" \
      --tag "$CLOJURE_MCP_LIGHT_TAG" "$@"
  else
    echo "$name already installed"
  fi
}

install_if_missing clj-paren-repair-claude-hook
install_if_missing clj-nrepl-eval \
  --as clj-nrepl-eval \
  --main-opts '["-m" "clojure-mcp-light.nrepl-eval"]'
install_if_missing clj-paren-repair \
  --as clj-paren-repair \
  --main-opts '["-m" "clojure-mcp-light.paren-repair"]'

echo ""
echo "Done. Ensure the following is on your PATH:"
echo "  export PATH=\"\$HOME/.local/bin:\$PATH\""
