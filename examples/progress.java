//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS me.tongfei:progressbar:0.8.1

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

@Command(name = "progress", mixinStandardHelpOptions = true, version = "progress 0.1",
        description = "progress made with jbang")
class progress implements Callable<Integer> {

    @Parameters(index = "0", description = "The URL to download")
    private URI uri;

    @Parameters(index = "1", description = "File to save as")
    private File file;


    public static void main(String... args) {
        int exitCode = new CommandLine(new progress()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...

        ProgressBarBuilder pbb = new ProgressBarBuilder()
                .setTaskName("Downloading")
                .setUnit("MB", 1048576);

        BufferedInputStream bis = new BufferedInputStream(ProgressBar.wrap(uri.toURL().openStream(), pbb));
        FileOutputStream fis = new FileOutputStream(file);

        byte[] buffer = new byte[1024];
        int count=0;
        while((count = bis.read(buffer,0,1024)) != -1)
        {
            fis.write(buffer, 0, count);
        }
        fis.close();
        bis.close();

        return 0;
    }
}
