export PATH=`realpath ../build/install/jbang/bin`:$PATH

## github seem to be setting this thus trying to ensure it does not affect it here.
unset JAVA_TOOL_OPTIONS

jbang --javaagent=jacocoagent.jar=destfile=../build/jacoco/test.exec ./karate.java -o ../build/karate *.feature
