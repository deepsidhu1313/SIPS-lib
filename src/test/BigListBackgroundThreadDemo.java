/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 *
 * @author navdeep singh <navdeepsingh.sidhu95@gmail.com>
 */
public class BigListBackgroundThreadDemo extends Application {

    private static final int NUM_ITERATIONS = 10_000;
    private static final int NUM_THREADS_PER_CALL = 50;

    @Override
    public void start(Stage primaryStage) {
        ListView<String> data = new ListView<>();
        data.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        Button startButton = new Button("Start");
        Button selectAllButton = new Button("Select All");
        Button selectNoneButton = new Button("Clear Selection");
        Button copyToClipboardButton = new Button("Copy to clipboard");
        copyToClipboardButton.disableProperty().bind(Bindings.isEmpty(data.getSelectionModel().getSelectedItems()));

        AtomicInteger threadCount = new AtomicInteger();
        ExecutorService exec = Executors.newFixedThreadPool(5, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        startButton.setOnAction(event -> {
            exec.submit(() -> {
                for (int i = 0; i < NUM_THREADS_PER_CALL; i++) {
                    exec.submit(createTask(threadCount, data));
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException exc) {
                        throw new Error("Unexpected interruption", exc);
                    }
                }
            });
        });

        selectAllButton.setOnAction(event -> {
            data.getSelectionModel().selectAll();
            data.requestFocus();
        });
        selectNoneButton.setOnAction(event -> {
            data.getSelectionModel().clearSelection();
            data.requestFocus();
        });

        copyToClipboardButton.setOnAction(event -> {
            Thread copy = new Thread(new Runnable() {

                @Override
                public void run() {
                    ClipboardContent clipboardContent = new ClipboardContent();
                    clipboardContent.putString(String.join("\n", data.getSelectionModel().getSelectedItems()));
                    Clipboard.getSystemClipboard().setContent(clipboardContent);
                }
            });
            copy.start();
        });
        HBox controls = new HBox(5, startButton, selectAllButton, selectNoneButton, copyToClipboardButton);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(5));

        BorderPane root = new BorderPane(data, null, null, controls, null);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private Task<Void> createTask(AtomicInteger threadCount, ListView<String> target) {
        return new Task<Void>() {
            @Override
            public Void call() throws Exception {
                int count = threadCount.incrementAndGet();
                AtomicBoolean pending = new AtomicBoolean(false);
                BlockingQueue<String> messages = new LinkedBlockingQueue<>();
                for (int i = 0; i < NUM_ITERATIONS; i++) {
                    messages.add("Thread number: " + count + "\tLoop counter: " + i);
                    System.out.println("Thread number: " + count + "\tLoop counter: " + i);
                    if (pending.compareAndSet(false, true)) {
                        Platform.runLater(() -> {
                            pending.set(false);
                            messages.drainTo(target.getItems());
                            target.scrollTo(target.getItems().size() - 1);
                        });
                    }
                }

                return null;
            }
        };
    }

    public static void main(String[] args) {
        launch(args);
    }
}
