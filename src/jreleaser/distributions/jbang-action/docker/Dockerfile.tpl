FROM {{dockerBaseImage}}

{{#dockerLabels}}
LABEL {{.}}
{{/dockerLabels}}
LABEL org.opencontainers.image.source=https://github.com/jbangdev/jbang-action

{{#dockerPreCommands}}
{{.}}
{{/dockerPreCommands}}

COPY assembly/* /

## mkdir of .userPrefs is to fix https://github.com/jbangdev/jbang/issues/1831
RUN jar xf {{distributionArtifactFileName}}{{distributionArtifactFileExtension}} && \
    rm {{distributionArtifactFileName}}{{distributionArtifactFileExtension}} && \
    mv jbang-* jbang && \
    chmod +x jbang/bin/jbang && \
    mkdir -p $HOME/.java/.userPrefs

{{#dockerPostCommands}}
{{.}}
{{/dockerPostCommands}}

ENV PATH="${PATH}:/{{distributionArtifactName}}/bin"

ADD ./entrypoint /bin/entrypoint

ENV SCRIPTS_HOME=/scripts
ENV JBANG_VERSION={{projectVersion}}
ENV JBANG_PATH=/jbang/bin

VOLUME /scripts

ENV PATH="${PATH}:/jbang/bin"

## github action does not allow writing to $HOME thus routing this elsewhere
ENV JBANG_DIR="/jbang/.jbang"

ENTRYPOINT ["entrypoint"]
