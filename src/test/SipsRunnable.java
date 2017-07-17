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
public abstract class SipsRunnable implements Runnable, Serializable{

    private static final long serialVersionUID = 6217172014399079306L;

    @Override
    public abstract void run();
    
}
