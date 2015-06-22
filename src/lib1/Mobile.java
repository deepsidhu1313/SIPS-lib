/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package lib1;

import java.io.Serializable;

/**
 * 
 * @author NAVDEEP SINGH SIDHU <navdeepsingh.sidhu95@gmail.com>
 */
 class Mobile implements Serializable{
     
    private Object number;
     
    public Mobile(Object num){
        this.number = num;
    }
     
    public Object getNumber() {
        return number;
    }
    public void setNumber(Object number) {
        this.number = number;
    }
}
