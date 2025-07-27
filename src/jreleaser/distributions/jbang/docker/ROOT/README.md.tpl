# Containers for JBang

[![GitHub release badge](https://badgen.net/github/release/jbangdev/jbang-container/stable)](https://github.com/jbangdev/jbang-container/releases/latest)
[![GitHub license badge](https://badgen.net/github/license/jbangdev/jbang-container)]()
[![DockerHub Pulls](https://img.shields.io/docker/pulls/jbangdev/jbang-container)]()

These container are intended for quick and easily run java based application and scripts with [jbang](https://jbang.dev).

This container image is available in several variants, each based on a different Java version (for example, Java 8, 11, 17, etc.). 
You can choose the variant that matches your preferred Java version. If you require a different Java version than the one provided by the base image, 
JBang will automatically download and use the required Java version at runtime.

The variants are named `jbangdev/jbang:v<jbang-version>-java-<java-version>`,
i.e. `jbangdev/jbang:v0.127.18-java-11` will run jbang 0.127.18 with a base image of Java 11.

There is also a 'latest' which will resolve to what uses the latest "LTS" of Java.

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







