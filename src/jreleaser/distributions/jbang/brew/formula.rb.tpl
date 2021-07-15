class {{brewFormulaName}} < Formula
  desc "{{projectDescription}}"
  homepage "{{projectWebsite}}"
  version "{{projectVersion}}"
  url "{{distributionUrl}}"
  sha256 "{{distributionSha256}}"
  license "{{projectLicense}}"

  bottle :unneeded

  {{#brewDependencies}}
  depends_on {{.}}
  {{/brewDependencies}}

  def install
    libexec.install Dir["*"]
    inreplace "#{libexec}/bin/{{distributionExecutable}}", /^abs_jbang_dir=.*/, "abs_jbang_dir=#{libexec}/bin/jbang.jar"
    bin.install_symlink "#{libexec}/bin/{{distributionExecutable}}"
  end

  test do
      system "#{bin}/{{distributionExecutable}}", "--init=cli", "hello.java"
      system "#{bin}/{{distributionExecutable}}", "hello.java", "Homebrew!"
  end
end
