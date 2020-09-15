class Jbang < Formula
    desc "Unleash the power of Java for shell scripting"
    homepage "https://jbang.dev"
    url "https://github.com/jbangdev/jbang/releases/download/v@projectVersion@/jbang-@projectVersion@.zip"
    sha256 "@sha256@"

    bottle :unneeded

    keg_only :versioned_formula

    #depends_on cask:"java"
    depends_on :java => "1.8+"

    def install
      libexec.install Dir["*"]
      inreplace "#{libexec}/bin/jbang", /^abs_jbang_path=.*/, "abs_jbang_path=#{libexec}/bin/jbang.jar"
      bin.install_symlink "#{libexec}/bin/jbang"
    end

    test do
        system "#{bin}/jbang", "--init=cli", "hello.java"
        system "#{bin}/jbang", "hello.java", "Homebrew!"
    end
  end