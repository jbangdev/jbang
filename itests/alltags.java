
//REPOS dummy=http://dummy
//DEPS dummy.org:dummy:1.2.3

//SOURCES Two.java
//SOURCES nested/*.java

//FILES res/resource.properties renamed.properties=res/resource.properties
//FILES META-INF/application.properties=res/resource.properties

//DOCS javadoc=readme.md
//DOCS readme.adoc

//COMPILE_OPTIONS --enable-preview --verbose
//RUNTIME_OPTIONS --add-opens java.base/java.net=ALL-UNNAMED
//NATIVE_OPTIONS -O1 -d

//JAVA 11+
//MAIN mainclass
//MODULE mymodule
//DESCRIPTION some description
//GAV example.org:alltags:1.2.3
//CDS
//PREVIEW
//MANIFEST one=1 two=2 three=3
//JAVAAGENT

public class alltags {
    public static void main(String... args) {
    }
}
