/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.Random;

/**
 *
 * @author Navdeep Singh <navdeepsingh.sidhu95 at gmail.com>
 */
public class RandomArrGen {

    public static void main(String[] args) {
        Random rand = new Random();
        long firstdim = 100000, seconddim = 100;
        System.out.print("{");
        System.out.print("{");
        for (long i = 0L; i <= firstdim; i++) {
            System.out.print("" + rand.nextLong() % 100);
            if (i != firstdim) {
                System.out.print(",");
            }
            if(i%10000==0){
                System.out.println("");
            
            }
        }
        System.out.print("}");
        System.out.println(",");
        System.out.print("{");
        /*
        for (long j = 0L; j <= seconddim; j++) {
            System.out.print("" + rand.nextLong() % 100);
            if (j != seconddim) {
                System.out.print(",");
            }
        if(j%10==0){
                System.out.println("");
            
            }
        
        }
        
            System.out.println("}");
    System.out.println("}");
*/
        }
    }

