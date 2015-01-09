/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 *
 * @author navdeep singh <navdeepsingh.sidhu95@gmail.com>
 */
public class ThrottlingCounter extends Application {

    @Override
    public void start(Stage primaryStage) {
        final AtomicLong counter = new AtomicLong(-1);
        final Label label = new Label();
        final Thread countThread = new Thread(new Runnable() {
            @Override
            public void run() {
                long count = 0 ;
                while (true) {
                    count++ ;
                    if (counter.getAndSet(count) == -1) {
                        updateUI(counter, label);
                    }
                }
            }
        });
        countThread.setDaemon(true);
        countThread.start();

        VBox root = new VBox();
        root.getChildren().add(label);
        root.setPadding(new Insets(5));
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root, 150, 100);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void updateUI(final AtomicLong counter,
            final Label label) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                final String msg = String.format("Count: %,d", counter.getAndSet(-1));
                label.setText(msg);
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}