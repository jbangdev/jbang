FROM adoptopenjdk:16-jdk-hotspot

RUN curl -Ls "https://github.com/jbangdev/jbang/releases/download/v@projectVersion@/jbang-@projectVersion@.zip" --output jbang.zip \
              && jar xf jbang.zip && rm jbang.zip && mv jbang-* jbang && chmod +x jbang/bin/jbang

ADD ./entrypoint /bin/entrypoint

ENV SCRIPTS_HOME /scripts
ENV JBANG_VERSION @projectVersion@

# Needed for secure run on openshift but breaks github actions
# removed until can find better alternative
# RUN useradd -u 10001 -r -g 0 -m \
#      -d ${SCRIPTS_HOME} -s /sbin/nologin -c "jbang user" jo \
#    && chmod -R g+w /scripts \
#    && chmod -R g+w /jbang \
#    && chgrp -R root /scripts \
#    && chgrp -R root /jbang \
#    && chmod g+w /etc/passwd \
#    && chmod +x /bin/entrypoint

VOLUME /scripts

# USER 10001

ENV PATH="${PATH}:/jbang/bin"

## github action does not allow writing to $HOME thus routing this elsewhere
ENV JBANG_DIR="/jbang/.jbang"

ENTRYPOINT ["entrypoint"]