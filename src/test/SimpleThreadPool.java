/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author Sammy Guergachi <sguergachi at gmail.com>
 */
public class SimpleThreadPool {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        for (int i = 0; i < 100000; i++) {
            //Runnable worker = new WorkerThread("" + i);
            executor.execute(new WorkerThread("" + i));
            System.out.println("executed "+ i);
          }
        executor.shutdown();
        while (!executor.isTerminated()) {
        }
        System.out.println("Finished all threads");
    }
}
