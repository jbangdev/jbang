setx PATH "..\build\install\jbang\bin;%PATH%"

## github seem to be setting this thus trying to ensure it does not affect it here.
set JAVA_TOOL_OPTIONS=

where jbang
jbang karate.java -o ..\build\karate *.feature
