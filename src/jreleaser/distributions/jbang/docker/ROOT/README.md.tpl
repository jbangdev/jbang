# Containers for JBang

[![GitHub release badge](https://badgen.net/github/release/jbangdev/jbang-container/stable)](https://github.com/jbangdev/jbang-container/releases/latest)
[![GitHub license badge](https://badgen.net/github/license/jbangdev/jbang-container)]()
[![DockerHub Pulls](https://img.shields.io/docker/pulls/jbangdev/jbang-container)]()

JBang container images provide a simple way to run Java applications and scripts using [jbang](https://jbang.dev) without needing to install Java or JBang on your local machine. These containers are designed for quick, reliable execution of Java code in a consistent environment.

### Java Version Variants

Each container image is available in several variants, each based on a different Java version (for example, Java 8, 11, 17, etc.). This lets you choose the image that matches the Java version your application or script needs. If your script requires a different Java version than the one provided by the base image, JBang will automatically download and use the required Java version at runtime.

**Image naming convention:**  
Images are tagged as `jbangdev/jbang:<jbang-version>-java-<java-version>`.  
For example, `jbangdev/jbang:0.127.18-java-11` runs JBang version 0.127.18 on a base image with Java 11.

There is also a `latest` tag, which always points to the most recent JBang release using the latest Java LTS (Long Term Support) version.

### Working Directory

By default, the container uses `/workspace` as its working directory. When running the container, you should mount your local project or script directory to `/workspace` inside the container. This allows you to easily access and run your local scripts or Java files. For example, you can use `-v $(pwd):/workspace` with `docker run` to make your current directory available inside the container.

### Source Code

The source for these container images is maintained in [jbangdev/jbang](https://github.com/jbangdev/jbang/blob/HEAD/src/jreleaser/distributions/jbang/docker/) and is updated in this repository on every tag or release of jbangdev/jbang.

For more information, see the [jbangdev/jbang-container](https://github.com/jbangdev/jbang-container) repository.

### Container Registries

JBang container images are published to several container registries, so you can pull them from the one that best fits your needs:

- **docker.io** – The default registry for most container runners. May require login for higher pull limits.
- **quay.io** – Often usable without logins or rate limits.
- **gcr.io** – Requires authentication; mainly used in GitHub Actions where authentication is handled automatically.

## Container/Docker usage

Using dockerhub images:

```
docker run -v `pwd`:/workspace jbangdev/jbang helloworld.java
```

Using quay.io images:

```
docker run -v `pwd`:/workspace quay.io/jbangdev/jbang helloworld.java
```

## Environments

| Key   | Description                                 |
|-------|---------------------------------------------|
| TRUST | Path or URL to be trusted by the container. |

**TRUST**:  
If you need to trust a script, dependency, or domain before running a jbang command, set the `TRUST` environment variable to the path or URL you want to trust.  
For example to run Quarkus CLI directly:

```
docker run -e TRUST=https://repo1.maven.org/maven2/io/quarkus/quarkus-cli/ jbangdev/jbang quarkus@quarkusio
```








