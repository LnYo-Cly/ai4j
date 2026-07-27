#!/usr/bin/env bash
#
# ai4j CLI installer for Linux and macOS.
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/LnYo-Cly/ai4j/main/ai4j-cli/scripts/install.sh | bash
#   curl -fsSL https://raw.githubusercontent.com/LnYo-Cly/ai4j/main/ai4j-cli/scripts/install.sh | bash -s -- v2.4.2
#
set -euo pipefail

REPO="LnYo-Cly/ai4j"
INSTALL_DIR="${AI4J_HOME:-$HOME/.ai4j}"
BIN_DIR="$INSTALL_DIR/bin"

# ---- helpers ----------------------------------------------------------------

info()  { printf "\033[1;32m==>\033[0m %s\n" "$*"; }
warn()  { printf "\033[1;33m!!\033[0m %s\n" "$*"; }
fatal() { printf "\033[1;31merror:\033[0m %s\n" "$*" >&2; exit 1; }

need_cmd() {
    command -v "$1" >/dev/null 2>&1 || fatal "'$1' is required but not found in PATH."
}

# ---- pre-flight -------------------------------------------------------------

need_cmd curl
need_cmd java

JAVA_MAJOR=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9+]+).*|.*/\1/' | tr -d '+' | tr -d '"')
if [ -n "${JAVA_MAJOR:-}" ] && [ "$JAVA_MAJOR" -lt 8 ] 2>/dev/null; then
    fatal "Java 8 or later is required (found $JAVA_MAJOR)."
fi
info "Java detected: $(java -version 2>&1 | head -1)"

# ---- resolve version --------------------------------------------------------

VERSION="${1:-latest}"

if [ "$VERSION" = "latest" ]; then
    info "Fetching latest release version..."
    VERSION_TAG=$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" \
        | sed -n 's/.*"tag_name": *"\([^"]*\)".*/\1/p' | head -1)
    [ -n "$VERSION_TAG" ] || fatal "Could not determine latest release. Specify a version manually: install.sh v2.4.2"
    VERSION_TAG="${VERSION_TAG#v}"
else
    VERSION_TAG="${VERSION#v}"
fi

info "Installing ai4j CLI version ${VERSION_TAG}..."

# ---- download ---------------------------------------------------------------

JAR_NAME="ai4j-cli-${VERSION_TAG}-jar-with-dependencies.jar"
DOWNLOAD_URL="https://github.com/${REPO}/releases/download/v${VERSION_TAG}/${JAR_NAME}"

mkdir -p "$BIN_DIR"

info "Downloading ${DOWNLOAD_URL}"
if ! curl -fSL --progress-bar "$DOWNLOAD_URL" -o "${BIN_DIR}/ai4j.jar"; then
    fatal "Download failed. Check that version v${VERSION_TAG} exists at https://github.com/${REPO}/releases"
fi

# ---- wrapper script ---------------------------------------------------------

WRAPPER="$BIN_DIR/ai4j"
cat > "$WRAPPER" << EOF
#!/usr/bin/env bash
exec java \$AI4J_JAVA_OPTS -jar "${BIN_DIR}/ai4j.jar" "\$@"
EOF
chmod +x "$WRAPPER"

# ---- done -------------------------------------------------------------------

echo ""
info "ai4j CLI v${VERSION_TAG} installed to ${WRAPPER}"
echo ""
if [[ ":$PATH:" != *":${BIN_DIR}:"* ]]; then
    warn "Add ai4j to your PATH by adding this line to your shell profile (~/.bashrc, ~/.zshrc, etc.):"
    echo ""
    printf '    export PATH="%s:$PATH"\n' "$BIN_DIR"
    echo ""
fi
info "Run 'ai4j --help' to get started."
