
jbang_version=`ls build/distributions/jbang-*.*.zip | sed -e 's/.*jbang-\(.*\).zip/\1/g'`
echo "Updating jbang sdkman with version $jbang_version"


echo ${SDKMAN_CONSUMER_KEY} ${SDKMAN_CONSUMER_TOKEN} ${kscript_version}
#echo ${SDKMAN_CONSUMER_KEY} | cut -c-5
#echo ${SDKMAN_CONSUMER_TOKEN} | cut -c-5

curl -X POST \
    -H "Consumer-Key: ${SDKMAN_CONSUMER_KEY}" \
    -H "Consumer-Token: ${SDKMAN_CONSUMER_TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"candidate": "jbang", "version": "'${jbang_version}'", "url": "https://github.com/maxandersen/jbang/releases/download/v
    https://vendors.sdkman.io/release

## Set existing Version as Default for Candidate

curl -X PUT \
    -H "Consumer-Key: ${SDKMAN_CONSUMER_KEY}" \
    -H "Consumer-Token: ${SDKMAN_CONSUMER_TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"candidate": "jbang", "version": "'${jbang_version}'"}' \
    https://vendors.sdkman.io/default

## Broadcast a Structured Message
curl -X POST \
    -H "Consumer-Key: ${SDKMAN_CONSUMER_KEY}" \
    -H "Consumer-Token: ${SDKMAN_CONSUMER_TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"candidate": "jbang", "version": "'${jbang_version}'", "hashtag": "jbang"}' \
    https://vendors.sdkman.io/announce/struct