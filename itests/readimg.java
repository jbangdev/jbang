///usr/bin/env jbang "$0" "$@" ; exit $?

import java.io.File;
import javax.imageio.ImageIO;

class readimg {
    public static void main(String[] args) {
        System.out.println("" + ImageIO.read(new File(args[0])));
    }
}
