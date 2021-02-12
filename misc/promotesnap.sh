#!/usr/bin/env bash
########################################################################
### Release snap

set -e

jbang_version=`ls build/distributions/jbang-*.*.zip | sed -e 's/.*jbang-\(.*\).zip/\1/g'`
echo "Promoting jbang snap with version $jbang_version from `pwd`"

snapcraft list-revisions jbang | grep ${jbang_version} | awk '{print $1}' | tac | xargs -n1 -I {} snapcraft release jbang {} latest/stable

## to test use `sudo snap install jbang`