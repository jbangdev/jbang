#!/usr/bin/env bash
########################################################################
### Update snap

set -e

jbang_version=`ls build/distributions/jbang-*.*.zip | sed -e 's/.*jbang-\(.*\).zip/\1/g'`
echo "Updating jbang snap with version $jbang_version from `pwd`"
DIST=`ls build/distributions/jbang-${jbang_version}.zip | cut -f1 -d ' '`
sha256=`cat $DIST.sha256`

rm -rf jbang-snap
git clone https://github.com/jbangdev/jbang-snap.git

cp build/snap/snapcraft.yaml jbang-snap/snap

cd jbang-snap

git config user.name "Max Rydahl Andersen"
git config user.email "max@xam.dk"


git add snap/snapcraft.yaml
git commit -m "jbang v${jbang_version}"
git tag -a "v${jbang_version}" -m "jbang v${jbang_version}"

remote_repo="https://${BREW_USER}:${BREW_GITHUB_TOKEN}@github.com/jbangdev/jbang-snap.git"
echo $remote_repo

git push "${remote_repo}" --follow-tags


## to test use `sudo snap install jbang --edge --classic`