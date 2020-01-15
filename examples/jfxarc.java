//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.openjfx:javafx-controls:13
// Java program to create a arc  (currently disfunct due to javafx module deps)
import javafx.application.Application; 
import javafx.scene.Scene; 
import javafx.scene.shape.DrawMode; 
import javafx.scene.layout.*; 
import javafx.event.ActionEvent; 
import javafx.scene.shape.Arc; 
import javafx.scene.control.*; 
import javafx.stage.Stage; 
import javafx.scene.Group; 
public class jfxarc extends Application { 
  
    // launch the application 
    public void start(Stage stage) 
    { 
        // set title for the stage 
        stage.setTitle("creating arc"); 
  
        // create a arc 
        Arc arc = new Arc(100.0f, 100.0f, 100.0f, 100.0f, 0.0f, 100.0f); 
  
        // create a Group 
        Group group = new Group(arc); 
  
        // translate the arc to a position 
        arc.setTranslateX(100); 
        arc.setTranslateY(100); 
  
        // create a scene 
        Scene scene = new Scene(group, 500, 300); 
  
        // set the scene 
        stage.setScene(scene); 
  
        stage.show(); 
    } 
  
    public static void main(String args[]) 
    { 
        // launch the application 
        launch(args); 
    } 
} 