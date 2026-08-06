#!/usr/bin/env bash
#
# Copyright 2026 MLorek
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Download Frostlake and every jar it needs from Maven Central into one folder, ready to
# hand to a SQL client (DBeaver, DataGrip, …) as the JDBC driver.
#
# This script needs NOTHING but Maven and a network connection — no repository checkout and
# no build. Its sibling, build-driver-bundle.sh, does the same job from a source tree; use
# that one when you are working on Frostlake itself or want an unreleased version.
#
#   ./download-driver-bundle.sh [OUTDIR] [MODULE...]
#
#   ./download-driver-bundle.sh                                  # driver only
#   ./download-driver-bundle.sh ~/frostlake-driver geo ai        # + geo + Cortex AI
#   ./download-driver-bundle.sh ~/frostlake-driver geo rt-js     # + geo + JavaScript UDFs
#
# Modules: geo, ai, formats, rt-js, rt-py, rt-scala.
#
# Each module's version is resolved independently from Central, so a module that releases on
# its own schedule still lands on its own latest release. Pin one instead with:
#
#   FROSTLAKE_VERSION=0.0.5 ./download-driver-bundle.sh ~/frostlake-driver geo
#
set -euo pipefail

CENTRAL="https://repo1.maven.org/maven2"
GROUP_PATH="dev/frostlake"
OUTDIR="${1:-$HOME/frostlake-driver}"
shift || true
MODULES=("$@")

command -v mvn >/dev/null || { echo "maven (mvn) is required but not on PATH" >&2; exit 1; }

# ── module name -> artifact id ────────────────────────────────────────────────
artifact_for() {
    case "$1" in
        engine)   echo "frostlake-db" ;;
        geo)      echo "frostlake-geo" ;;
        ai)       echo "frostlake-ai" ;;
        formats)  echo "frostlake-formats" ;;
        rt-js)    echo "frostlake-rt-js" ;;
        rt-py)    echo "frostlake-rt-py" ;;
        rt-scala) echo "frostlake-rt-scala" ;;
        *)        return 1 ;;
    esac
}

# The newest release Central lists for an artifact, or nothing when it has never been released.
latest_release() {
    local artifact="$1"
    local metadata
    metadata="$(curl -fsS -m 30 "$CENTRAL/$GROUP_PATH/$artifact/maven-metadata.xml" 2>/dev/null || true)"
    # `|| true` matters twice over: an artifact that has never been released has no metadata to
    # grep, and under `set -o pipefail` that empty grep would otherwise abort the whole script
    # before it could tell you which module is missing.
    echo "$metadata" | grep -o '<release>[^<]*' | head -1 | cut -d'>' -f2 || true
}

# ── resolve every requested module before downloading anything ────────────────
declare -a WANTED_ARTIFACTS=()
declare -a WANTED_VERSIONS=()
declare -a UNAVAILABLE=()

for module in engine "${MODULES[@]}"; do
    artifact="$(artifact_for "$module")" || {
        echo "unknown module: $module (want: geo, ai, formats, rt-js, rt-py, rt-scala)" >&2
        exit 1
    }
    version="${FROSTLAKE_VERSION:-$(latest_release "$artifact")}"
    if [[ -z "$version" ]]; then
        UNAVAILABLE+=("$module ($artifact)")
        continue
    fi
    WANTED_ARTIFACTS+=("$artifact")
    WANTED_VERSIONS+=("$version")
    printf '  %-12s %s:%s\n' "$module" "$artifact" "$version"
done

if [[ ${#UNAVAILABLE[@]} -gt 0 ]]; then
    echo
    echo "Not published to Maven Central yet, so it cannot be downloaded:"
    for entry in "${UNAVAILABLE[@]}"; do
        echo "  - $entry"
    done
    echo "Build it from source instead: ./data/build-driver-bundle.sh $OUTDIR ${MODULES[*]}"
    echo
fi

if [[ ${#WANTED_ARTIFACTS[@]} -eq 0 ]]; then
    echo "nothing to download" >&2
    exit 1
fi

# ── a throwaway pom is the reliable way to pull TRANSITIVE dependencies ───────
# dependency:get fetches one artifact; copy-dependencies over a generated project fetches the
# whole closure, which is what a client classpath actually needs.
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

{
    echo '<?xml version="1.0" encoding="UTF-8"?>'
    echo '<project xmlns="http://maven.apache.org/POM/4.0.0">'
    echo '  <modelVersion>4.0.0</modelVersion>'
    echo '  <groupId>dev.frostlake.bundle</groupId>'
    echo '  <artifactId>frostlake-driver-bundle</artifactId>'
    echo '  <version>1</version>'
    echo '  <packaging>pom</packaging>'
    echo '  <dependencies>'
    for i in "${!WANTED_ARTIFACTS[@]}"; do
        echo '    <dependency>'
        echo '      <groupId>dev.frostlake</groupId>'
        echo "      <artifactId>${WANTED_ARTIFACTS[$i]}</artifactId>"
        echo "      <version>${WANTED_VERSIONS[$i]}</version>"
        echo '    </dependency>'
    done
    echo '  </dependencies>'
    echo '</project>'
} > "$WORKDIR/pom.xml"

echo
echo "Downloading into $OUTDIR …"
rm -rf "$OUTDIR"
mkdir -p "$OUTDIR"

mvn -q -f "$WORKDIR/pom.xml" dependency:copy-dependencies \
    -DoutputDirectory="$OUTDIR" -DincludeScope=runtime

# Test-only and non-runtime artifacts never belong in a client bundle.
rm -f "$OUTDIR"/*-tests.jar "$OUTDIR"/*-sources.jar "$OUTDIR"/*-javadoc.jar

echo
echo "$(ls "$OUTDIR"/*.jar 2>/dev/null | wc -l) jars, $(du -sh "$OUTDIR" | cut -f1)"
echo
echo "In DBeaver: Database > Driver Manager > New, then"
echo "  Class name:   dev.frostlake.jdbc.DatabaseDriver"
echo "  URL template: jdbc:frostlake:file:{file}"
echo "  Libraries > Add Folder: $OUTDIR"
echo
echo "See docs/dbeaver.md for the full walk-through."
