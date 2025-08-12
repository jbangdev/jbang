# {{jreleaserCreationStamp}}
FROM {{dockerBaseImage}}

{{#dockerLabels}}
LABEL {{.}}
{{/dockerLabels}}
LABEL org.opencontainers.image.source=https://github.com/jbangdev/jbang-container

{{#dockerPreCommands}}
{{.}}
{{/dockerPreCommands}}

COPY assembly/ /

## mkdir of .userPrefs is to fix https://github.com/jbangdev/jbang/issues/1831
RUN jar xf {{distributionArtifactFileName}}{{distributionArtifactFileExtension}} && \
    rm {{distributionArtifactFileName}}{{distributionArtifactFileExtension}} && \
    chmod +x {{distributionArtifactRootEntryName}}/bin/{{distributionExecutableUnix}} && \
    mkdir -p $HOME/.java/.userPrefs

    
{{#dockerPostCommands}}
{{.}}
{{/dockerPostCommands}}

VOLUME /workspace
ADD ./entrypoint /bin/entrypoint

ENV PATH="${PATH}:/{{distributionArtifactRootEntryName}}/bin"

ENTRYPOINT ["entrypoint"]