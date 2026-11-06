#!/bin/bash
# Releases the current X.Y.Z-SNAPSHOT version as X.Y.Z:
#   1. sets the version to X.Y.Z and runs the build (all tests) with the release profile
#   2. commits "Release X.Y.Z" and tags it vX.Y.Z
#   3. bumps to X.Y.(Z+1)-SNAPSHOT and commits
#   4. pushes main and the tag; the tag push runs .github/workflows/deploy-snaphop.yml
#      which deploys vX.Y.Z to maven.snaphop.com
#
# Usage: bin/release.sh [releaseVersion]
#   SKIP_VERIFY=true bin/release.sh   skip the local build (the deploy workflow still runs it)
#   NO_PUSH=true bin/release.sh       do everything locally but do not push
set -euo pipefail

cd "$(dirname "$0")/.."

fail() {
  echo "release.sh: $*" >&2
  exit 1
}

[[ -z "$(git status --porcelain)" ]] || fail "working tree is not clean"
[[ "$(git rev-parse --abbrev-ref HEAD)" == "main" ]] || fail "releases are made from main"
git fetch --quiet origin main
[[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || fail "main is not in sync with origin/main"

current=$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout -N)
[[ "$current" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)-SNAPSHOT$ ]] || fail "current version $current is not X.Y.Z-SNAPSHOT"

release="${1:-${current%-SNAPSHOT}}"
[[ "$release" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || fail "release version $release is not X.Y.Z"
next="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.$((BASH_REMATCH[3] + 1))-SNAPSHOT"
tag="v$release"

git rev-parse -q --verify "refs/tags/$tag" >/dev/null && fail "tag $tag already exists"

echo "Releasing $release (current $current, next $next)"

./mvnw -q versions:set -DnewVersion="$release"
if [[ "${SKIP_VERIFY:-false}" != "true" ]]; then
  ./mvnw -s .snaphop/settings.xml -Prelease --batch-mode clean verify
fi
git commit -q -am "Release $release"
git tag -a "$tag" -m "Release $release"

./mvnw -q versions:set -DnewVersion="$next"
git commit -q -am "Prepare $next"

if [[ "${NO_PUSH:-false}" == "true" ]]; then
  echo "Not pushing. When ready: git push --atomic origin main $tag"
  exit 0
fi
git push --atomic origin main "$tag"
echo "Pushed $tag. The 'Deploy to SnapHop' workflow deploys it to maven.snaphop.com."
