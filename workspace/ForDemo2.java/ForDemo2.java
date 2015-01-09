/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */



import java.math.BigInteger;
import lib1.*;
/**
 *
 * @author Nika
 */
public class ForDemo2 {
    int i=0;
    byte b=100;

    short c=0;
    double d=0;
    float f=0;
simulator sim= new simulator("ForDemo2");
    public ForDemo2() {
//sim.saveDValues(""+b);
    for(int i=0;i<=b;i++){
        System.out.println(""+i);
    
    }
//sim.saveDValues(""+c);

    }
    
    public static void main(String[] args) {
        new ForDemo2();
    }
}
