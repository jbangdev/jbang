set -e
cd ..
./gradlew build install
cd -
export PATH=`realpath ../build/install/jbang/bin`:$PATH
jbang ./karate.java -o ../build/karate *.feature
