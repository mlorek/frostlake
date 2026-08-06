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
# Assemble a folder of jars to hand to a SQL client (DBeaver, DataGrip, …) as the
# Frostlake JDBC driver, including whichever optional modules you ask for.
#
#   ./data/build-driver-bundle.sh [OUTDIR] [MODULES...]
#
#   ./data/build-driver-bundle.sh                       # engine only          (~10 MB)
#   ./data/build-driver-bundle.sh ~/frostlake-driver geo ai        # + geo + Cortex AI
#   ./data/build-driver-bundle.sh ~/frostlake-driver geo ai rt-js rt-py   # everything (~180 MB)
#
# Module names are the Maven module directories: geo, ai, rt-js, rt-py, rt-scala, formats.
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTDIR="${1:-$HOME/frostlake-driver}"
shift || true
MODULES=("$@")

echo "Building Frostlake ${MODULES[*]:-(engine only)} -> $OUTDIR"
rm -rf "$OUTDIR"
mkdir -p "$OUTDIR"

cd "$HERE"
mvn -q install -DskipTests

for m in engine "${MODULES[@]}"; do
    if [[ ! -d "$m" ]]; then
        echo "no such module: $m" >&2
        exit 1
    fi
    mvn -q -pl "$m" dependency:copy-dependencies \
        -DoutputDirectory="$OUTDIR" -DincludeScope=runtime >/dev/null
    cp "$m"/target/frostlake-*.jar "$OUTDIR"/ 2>/dev/null || true
done

# Test-only and non-runtime artifacts never belong in a client bundle.
rm -f "$OUTDIR"/*-tests.jar "$OUTDIR"/*-sources.jar "$OUTDIR"/*-javadoc.jar

echo
echo "$(ls "$OUTDIR"/*.jar | wc -l) jars, $(du -sh "$OUTDIR" | cut -f1)"
echo
echo "In DBeaver: Database > Driver Manager > New, then"
echo "  Class name: dev.frostlake.jdbc.DatabaseDriver"
echo "  URL template: jdbc:frostlake:file:{file}"
echo "  Libraries > Add Folder: $OUTDIR"
