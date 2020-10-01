
import java.util.logging.Logger;

/**
 * Just some application
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 */
public class JULTest {
    private static final Logger log = Logger.getLogger(JULTest.class.getName());

    public void printSomeStuff(String message){
        log.info("info " + message);
        log.finest("finest " + message);

    }


    public static void main(String[] args) {
        new JULTest().printSomeStuff(args[0]);
    }
}