///usr/bin/env jbang "$0" "$@" ; exit $?

System.out.println("Hello " + (args.length>0?args[0]:"World"));
for(String a : args) {
    System.out.println(a);
}

/exit
