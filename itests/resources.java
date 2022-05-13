///usr/bin/env jbang "$0" "$@" ; exit $?
//FILES res/*.properties

import java.io.InputStream;
import java.util.Properties;

class resources {

    public static void main(String[] args) throws Exception {
        Properties prop = new Properties();
        try(InputStream is = resources.class.getClassLoader().getResourceAsStream("resource.properties")) {
            prop.load(is);
        }
        System.out.println("hello " + prop.getProperty("message"));
    }
}