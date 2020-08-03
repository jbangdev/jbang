//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS javax.mail:mail:1.4.7

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;
import java.util.Properties;
import java.util.concurrent.Callable;

@Command(name = "imap", mixinStandardHelpOptions = true, version = "imap 0.1",
        description = "imap made with jbang - prints out unread message count.", showDefaultValues = true)
class imap implements Callable<Integer> {

    @CommandLine.Option(names={"--host"}, defaultValue = "imap.gmail.com")
    private String host;

    @Option(names={"--username"}, required = true)
    private String username;

    @Option(names = {"--password"}, required = true)
    private String password;

    @Parameters(index="0", defaultValue="INBOX")
    String folder;

    public static void main(String... args) {
        int exitCode = new CommandLine(new imap()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        Properties props = System.getProperties();
        props.setProperty("mail.store.protocol", "imaps");
        Session session = Session.getDefaultInstance(props, null);
        Store store = session.getStore("imaps");
        store.connect(host, username, password);
        //System.out.println(store);

        Folder f = store.getFolder(folder);
        System.out.println(f.getName() + ":" + f.getUnreadMessageCount());

        return f.getUnreadMessageCount();
    }


}
