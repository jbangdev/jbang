//usr/bin/env jbang "$0" "$@" ; exit $?
// //DEPS <dependency1> <dependency2>

import static java.lang.System.*;

import java.util.Locale;
import java.nio.charset.Charset;


public class lang {

    public static void main(String... args) {
        
        System.out.println(Locale.getDefault().getLanguage() + "." + Charset.defaultCharset().displayName());

    }
}
