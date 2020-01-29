#!/usr/bin/env bash
########################################################################
### Update docker

set -e

jbang_version=`ls build/distributions/jbang-*.*.zip | sed -e 's/.*jbang-\(.*\).zip/\1/g'`
echo "Updating jbang docker with version $jbang_version from `pwd`"
DIST=`ls build/distributions/jbang-${jbang_version}.zip | cut -f1 -d ' '`
sha256=`cat $DIST.sha256`

rm -rf jbang-action
git clone https://github.com/maxandersen/jbang-action.git

cp build/container/Dockerfile jbang-action/Dockerfile
cp build/container/README.md jbang-action/README.md

cd jbang-action

git config user.name "Max Rydahl Andersen"
git config user.email "max@xam.dk"


git add Dockerfile README.md
git commit -m "jbang v${jbang_version}"
git tag "v${jbang_version}"

remote_repo="https://${BREW_USER}:${BREW_GITHUB_TOKEN}@github.com/maxandersen/jbang-action.git"
echo $remote_repo

git push "${remote_repo}" --follow-tags


