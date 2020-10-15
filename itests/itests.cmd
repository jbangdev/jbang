setx PATH "..\build\install\jbang\bin;%PATH%"

rem github seem to be setting this thus trying to ensure it does not affect it here.
set JAVA_TOOL_OPTIONS=

where jbang
where java
echo JAVA_HOME=%JAVA_HOME%

jbang --javaagent=org.jacoco:org.jacoco.agent:0.8.7:runtime=destfile=..\build\jacoco\test.exec karate.java -o ..\build\karate *.feature
