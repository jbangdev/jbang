class {{brewFormulaName}} < Formula
  desc "{{projectDescription}}"
  homepage "{{projectWebsite}}"
  version "{{projectVersion}}"
  license "{{projectLicense}}"

  {{brewMultiPlatform}}

  def install
    libexec.install Dir["*"]
    inreplace "#{libexec}/bin/{{distributionExecutable}}", /^abs_jbang_dir=.*/, "abs_jbang_dir=#{libexec}/bin"
    bin.install_symlink "#{libexec}/bin/{{distributionExecutable}}"
  end

  test do
    system({ "JBANG_USE_NATIVE" => "true" }, "#{bin}/{{distributionExecutable}}", "version")
    system "#{bin}/{{distributionExecutable}}", "init", "-t", "cli", "hello.java"
    system "#{bin}/{{distributionExecutable}}", "hello.java", "Homebrew!"
  end
end
