FROM {{dockerBaseImage}}

{{#dockerLabels}}
LABEL {{.}}
{{/dockerLabels}}

{{#dockerPreCommands}}
{{.}}
{{/dockerPreCommands}}

COPY assembly/* /

RUN jar xf {{distributionArtifactFileName}}{{distributionArtifactFileExtension}} && \
    rm {{distributionArtifactFileName}}{{distributionArtifactFileExtension}} && \
    mv jbang-* jbang && \
    chmod +x jbang/bin/jbang

{{#dockerPostCommands}}
{{.}}
{{/dockerPostCommands}}

ENV PATH="${PATH}:/{{distributionArtifactName}}/bin"

ADD ./entrypoint /bin/entrypoint

ENV SCRIPTS_HOME /scripts
ENV JBANG_VERSION {{projectVersion}}
ENV JBANG_PATH=/jbang/bin

VOLUME /scripts

ENV PATH="${PATH}:/jbang/bin"

## github action does not allow writing to $HOME thus routing this elsewhere
ENV JBANG_DIR="/jbang/.jbang"

ENTRYPOINT ["entrypoint"]
