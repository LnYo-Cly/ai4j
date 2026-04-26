#!/usr/bin/env sh
set -eu

AI4J_HOME="${AI4J_HOME:-$HOME/.ai4j}"
AI4J_BIN_DIR="$AI4J_HOME/bin"
AI4J_LIB_DIR="$AI4J_HOME/lib"
AI4J_VERSION_FILE="$AI4J_HOME/version.txt"
MAVEN_REPO="${AI4J_MAVEN_REPO:-https://repo.maven.apache.org/maven2}"
METADATA_URL="$MAVEN_REPO/io/github/lnyo-cly/ai4j-cli/maven-metadata.xml"

say() {
  printf '%s\n' "$*"
}

fail() {
  printf 'ai4j installer: %s\n' "$*" >&2
  exit 1
}

have_cmd() {
  command -v "$1" >/dev/null 2>&1
}

skip_path_update() {
  case "${AI4J_SKIP_PATH_UPDATE:-}" in
    1|true|TRUE|yes|YES)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

download_to() {
  url="$1"
  output="$2"
  if have_cmd curl; then
    curl -fsSL "$url" -o "$output"
    return
  fi
  if have_cmd wget; then
    wget -qO "$output" "$url"
    return
  fi
  fail "curl or wget is required"
}

download_text() {
  url="$1"
  if have_cmd curl; then
    curl -fsSL "$url"
    return
  fi
  if have_cmd wget; then
    wget -qO- "$url"
    return
  fi
  fail "curl or wget is required"
}

resolve_version() {
  if [ -n "${AI4J_VERSION:-}" ]; then
    printf '%s' "$AI4J_VERSION"
    return
  fi

  metadata="$(download_text "$METADATA_URL" | tr -d '\r\n')"
  version="$(printf '%s' "$metadata" | sed -n 's:.*<release>\([^<]*\)</release>.*:\1:p')"
  if [ -z "$version" ]; then
    version="$(printf '%s' "$metadata" | sed -n 's:.*<latest>\([^<]*\)</latest>.*:\1:p')"
  fi
  if [ -z "$version" ]; then
    fail "unable to resolve latest ai4j-cli version from Maven metadata"
  fi
  printf '%s' "$version"
}

java_major_version() {
  version="$(
    java -version 2>&1 \
      | awk -F '"' '/version/ {print $2; exit}'
  )"
  if [ -z "$version" ]; then
    fail "unable to detect Java version"
  fi
  case "$version" in
    1.*)
      printf '%s' "$version" | cut -d. -f2
      ;;
    *)
      printf '%s' "$version" | cut -d. -f1
      ;;
  esac
}

ensure_java() {
  if ! have_cmd java; then
    fail "Java 8+ is required. Install Java first, then rerun this installer."
  fi
  major="$(java_major_version)"
  if [ "$major" -lt 8 ]; then
    fail "Java 8+ is required. Current Java major version: $major"
  fi
}

write_launcher() {
  launcher="$AI4J_BIN_DIR/ai4j"
  install_home_escaped="$(printf '%s' "$AI4J_HOME" | sed "s/'/'\\\\''/g")"
  {
    printf '%s\n' '#!/usr/bin/env sh'
    printf '%s\n' 'set -eu'
    printf '\n'
    printf "INSTALL_HOME='%s'\n" "$install_home_escaped"
    cat <<'EOF'
AI4J_HOME="${AI4J_HOME:-$INSTALL_HOME}"
JAVA_BIN="${AI4J_JAVA:-java}"
JAR_PATH="$AI4J_HOME/lib/ai4j-cli.jar"

if [ ! -f "$JAR_PATH" ]; then
  printf 'ai4j launcher: missing %s\n' "$JAR_PATH" >&2
  exit 1
fi

if [ -n "${AI4J_JAVA_OPTS:-}" ]; then
  # shellcheck disable=SC2086
  exec "$JAVA_BIN" $AI4J_JAVA_OPTS -jar "$JAR_PATH" "$@"
fi

exec "$JAVA_BIN" -jar "$JAR_PATH" "$@"
EOF
  } > "$launcher"
  chmod +x "$launcher"
}

path_contains() {
  case ":$PATH:" in
    *":$1:"*) return 0 ;;
    *) return 1 ;;
  esac
}

ensure_path() {
  if skip_path_update; then
    say "Skipping PATH update because AI4J_SKIP_PATH_UPDATE is set."
    return
  fi

  if path_contains "$AI4J_BIN_DIR"; then
    say "ai4j is already available on PATH in this shell."
    return
  fi

  shell_name="${SHELL:-}"
  case "$shell_name" in
    */zsh) rc_file="$HOME/.zshrc" ;;
    */bash) rc_file="$HOME/.bashrc" ;;
    *) rc_file="$HOME/.profile" ;;
  esac

  export_line="export PATH=\"$AI4J_BIN_DIR:\$PATH\""
  if [ -f "$rc_file" ] && grep -F "$export_line" "$rc_file" >/dev/null 2>&1; then
    say "PATH entry already present in $rc_file"
    return
  fi

  {
    printf '\n# ai4j installer\n'
    printf '%s\n' "$export_line"
  } >> "$rc_file"
  say "Added $AI4J_BIN_DIR to PATH in $rc_file"
  say "Run: export PATH=\"$AI4J_BIN_DIR:\$PATH\""
}

main() {
  ensure_java

  version="$(resolve_version)"
  jar_url="$MAVEN_REPO/io/github/lnyo-cly/ai4j-cli/$version/ai4j-cli-$version-jar-with-dependencies.jar"
  tmp_jar="$AI4J_LIB_DIR/ai4j-cli.jar.tmp"
  jar_path="$AI4J_LIB_DIR/ai4j-cli.jar"

  say "Installing ai4j-cli $version"
  mkdir -p "$AI4J_BIN_DIR" "$AI4J_LIB_DIR"
  download_to "$jar_url" "$tmp_jar"
  mv "$tmp_jar" "$jar_path"
  printf '%s\n' "$version" > "$AI4J_VERSION_FILE"
  write_launcher
  ensure_path

  say ""
  say "Installed ai4j-cli $version to $AI4J_HOME"
  say "Restart your shell if 'ai4j' is not found immediately."
  say "Then run: ai4j --help"
}

main "$@"
