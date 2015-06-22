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
import java.util.Random;  
import lib1.SIPS;

class MatMul {

    public static void main(String args[]) {
        SIPS sim = new SIPS("MatMul");
        double a[][] = new double[1000][1000];
        double b[][] = new double[1000][1000];
        double c[][] = new double[a[0].length][b[1].length];

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
                a[i][j] = rand.nextDouble();
            //    System.out.print(a[i][j] + "   ");
                sim.simulateDLoop();
            }
         //   System.out.println();
        }

        //System.out.println("Elements of b[][] array are : ");
        for (int i = 0; i < nrb; i++) {
            for (int j = 0; j < ncb; j++) {
                b[i][j] = rand.nextDouble();
            //    System.out.print(b[i][j] + "   ");
                sim.simulateDLoop();
            }
           // System.out.println();
        }
        System.out.println("Elements of resultant array are : ");

        sim.saveDValues("" + nra, "" + ncb, "" + nca);
        sim.saveDObject("a",0,a);
        sim.saveDObject("b",1,b);
        a=(double [][])sim.resolveObject("a", 0);
        b=(double [][])sim.resolveObject("b", 1);
        
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