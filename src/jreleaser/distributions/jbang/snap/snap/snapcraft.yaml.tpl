name: {{distributionName}}
version: {{projectVersion}}
summary: {{projectDescription}}
description: {{projectLongDescription}}

grade: {{snapGrade}}
confinement: {{snapConfinement}}
base: {{snapBase}}
type: app

apps:
  {{distributionExecutable}}:
    command: bin/{{distributionExecutable}}

parts:
  {{distributionExecutable}}:
    plugin: dump
    source: {{distributionUrl}}
    source-checksum: sha256/{{distributionChecksumSha256}}
    stage-packages:
      - curl
