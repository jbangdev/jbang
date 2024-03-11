# {{jreleaserCreationStamp}}
FROM {{dockerBaseImage}}

{{#dockerLabels}}
LABEL {{.}}
{{/dockerLabels}}

{{#dockerPreCommands}}
{{.}}
{{/dockerPreCommands}}

COPY assembly/ /

RUN jar xf {{distributionArtifactFileName}}{{distributionArtifactFileExtension}} && \
    rm {{distributionArtifactFileName}}{{distributionArtifactFileExtension}} && \
    chmod +x {{distributionArtifactRootEntryName}}/bin/{{distributionExecutableUnix}}
    
{{#dockerPostCommands}}
{{.}}
{{/dockerPostCommands}}

ENV PATH="${PATH}:/{{distributionArtifactRootEntryName}}/bin"

ENTRYPOINT ["/{{distributionArtifactRootEntryName}}/bin/{{distributionExecutableUnix}}"]