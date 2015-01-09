/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import lib1.SIPS;

/**
 *
 * @author Navdeep Singh <navdeepsingh.sidhu95 at gmail.com>
 */
public class LinearSearch {

    public static void main(String[] args) {
        SIPS sim = new SIPS();
        int i[] = new int[1000000];
        int number = 83;
        for (int r = 0; r < i.length; r++) {
            i[r] = (int) (Math.random() * 100);
        }
        System.out.println(i.length);
// sim.saveDValues(""+i.length);
        for (int p = 0; p < i.length; p++) {
            if (i[p] == number) {
                System.out.println("Location of " + number + " in Array is " + p);
            }
        }

    }
}
