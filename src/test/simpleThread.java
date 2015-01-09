/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

/**
 *
 * @author navdeep singh <navdeepsingh.sidhu95@gmail.com>
 */
public class simpleThread implements Runnable {

    int threadnumber;

    public simpleThread(int j) {
        threadnumber = j;
    }

    @Override
    public void run() {
     
        for (long i = 0L; i <= 1000000; i++) {
            System.out.println("Thread number = " + threadnumber + " \t Loop Counter=" + i);

            MainFrame.jta.append("Thread number = " + threadnumber + " \t Loop Counter=" + i+"\n");
        MainFrame.verticalScrollBar.setValue(MainFrame.verticalScrollBar.getMaximum());
        }

    }

}
