## Markdown Scripts

It is possible to write scripts using markdown.

JBang will extract code found in `java` or `jsh` or `jshelllanguage` code blocks.

Try run `jbang readme.md`.

```java
class Demo {
	void test() {
		System.out.println("Hello, World!");
	}
}
```

It will take all blocks and execute via jshell by default and if main method found it will treat it as a .java file.

```jshelllanguage
new Demo().test();
```

You can of course also use `//DEPS` in the code blocks.

```jsh
//DEPS com.github.lalyos:jfiglet:0.0.8
import com.github.lalyos.jfiglet.FigletFont;

System.out.println(FigletFont.convertOneLine(
			"Hello " + ((args.length>0)?args[0]:"jbang")));
```

Oh, and did you notice it handled arguments too?

```java
if(args.length==0) {
	System.out.println("You have no arguments!");
} else {
System.out.printf("You have %s arguments! First is %s", args.length, args[0]);
}
```
