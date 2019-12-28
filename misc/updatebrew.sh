########################################################################
### Update homebrew

set -e

jbang_version=`ls build/distributions/jbang-*.*.zip | sed -e 's/.*jbang-\(.*\).zip/\1/g'`
echo "Updating jbang brew with version $jbang_version"
DIST=`ls build/distributions/jbang-${jbang_version}.zip | cut -f1 -d ' '`
sha256=`cat $DIST.sha256`

rm -rf homebrew-tap
git clone https://github.com/maxandersen/homebrew-tap.git

cp build/brew/formula/jbang.rb Formula/jbang.rb

cd homebrew-tap

git config user.name "Max Rydahl Andersen"
git config user.email "max@xam.dk"


git add Formula/jbang.rb
git commit -m "jbang v${jbang_version}"

remote_repo="https://${BREW_USER}:${BREW_GITHUB_TOKEN}@github.com/maxandersen/homebrew-tap.git"
echo $remote_repo

git push "${remote_repo}" --follow-tags


## to test use `brew install maxandersen/tap/jbang`

