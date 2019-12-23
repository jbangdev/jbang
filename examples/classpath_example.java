//#!/usr/bin/env jbang

//DEPS com.offbytwo:docopt:0.6.0.20150202,log4j:log4j:1.2.14

import org.docopt.Docopt;
import java.io.File;
import java.util.*;
import static java.lang.System.*;


class classpath_example {

	static String usage = "jbang  - Enhanced scripting support for Java on *nix-based systems.\n" + "\n" + "Usage:\n"
			+ "    jbang ( -t | --text ) <version>\n"
			+ "    jbang [ --interactive | --idea | --package ] [--] ( - | <file or URL> ) [<args>]...\n"
			+ "    jbang (-h | --help)\n" + "\n" + "Options:\n"
			+ "    -t, --text         Enable stdin support API for more streamlined text processing  [default: latest]\n"
			+ "    --package          Package script and dependencies into self-dependent binary\n"
			+ "    --idea             boostrap IDEA from a kscript\n"
			+ "    -i, --interactive  Create interactive shell with dependencies as declared in script\n"
			+ "    -                  Read script from the STDIN\n" + "    -h, --help         Print this text\n"
			+ "    --clear-cache      Wipe cached script jars and urls\n" + "";

	public static void main(String[] args) {
		var doArgs = new Docopt(usage).parse(args);

		out.println("parsed args are: \n$doArgs (${doArgs.javaClass.simpleName})\n");

		/*doArgs.forEach { (key: Any, value: Any) ->
				    println("$key:\t$value\t(${value?.javaClass?.canonicalName})")
		};*/

		out.println("\nHello from Java!");
		for (String arg : args) {
			out.println("arg: $arg");
		}
	
	}
}