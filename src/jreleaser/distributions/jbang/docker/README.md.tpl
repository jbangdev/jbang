# JBang Container for Docker and Github Action

[![GitHub release badge](https://badgen.net/github/release/jbangdev/jbang-container/stable)](https://github.com/jbangdev/jbang-container/releases/latest)
[![GitHub license badge](https://badgen.net/github/license/jbangdev/jbang-container)]()
[![DockerHub Pulls](https://img.shields.io/docker/pulls/jbangdev/jbang-container)]()

This container intended for quick and easily run java based scripts with [jbang](https://jbang.dev).

The source is located in [jbangdev/jbang](https://github.com/jbangdev/jbang/blob/HEAD/src/jreleaser/distributions/jbang/docker/) and are updated in this repo on every tag/release of jbangdev/jbang.


[Source](https://github.com/jbangdev/jbang-container)

## Container/Docker usage

Using dockerhub images:

```
docker run -v `pwd`:/ws --workdir=/ws jbangdev/jbang helloworld.java
```

Using quay.io images:

```
docker run -v `pwd`:/ws --workdir=/ws quay.io/jbangdev/jbang helloworld.java
```


## Environments

| Key   | Description                                 |
|-------|---------------------------------------------|
| TRUST | Path or URL to be trusted by the container. |

**TRUST**:  
If you need to trust a script, dependency, or domain before running a jbang command, set the `TRUST` environment variable to the path or URL you want to trust.  
For example:







