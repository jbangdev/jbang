# J'Bang Container for Docker and Github Action

This container intended for quick and easily run java based scripts with [jbang](https://jbang.dev).

Can be used directly with docker or as a GitHub Action

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

### Outputs

### Example usage

Here it is assumed you have a jbang script called `createissue.java` in the root of your project.

```
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
		args: "my world"
		env:
		JBANG_REPO: /root/.jbang/repository
		GITHUB_TOKEN: ${{ secrets.ISSUE_GITHUB_TOKEN }}
```
