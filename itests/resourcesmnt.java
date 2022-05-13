///usr/bin/env jbang "$0" "$@" ; exit $?
//FILES somedir=res/*.properties

import java.io.InputStream;
import java.util.Properties;

class resourcesmnt {

    public static void main(String[] args) throws Exception {
        Properties prop = new Properties();
        try(InputStream is = resourcesmnt.class.getClassLoader().getResourceAsStream("somedir/resource.properties")) {
            prop.load(is);
        }
        System.out.println("hello " + prop.getProperty("message"));
    }
}