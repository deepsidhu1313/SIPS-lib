/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

/**
 *
 * @author Navdeep Singh <navdeepsingh.sidhu95 at gmail.com>
 */
import java.util.Random;  /* for random number */

import java.lang.*;		/* for Math.abs funciton  */
import lib1.*;

class MatMul {

    public static void main(String args[]) {
        SIPS sim = new SIPS();
        long a[][] = new long[1000][1000];
        long b[][] = new long[1000][1000];
        long c[][] = new long[a[0].length][b[1].length];

        int nra, nca; /* nra is number of rows of a, nca is number of col. of a */

        int nrb, ncb;
        int nrc, ncc;

        /* fix order 10 by 10 */
        nra = nca = nrb = ncb = a[0].length;

        /*initialize a[][]with random numbers */
        Random rand = new Random();


        /* display a[][] and b[][] temporarily for checking */
       // System.out.println("Elements of a[][] array are : ");
        for (int i = 0; i < nra; i++) {
            for (int j = 0; j < nca; j++) {
                a[i][j] = rand.nextLong();
            //    System.out.print(a[i][j] + "   ");
                sim.simulateDLoop();
            }
         //   System.out.println();
        }

        //System.out.println("Elements of b[][] array are : ");
        for (int i = 0; i < nrb; i++) {
            for (int j = 0; j < ncb; j++) {
                b[i][j] = rand.nextLong();
            //    System.out.print(b[i][j] + "   ");
                sim.simulateDLoop();
            }
           // System.out.println();
        }
        System.out.println("Elements of resultant array are : ");

        sim.saveDValues("" + nra, "" + ncb, "" + nca);
        sim.saveDObjects(a);
        sim.saveDObjects(b);
        a=(long [][])sim.resolveObject("a", 0);
        b=(long [][])sim.resolveObject("b", 1);
        
        /* start matrix multiplication */
        for (int i = 0; i < nra; i++) {
            for (int j = 0; j < ncb; j++) {
                c[i][j] = 0;
                for (int k = 0; k < nca; k++) {
                    c[i][j] = c[i][j] + a[i][k] * b[k][j];
                }
                System.out.print(c[i][j] + "   ");

            }
            System.out.println();
        }

    }
}