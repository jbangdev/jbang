///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS org.zeroturnaround:zt-exec:1.12
//DEPS commons-codec:commons-codec:1.15
//DEPS org.slf4j:slf4j-nop:1.7.30

import org.apache.commons.codec.digest.DigestUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.LogOutputStream;
import org.apache.commons.codec.digest.DigestUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.LogOutputStream;
import picocli.CommandLine.*;
import picocli.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Callable;

@Command(name = "GrabAvatars", mixinStandardHelpOptions = true, version = "GrabAvatars 0.1",
        description = "GrabAvatars made with jbang")
class grabavatars implements Callable<Integer> {

    public static void main(String... args) {
        int exitCode = new CommandLine(new grabavatars()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...

        int size = 90;
        File outputDir = Path.of(".git/avatar").toFile();

        if(!Path.of(".git").toFile().exists()) {
            throw new IllegalStateException("no .git/ directory found in current path");
        }

        if(!outputDir.exists()) {
            outputDir.mkdir();
        }

        Set processedAuthors = new HashSet();

        MessageDigest md = MessageDigest.getInstance("MD5");

        new ProcessExecutor().command("git", "log", "--pretty=format:%ae|%an")
                .redirectOutput(new LogOutputStream() {
                    @Override
                    protected void processLine(String line) {
                        String[] data = line.split("\\|");
                        String email = data[0].toLowerCase();
                        String author = data[1];
                        boolean error = false;

                        if(!processedAuthors.add(author)) {
                            return;
                        }

                        File authorImageFile = new File(outputDir, author + ".png");

                        if(authorImageFile.exists()) {
                            return;
                        }

                        try {
                        URL grav_url = new URL("http://www.gravatar.com/avatar/"
                                    + DigestUtils.md5Hex(email)
                                    + "?d=404&size=" + size);


                        try(InputStream in = grav_url.openStream()) {
                            Files.copy(in, authorImageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }

                        } catch (IOException e) {
                            error = true;
                        }

                        System.out.println(author + " <" + email + "> " + (error?"N/A":"OK"));

                    }
                })
                .execute();
        return 0;
    }
}
