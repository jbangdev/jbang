//usr/bin/env jbang "$0" "$@" ; exit $?
//FILES resource.properties renamed.properties=resource.properties
//FILES META-INF/application.properties=resource.properties

import java.io.InputStream;
import java.util.Properties;

class resource {

    public static void main(String[] args) throws Exception {
        Properties prop = new Properties();
        try(InputStream is = resource.class.getClassLoader().getResourceAsStream("resource.properties")) {
            prop.load(is);
        }
        System.out.println("hello " + prop.getProperty("message"));
    }
}