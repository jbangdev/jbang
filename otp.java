//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.2.0
//DEPS com.amdelamar:jotp:1.2.2

import com.amdelamar.jotp.OTP;
import com.amdelamar.jotp.type.Type;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(name = "otp", mixinStandardHelpOptions = true, version = "otp 0.1",
        description = "otp made with jbang")
class otp implements Callable<Integer> {

    @Parameters(index = "0", description = "Secret to use to generate from")
    private String secret;

    public static void main(String... args) {
        int exitCode = new CommandLine(new otp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        // Generate a Time-based OTP from the secret, using Unix-time
// rounded down to the nearest 30 seconds.
        String hexTime = OTP.timeInHex(System.currentTimeMillis());
        String code = OTP.create(secret, hexTime, 6, Type.TOTP);

        System.out.println(code);
        return 0;
    }
}
