Name:       jbang
Version:    @projectVersion@
Release:    1%{?dist}
Summary:    Unleash the power of Java

License:    MIT
URL:        https://github.com/jbangdev/%{name}
Source0:    https://github.com/jbangdev/%{name}/releases/download/v%{version}/%{name}-%{version}.tar

BuildArch:      noarch
BuildRequires:  git
Requires: java

%description
Unleash the power of Java

Want to learn or explore Java instantly without setup ?

Do you like Java but uses python, groovy, kotlin or
similar languages for your scripts ?

Ever tried out Java 10+ support for running .java files
directly in your shell but felt it was a bit too cumbersome ?

Then try jbang which lets you do this:

    $ jbang --init=cli hello.java
    $ jbang hello.java Max!
    [jbang] Resolving dependencies...
    [jbang]     Resolving info.picocli:picocli:4.6.3...Done
    [jbang] Dependencies resolved
    [jbang] Building jar...
    Hello Max!
    $ jbang hello.java -h
    Usage: hello [-hV] <greeting>
    hello made with jbang
          <greeting>   The greeting to print
      -h, --help       Show this help message and exit.
      -V, --version    Print version information and exit.

Instant cli app generated built using java and picocli as a dependency that was fetched as needed for the compilation and execution.

%prep
%autosetup -S git


%install
#mkdir -p %{buildroot}%{_javadir}
mkdir -p %{buildroot}%{_bindir}
install -p -m 644 bin/%{name}.jar %{buildroot}%{_bindir}/%{name}.jar
install -p -m 755 bin/%{name} %{buildroot}%{_bindir}/%{name}

%files
%{_bindir}/jbang.jar
%{_bindir}/jbang