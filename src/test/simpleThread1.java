/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import javafx.application.Platform;

/**
 *
 * @author navdeep singh <navdeepsingh.sidhu95@gmail.com>
 */
public class simpleThread1 implements Runnable {

    int threadnumber;

    public simpleThread1(int j) {
        threadnumber = j;
    }

    @Override
    public void run() {

        for (long i = 0L; i <= 1000000; i++) {
            System.out.println("Thread number = " + threadnumber + " \t Loop Counter=" + i);
            final long j=i;
            Platform.runLater(new Runnable() {

                @Override
                public void run() {
            FXMain.lab[threadnumber].setText("Thread number = " + threadnumber + " \t Loop Counter=" + j);
            }
            });
            }

    }

}
