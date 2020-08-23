//JAVA 11
import java.util.Scanner;
import sun.misc.Signal;

public class ilc {

    public static void main(final String[] args) {

        // running this with `yes jbang | jbang ilc.java | head -n 5`
        // and you will see the output freeze it below code is not there.
        // See https://github.com/jbangdev/jbang/issues/247 for details.
        if (!"Windows".equals(System.getProperty("os.name"))) {
            Signal.handle(new Signal("PIPE"), (final Signal sig) -> System.exit(1));
        }

	int count = 0;
	final var input = new Scanner(System.in);
	while (input.hasNextLine()) {
	    input.nextLine();
	    count += 1;
	    System.out.println(count);
	}
        input.close();
    }
}