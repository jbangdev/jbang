//PROPS log.component=requests

import org.tinylog.Logger;

public class RequestLog {
    static void record(int n) {
        Logger.info("request {} handled by {}", n, System.getProperty("log.component", "unknown"));
    }
}
