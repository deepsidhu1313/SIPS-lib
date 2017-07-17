/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import lib1.SIPS;
import lib1.serialize;

/**
 *
 * @author Navdeep Singh <navdeepsingh.sidhu95 at gmail.com>
 */
public class LinearSearch {

    public static void main(String[] args) {
        try {
            SIPS sim = new SIPS("LinearSearch");
//        int i[] = new int[1000000];
//        int number = 83;
//        for (int r = 0; r < i.length; r++) {
//            i[r] = (int) (Math.random() * 100);
//        }
//        System.out.println(i.length);
//// sim.saveDValues(""+i.length);
//        for (int p = 0; p < i.length; p++) {
//            if (i[p] == number) {
//                System.out.println("Location of " + number + " in Array is " + p);
//            }
//        }
           SipsRunnable sir= new SipsRunnable() {
                @Override
                public void run() {
                    System.out.println("Hurray");
                    sim.saveDObject("Thread", 0, new SipsThread());
                }
            };
            
//sim.saveDObject("Thread", 0, t);
            
//serialize.serialize(t, "thread");
            serialize.serialize(sir, "thread2");
     SipsRunnable t2 = (SipsRunnable) serialize.deserialize("thread2");
//     SipsThread t3 = (SipsThread) serialize.deserialize("thread");
SipsThread t = new SipsThread(t2);     
t.start();
        } catch (IOException ex) {
            Logger.getLogger(LinearSearch.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(LinearSearch.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
