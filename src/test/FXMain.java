/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 *
 * @author navdeep singh <navdeepsingh.sidhu95@gmail.com>
 */
public class FXMain extends Application {
    public static Label lab[]= new Label[2];
    @Override
    public void start(Stage primaryStage) {
         lab[0]= new Label("Thread 0");
         lab[1]= new Label("Thread 1");
        Button btn = new Button();
        btn.setText("Start");
        btn.setOnAction(new EventHandler<ActionEvent>() {
            
            @Override
            public void handle(ActionEvent event) {
           for (int i = 0; i < 2; i++) {
               Thread t= new Thread(new simpleThread1(i));
           t.start();
           }
           System.out.println("Button Clicked");
            }
        });
        
        VBox root = new VBox(40);
        root.getChildren().add(lab[0]);
        root.getChildren().add(lab[1]);
        root.getChildren().add(btn);
        root.setPadding(new Insets(50, 0, 0, 50));
        Scene scene = new Scene(root, 500, 550);
        
        primaryStage.setTitle("Hello World!");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
    
}
