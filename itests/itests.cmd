setx PATH "..\build\install\jbang\bin;%PATH%"
where jbang
jbang karate.java -o ..\build\karate *.feature
