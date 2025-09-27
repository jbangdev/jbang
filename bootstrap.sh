## This script is an experiment to bootstrap jbang with jbang itself.

## in gradle we have plugin to generate BuildConfig with version info in it.
## For now just generate a placeholder. Ultimately could be some other script to generate the file.
mkdir -p generated/
cat << EOF > generated/BuildConfig.java
package dev.jbang.util;

public final class BuildConfig {
    public static final String VERSION = "999-jbang";
    public static final String NAME = "jbang";

    private BuildConfig() {
    }
}
EOF

# Use Gradle to get the implementation dependencies in a clean format
# this is done just to avoid having to double maintain the list of dependencies
DEPS=$(./gradlew dependencies --configuration compileClasspath --quiet | \
       grep -E '^[+\\-]---' | \
       sed -E 's/^[+\\-]+---[ \\]*//' | \
       sed -E 's/ -> .*$//' | \
       sed 's/ (\*)//' | \
       grep -E '^[^:]+:[^:]+:[^:]+$' | \
       sort -u | \
       tr '\n' ',' | sed 's/,$//')

jbang export local --force -O jbang.jar --sources src/main/java,src/main/java9,generated --files src/main/resources --repos mavencentral --deps "$DEPS" src/main/java/dev/jbang/Main.java
