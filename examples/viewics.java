///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS org.jfxtras:jfxtras-icalendaragenda:9.0-r1
//DEPS org.openjfx:javafx-controls:11.0.2:${os.detected.jfxname}
//DEPS org.openjfx:javafx-graphics:11.0.2:${os.detected.jfxname}

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jfxtras.icalendarfx.VCalendar;
import jfxtras.icalendarfx.properties.calendar.Method.MethodType;
import jfxtras.scene.control.agenda.icalendar.ICalendarAgenda;
import jfxtras.scene.layout.HBox;

@Command(name = "viewics", mixinStandardHelpOptions = true, version = "viewics 0.1",
        description = "viewics made with jbang")
public class viewics extends Application {

    private CommandLine.ParseResult parseResult;

    @CommandLine.Parameters(index = "0", description = "Calender to open", arity = "0..1")
    private File ics;

    public static void main(String... args) {
        launch(args);
    }

    public viewics() {

    }

    @Override
    public void start(Stage primaryStage) {
        parseResult = new CommandLine(this).parseArgs(getParameters().getRaw().toArray(new String[]{}));

        // setup VCalendar - holds all calendaring information (i.e events)
        VCalendar mainVCalendar = new VCalendar()
                .withProductIdentifier(ICalendarAgenda.DEFAULT_PRODUCT_IDENTIFIER)
                .withVersion(); // uses default VERSION 2.0

        // setup controls
        BorderPane root = new BorderPane();
        ICalendarAgenda agenda = new ICalendarAgenda(mainVCalendar); // Agenda - displays the VCalendar information
        root.setCenter(agenda);

        // Buttons
        Button increaseWeek = new Button(">");
        Button decreaseWeek = new Button("<");
        HBox weekButtonHBox = new HBox(decreaseWeek, increaseWeek);
        Button importButton = new Button("Import an ics file");
        Button exportButton = new Button("Export an ics file");
        HBox buttonHBox = new HBox(weekButtonHBox, importButton, exportButton);
        buttonHBox.setSpacing(10);
        root.setTop(buttonHBox);

        // weekly increase/decrease event handlers
        increaseWeek.setOnAction(e ->
        {
            LocalDateTime newDisplayedLocalDateTime = agenda.getDisplayedLocalDateTime().plus(Period.ofWeeks(1));
            agenda.setDisplayedLocalDateTime(newDisplayedLocalDateTime);
        });
        decreaseWeek.setOnAction(e ->
        {
            LocalDateTime newDisplayedLocalDateTime = agenda.getDisplayedLocalDateTime().minus(Period.ofWeeks(1));
            agenda.setDisplayedLocalDateTime(newDisplayedLocalDateTime);
        });

        // import ics event handler
        FileChooser fileChooser = new FileChooser();
        fileChooser.setSelectedExtensionFilter(new FileChooser.ExtensionFilter("ics files", "*.ics"));
        importButton.setOnAction(e ->
        {
            File file = fileChooser.showOpenDialog(primaryStage);
            if (file != null && file.toString().lastIndexOf("ics") > 0)
            {
                loadICS(mainVCalendar, file);
            } else
            {
                throw new IllegalArgumentException("Invalid file:" + file + ". Select a valid ics file.");
            }
        });

        // export ics event handler
        exportButton.setOnAction(e ->
        {
            VCalendar publishMessage = new VCalendar()
                    .withMethod(MethodType.PUBLISH);
            mainVCalendar.copyChildrenInto(publishMessage);
            File file = fileChooser.showSaveDialog(primaryStage);
            BufferedWriter writer = null;
            try
            {
                writer = new BufferedWriter(new FileWriter(file));
                writer.write(publishMessage.toString());
                writer.close();
            } catch ( IOException e2)
            {
                e2.printStackTrace();
            }
        });

        if(ics!=null) {
            System.out.println(ics);
            loadICS(mainVCalendar, ics);
        }

        Scene scene = new Scene(root, 1366, 768);
        primaryStage.setScene(scene);
        primaryStage.setTitle("ICalendar Agenda Simple Demo");
        primaryStage.show();
    }

    private void loadICS(VCalendar mainVCalendar, File file) {
        try
        {
            // process iTIP and log exceptions
            final List<String> log = new ArrayList<>();
            VCalendar iTIPMessage = VCalendar.parseICalendarFile(file.toPath());
            Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> log.add(exception.getMessage()));
            List<String> messageLog = mainVCalendar.processITIPMessage(iTIPMessage);
            log.addAll(messageLog);
//                    log.forEach(System.out::println);
        } catch (Exception e1)
        {
            e1.printStackTrace();
        }
    }
}
