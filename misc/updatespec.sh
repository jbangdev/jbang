#!/usr/bin/env bash
########################################################################
### Update docker

set -e

jbang_version=`ls build/distributions/jbang-*.*.zip | sed -e 's/.*jbang-\(.*\).zip/\1/g'`
echo "Updating jbang spec with version $jbang_version from `pwd`"
DIST=`ls build/distributions/jbang-${jbang_version}.zip | cut -f1 -d ' '`
sha256=`cat $DIST.sha256`

rm -rf jbang-spec
git clone https://github.com/jbangdev/jbang-spec.git

cp build/spec/jbang.spec jbang-spec/jbang.spec
cd jbang-spec

git config user.name "Max Rydahl Andersen"
git config user.email "max@xam.dk"


git add jbang.spec
git commit -m "jbang v${jbang_version}"
git tag -a "v${jbang_version}" -m "jbang v${jbang_version}"

remote_repo="https://${BREW_USER}:${BREW_GITHUB_TOKEN}@github.com/jbangdev/jbang-spec.git"
echo $remote_repo

git push "${remote_repo}" --follow-tags


