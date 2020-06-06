#!/usr/bin/env bash

# In OpenShift, containers are run as a random high number uid
# that doesn't exist in /etc/passwd
if [ `id -u` -ge 500 ] || [ -z "${CURRENT_UID}" ]; then

  cat << EOF > /tmp/passwd
root:x:0:0:root:/root:/bin/bash
jbang:x:`id -u`:`id -g`:,,,:/scripts:/bin/bash
EOF

  cat /tmp/passwd > /etc/passwd
  rm /tmp/passwd
fi

exec jbang "${@}"