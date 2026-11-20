//VARS tinylog.version=2.7.0
//VARS max.size=2MB
//VARS base=varsbase
//DEPS org.tinylog:tinylog-api:${tinylog.version}
//DEPS org.tinylog:tinylog-impl:${tinylog.version}
//SOURCES ${base}/RequestLog.java
//PROPS "tinylog.writer=rolling file" tinylog.writer.file=logdemo_{count}.log
//PROPS tinylog.writer.backups=3 tinylog.writer.policies=size:${max.size}
//RUNTIME_OPTIONS -Xmx64m -Dtinylog.writingthread=false

import java.lang.management.ManagementFactory;
import org.tinylog.Logger;

public class logdemo {
    public static void main(String[] args) {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            System.out.println("JVMARG:" + arg);
        }
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        for (int i = 0; i < count; i++) {
            RequestLog.record(i);
        }
        Logger.info("done after {} entries", count);
        System.out.println("DONE");
    }
}
