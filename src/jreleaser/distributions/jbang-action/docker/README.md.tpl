# JBang Container for Docker and Github Action

[![GitHub release badge](https://badgen.net/github/release/jbangdev/jbang-action/stable)](https://github.com/jbangdev/jbang-action/releases/latest)
[![GitHub license badge](https://badgen.net/github/license/jbangdev/jbang-action)]()
[![GitHub Workflows badge](https://badgen.net/runkit/maxandersen/61b3c9809073c8000ae9b210)](https://github.com/search?q=jbang-action+language%3AYAML+language%3AYAML+path%3A.github%2Fworkflows&type=Code&ref=advsearch&l=&l=)
[![DockerHub Pulls](https://img.shields.io/docker/pulls/jbangdev/jbang-action)]()

This container intended for quick and easily run java based scripts with [jbang](https://jbang.dev).

Can be used directly with docker or as a GitHub Action.

The source is located in [jbangdev/jbang](https://github.com/jbangdev/jbang/blob/HEAD/src/jreleaser/distributions/jbang/docker/) and are updated in this repo on every tag/release of jbangdev/jbang.


[Source](https://github.com/jbangdev/jbang-action)

## Container/Docker usage

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
scriptargs | `--token ${GITHUB_TOKEN}` | Arguments to pass to the script. Note: due to how github actions + docker arguments containing spaces gets treated as separate arguments no matter how much quoting is done. If you need argument with spaces better to extend the docker file and call jbang directly.

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
      uses: jbangdev/jbang-action@{{tagName}}
      with:
        script: createissue.java
        scriptargs: "my world"
      env:
        JBANG_REPO: /root/.jbang/repository
        GITHUB_TOKEN: ${{ secrets.ISSUE_GITHUB_TOKEN }}
```
