///usr/bin/env jbang "$0" "$@" ; exit $?
// //DEPS <dependency1> <dependency2>
//NATIVE_OPTIONS -O1
//RUNTIME_OPTIONS -Dfoo=bar "-Dbar=aap noot mies"
//MANIFEST foo bar=baz baz=${bazprop:nada}

class helloworld {
    fun main(vararg args: String) {
        println("Hello " + if (args.length>0) args[0] else "World"));
    }
}
