//JAVAAGENT
public class JULAgent {

    public static void premain(String agentArgs, java.lang.instrument.Instrumentation instrumentation) {
        System.out.println(agentArgs);
    }
}