/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.io.Serializable;

/**
 *
 * @author nika
 */
public class SipsThread extends Thread implements Serializable {

    SipsThread(Runnable runnable) {
       super(runnable); //To change body of generated methods, choose Tools | Templates.
    }

    SipsThread() {
     super();
    }

    @Override
    public synchronized void start() {
        super.start(); //To change body of generated methods, choose Tools | Templates.
    }
    
    
    
}
