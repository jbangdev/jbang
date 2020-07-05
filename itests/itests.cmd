setx PATH "..\build\install\jbang\bin;%PATH%"
dir "..\build\install\jbang\bin"
echo %PATH%
where jbang
where jbang.cmd
jbang karate.java -o ..\build\karate *.feature
