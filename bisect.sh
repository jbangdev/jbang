## adjust the test.java and this script to use in bisect to
## find when something stopped/started working.
## git co knownbadcommit
## git bisect start
## git bisect bad
## git co knowngoodcommit
## git bisect run ./bisect.sh
## git bisect reset
##
## see details at https://stackoverflow.com/a/22592593/71208
./gradlew clean build installDist -x test
export PATH=/Users/max/code/personal/jbangdev/jbang/build/install/jbang/bin:$PATH
rm -f mode.quarkus
jbang --fresh -Dquarkus.dev test.java
grep DEVELOPMENT mode.quarkus

