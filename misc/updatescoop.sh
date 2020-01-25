#!/usr/bin/env bash
########################################################################
### Update scoop

set -e

jbang_version=`ls build/distributions/jbang-*.*.zip | sed -e 's/.*jbang-\(.*\).zip/\1/g'`
echo "Updating jbang scoop with version $jbang_version from `pwd`"
DIST=`ls build/distributions/jbang-${jbang_version}.zip | cut -f1 -d ' '`
sha256=`cat $DIST.sha256`

rm -rf scoop-bucket
git clone https://github.com/maxandersen/scoop-bucket.git

cp build/scoop/jbang.json scoop-bucket/jbang.json

cd scoop-bucket

git config user.name "Max Rydahl Andersen"
git config user.email "max@xam.dk"


git add jbang.json
git commit -m "jbang v${jbang_version}"

remote_repo="https://${BREW_USER}:${BREW_GITHUB_TOKEN}@github.com/maxandersen/scoop-bucket.git"
echo $remote_repo

git push "${remote_repo}" --follow-tags


## to test use `scoop bucket add https://github.com/maxandersen/scoop-bucket`
# scoop install jbang