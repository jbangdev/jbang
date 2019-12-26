########################################################################
### Update homebrew

jbang_version=`ls build/distributions/jbang-*.*.zip | sed -e 's/.*jbang-\(.*\).zip/\1/g'`
echo "Updating jbang brew with version $jbang_version"
DIST=`ls build/distributions/jbang-${jbang_version}.zip | cut -f1 -d ' '`
sha256=`cat $DIST.sha256`

rm -rf homebrew-tap
git clone https://github.com/maxandersen/homebrew-tap.git
cd homebrew-tap

git config user.name "Max Rydahl Andersen"
git config user.email "max@xam.dk"

cat - <<EOF > Formula/jbang.rb
class JBang < Formula
  desc "jbang"
  homepage "https://github.com/maxandersen/jbang"
  url "https://github.com/maxandersen/jbang/releases/download/v${jbang_version}/jbang-${jbang_version}.zip"
  sha256 "${sha256}"

  def install
    libexec.install Dir["*"]
    inreplace "#{libexec}/bin/kscript", /^jarPath=.*/, "jarPath=#{libexec}/bin/jbang.jar"
    bin.install_symlink "#{libexec}/bin/jbang"
  end
end
EOF

git add Formula/jbang.rb
git commit -m "jbang v${jbang_version}"

remote_repo="https://${GITHUB_ACTOR}:${INPUT_GITHUB_TOKEN}@github.com/maxandersen/homebrew-tap.git"

git push "${remote_repo}" --follow-tags


## to test use `brew install holgerbrandl/tap/kscript`

