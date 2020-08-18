# JBang Container for Docker and Github Action

This container intended for quick and easily run java based scripts with [jbang](https://jbang.dev).

Can be used directly with docker or as a GitHub Action.

The source is located in [jbangdev/jbang](https://github.com/jbangdev/jbang/blob/master/src/main/scripts/container/) and are updated in this repo on every tag/release of jbangdev/jbang.

[Source](https://github.com/jbangdev/jbang-action)

## Container/Docker usage

[![Docker Repository on Quay.io](https://quay.io/repository/jbangdev/jbang-action/status "Docker Repository on Quay.io")](https://quay.io/repository/jbangdev/jbang-action) [![](https://images.microbadger.com/badges/image/jbangdev/jbang-action.svg)](https://microbadger.com/images/jbangdev/jbang-action "Get your own image badge on microbadger.com") [![nodesource/node](http://dockeri.co/image/jbangdev/jbang-action)](https://registry.hub.docker.com/r/jbangdev/jbang-action)

Using dockerhub images:

```
docker run -v `pwd`:/ws --workdir=/ws jbangdev/jbang-action helloworld.java
```

Using quay.io images:

```
docker run -v `pwd`:/ws --workdir=/ws quay.io/jbangdev/jbang-action helloworld.java
```


## Github Action

### Inputs

Key | Example | Description
----|---------|------------
trust | `https://github.com/maxandersen` | Host pattern to add to be trusted before the script are executed.
jbangargs | `--verbose` | Arguments to pass to jbang before the script.
script | `hello.java` | File, URL or alias referring to script to run
scriptargs | `--token ${GITHUB_TOKEN}` | Arguments to pass to the script. Note: due to how github actions + docker arguments containing spaces gets treated as seperate arguments no matter how much quoting is done. If you need argument with spaces better to extend the docker file and call jbang directly.

### Outputs

### Example usage

Here it is assumed you have a jbang script called `createissue.java` in the root of your project.

```yaml
on: [push]

jobs:
    jbang:
    runs-on: ubuntu-latest
    name: A job to run jbang
    steps:
    - name: checkout
      uses: actions/checkout@v1
    - uses: actions/cache@v1
      with:
        path: /root/.jbang
        key: ${{ runner.os }}-jbang-${{ hashFiles('*.java') }}
        restore-keys: |
            ${{ runner.os }}-jbang-
    - name: jbang
      uses: jbangdev/jbang-action@v@projectVersion@
      with:
        script: createissue.java
        scriptargs: "my world"
      env:
        JBANG_REPO: /root/.jbang/repository
        GITHUB_TOKEN: ${{ secrets.ISSUE_GITHUB_TOKEN }}
```
