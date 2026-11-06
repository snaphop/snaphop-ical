#!/bin/bash
# Deploys the checked out version to maven.snaphop.com from this machine. Normally the
# deploy workflow does this (on vX.Y.Z tags, or manually for snapshots); this script is
# the fallback. Requires NEXUS_SNAPHOP_COM_PASSWORD.
#
# Usage: bin/deploy.sh [extra maven args]   e.g. bin/deploy.sh -DskipTests
set -euo pipefail

cd "$(dirname "$0")/.."

[[ -n "${NEXUS_SNAPHOP_COM_PASSWORD:-}" ]] || {
  echo "deploy.sh: NEXUS_SNAPHOP_COM_PASSWORD is not set" >&2
  exit 1
}

version=$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout -N)
profiles=""
if [[ "$version" != *-SNAPSHOT ]]; then
  profiles="-Prelease"
fi

echo "Deploying $version"
./mvnw -s .snaphop/settings.xml --batch-mode $profiles clean deploy "$@"
