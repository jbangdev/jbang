# Testing with a proxy

You can use the `docker-compose.yml` file in this folder to test how
JBang behaves on a system that only has access to the Internet via a
proxy.

To do this you need to have `docker-compose` installed and then from
this folder you can run the command:

```
docker-compose up -d tinyproxy
```

Which will start up a proxy server on an internal container network.
When that's done you can run a container that has JBang pre-installed:

```
docker-compose run --rm jbang
```

You'll now have access to a shell in a container that can only access
the Internet via the proxy at the URL `tinyproxy:8888`. Environment
variables have already been set for Linux itself so `curl` will work
just fine. Likewise `~/.m2/settings.xml` is setup so that Maven will
honor the proxy settings too.

Setting the proxy options for Java requires a bit more work and doesn't
always work. One way is to inherit the settings from the OS, like this:

```
export JAVA_TOOL_OPTIONS="-Djava.net.useSystemProxies=true"
```

The other option is to set it explicitly:

```
export JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=tinyproxy -Dhttp.proxyPort=8888 -Dhttps.proxyHost=tinyproxy -Dhttps.proxyPort=8888"
```
