/*
 * Copyright (C) 2018 Navdeep Singh Sidhu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package test;

import java.util.Random;
import in.co.s13.sips.lib.*;

/**
 *
 * @author nika
 */
public class MergeSort {

    private int[] numbers;
    private int[] helper;

    private int number;

    public static void main(String[] args) {
        SIPS sips = new SIPS("MergeSort");

        MergeSort sorter = new MergeSort();
        int first[] = new int[1000000];
        int second[] = new int[2000000];
        int third[] = new int[3000000];
        int fourth[] = new int[4000000];
        int fifth[] = new int[5000000];
        int sixth[] = new int[6000000];
        int seventh[] = new int[7000000];
        int eigth[] = new int[8000000];
        int ninth[] = new int[9000000];
        int tenth[] = new int[10000000];
        sips.simulateSection();
        Random rand = new Random();
        for (int i = 0; i < first.length; i++) {
            first[i] = rand.nextInt();
        }
        sips.saveObject("first", 0, first);
        for (int i = 0; i < second.length; i++) {
            second[i] = rand.nextInt();
        }

        sips.saveObject("second", 0, second);

        for (int i = 0; i < third.length; i++) {
            third[i] = rand.nextInt();
        }

        sips.saveObject("third", 0, third);

        for (int i = 0; i < fourth.length; i++) {
            fourth[i] = rand.nextInt();
        }

        sips.saveObject("fourth", 0, fourth);
        for (int i = 0; i < fifth.length; i++) {
            fifth[i] = rand.nextInt();
        }

        sips.saveObject("fifth", 0, fifth);
        for (int i = 0; i < sixth.length; i++) {
            sixth[i] = rand.nextInt();
        }

        sips.saveObject("sixth", 0, sixth);
        for (int i = 0; i < seventh.length; i++) {
            seventh[i] = rand.nextInt();
        }

        sips.saveObject("seventh", 0, seventh);
        for (int i = 0; i < eigth.length; i++) {
            eigth[i] = rand.nextInt();
        }
        sips.saveObject("eigth", 0, eigth);
        for (int i = 0; i < ninth.length; i++) {
            ninth[i] = rand.nextInt();
        }

        sips.saveObject("ninth", 0, ninth);

        for (int i = 0; i < tenth.length; i++) {
            tenth[i] = rand.nextInt();
        }

        sips.saveObject("tenth", 0, tenth);
        sips.endSimulateSection();
        long start = System.currentTimeMillis();

        sips.defineTask("first");
        sips.setDuration("first", 1);
        first = (int[]) sips.resolveObject("first", 0);
        sorter.sort(first);
        sips.sendResult("first", 1, first);
        sips.endTask("first");

        sips.defineTask("second");
        sips.setDuration("second", 2);
        second = (int[]) sips.resolveObject("second", 0);
        sorter.sort(second);
        sips.sendResult("second", 1, second);
        sips.endTask("second");

        sips.defineTask("third");
        sips.setDuration("third", 3);
        third = (int[]) sips.resolveObject("third", 0);
        sorter.sort(third);
        sips.sendResult("third", 1, third);
        sips.endTask("third");

        sips.defineTask("fourth");
        sips.setDuration("fourth", 4);
        fourth = (int[]) sips.resolveObject("fourth", 0);
        sorter.sort(fourth);
        sips.sendResult("fourth", 1, fourth);
        sips.endTask("fourth");

        sips.defineTask("fifth");
        sips.setDuration("fifth", 5);
        fifth = (int[]) sips.resolveObject("fifth", 0);
        sorter.sort(fifth);
        sips.sendResult("fifth", 1, fifth);
        sips.endTask("fifth");

        sips.defineTask("sixth");
        sips.setDuration("sixth", 6);
        sixth = (int[]) sips.resolveObject("sixth", 0);
        sorter.sort(sixth);
        sips.sendResult("sixth", 1, sixth);
        sips.endTask("sixth");

        sips.defineTask("seventh");
        sips.setDuration("seventh", 7);
        seventh = (int[]) sips.resolveObject("seventh", 0);
        sorter.sort(seventh);
        sips.sendResult("seventh", 1, seventh);
        sips.endTask("seventh");

        sips.defineTask("eigth");
        sips.setDuration("eigth", 8);
        eigth = (int[]) sips.resolveObject("eigth", 0);
        sorter.sort(eigth);
        sips.sendResult("eigth", 1, eigth);
        sips.endTask("eigth");

        sips.defineTask("ninth");
        sips.setDuration("ninth", 9);
        ninth = (int[]) sips.resolveObject("ninth", 0);
        sorter.sort(ninth);
        sips.sendResult("ninth", 1, ninth);
        sips.endTask("ninth");

        sips.defineTask("tenth");
        sips.setDuration("tenth", 10);
        tenth = (int[]) sips.resolveObject("tenth", 0);
        sorter.sort(tenth);
        sips.sendResult("tenth", 1, tenth);
        sips.endTask("tenth");

        sips.defineTask("1-2");
        sips.setDuration("1-2", 3);
        sips.setTaskDependency("1-2", "first","second");
        first = (int[]) sips.receiveResult("first", 1);
        second = (int[]) sips.receiveResult("second", 1);
        int[] f2 = new int[first.length + second.length];
        System.arraycopy(first, 0, f2, 0, first.length);
        System.arraycopy(second, 0, f2, first.length, second.length);
        sorter.sort(f2);
        sips.sendResult("f2", 0, f2);
        sips.endTask("1-2");

        sips.defineTask("3-4");
        sips.setDuration("3-4", 7);
        sips.setTaskDependency("3-4", "third","fourth");
        third = (int[]) sips.receiveResult("third", 1);
        fourth = (int[]) sips.receiveResult("fourth", 1);
        int[] t4 = new int[third.length + fourth.length];
        System.arraycopy(third, 0, t4, 0, third.length);
        System.arraycopy(fourth, 0, t4, third.length, fourth.length);
        sorter.sort(t4);
        sips.sendResult("t4", 0, t4);
        sips.endTask("3-4");

        sips.defineTask("5-6");
        sips.setDuration("5-6", 11);
        sips.setTaskDependency("5-6", "fifth","sixth");
        fifth = (int[]) sips.receiveResult("fifth", 1);
        sixth = (int[]) sips.receiveResult("sixth", 1);
        int[] f6 = new int[fifth.length + sixth.length];
        System.arraycopy(fifth, 0, f6, 0, fifth.length);
        System.arraycopy(sixth, 0, f6, fifth.length, sixth.length);
        sorter.sort(f6);
        sips.sendResult("f6", 0, f6);
        sips.endTask("5-6");

        sips.defineTask("7-8");
        sips.setDuration("7-8", 15);
        sips.setTaskDependency("7-8", "seventh","eigth");
        seventh = (int[]) sips.receiveResult("seventh", 1);
        eigth = (int[]) sips.receiveResult("eigth", 1);
        int[] s8 = new int[seventh.length + eigth.length];
        System.arraycopy(seventh, 0, s8, 0, seventh.length);
        System.arraycopy(eigth, 0, s8, seventh.length, eigth.length);
        sorter.sort(s8);
        sips.sendResult("s8", 0, s8);
        sips.endTask("7-8");

        sips.defineTask("9-10");
        sips.setDuration("9-10", 19);
        sips.setTaskDependency("9-10", "ninth","tenth");
        ninth = (int[]) sips.receiveResult("ninth", 1);
        tenth = (int[]) sips.receiveResult("tenth", 1);
        int[] n10 = new int[ninth.length + tenth.length];
        System.arraycopy(ninth, 0, n10, 0, ninth.length);
        System.arraycopy(tenth, 0, n10, ninth.length, tenth.length);
        sorter.sort(n10);
        sips.sendResult("n10", 0, n10);
        sips.endTask("9-10");

        sips.defineTask("1-2-3-4");
        sips.setDuration("1-2-3-4", 10);
        sips.setTaskDependency("1-2-3-4", "1-2","3-4");
        int[] f2_0 = (int[]) sips.receiveResult("f2", 0);
        int[] t4_0 = (int[]) sips.receiveResult("t4", 0);
        int[] f234 = new int[f2_0.length + t4_0.length];
        System.arraycopy(f2_0, 0, f234, 0, f2_0.length);
        System.arraycopy(t4_0, 0, f234, f2_0.length, t4_0.length);
        sorter.sort(f234);
        sips.sendResult("f234", 0, f234);
        sips.endTask("1-2-3-4");

        sips.defineTask("5-6-7-8");
        sips.setDuration("5-6-7-8", 26);
        sips.setTaskDependency("5-6-7-8", "5-6","7-8");
        int[] f6_0 = (int[]) sips.receiveResult("f6", 0);
        int[] s8_0 = (int[]) sips.receiveResult("s8", 0);
        int[] f678 = new int[f6_0.length + s8_0.length];
        System.arraycopy(f6_0, 0, f678, 0, f6_0.length);
        System.arraycopy(s8_0, 0, f678, f6_0.length, s8_0.length);
        sorter.sort(f678);
        sips.sendResult("f678", 0, f678);
        sips.endTask("5-6-7-8");

        sips.defineTask("1-2-3-4-5-6-7-8");
        sips.setDuration("1-2-3-4-5-6-7-8", 36);
        sips.setTaskDependency("1-2-3-4-5-6-7-8", "1-2-3-4","5-6-7-8");
        int[] f234_0 = (int[]) sips.receiveResult("f234", 0);
        int[] f678_0 = (int[]) sips.receiveResult("f678", 0);
        int[] f2345678 = new int[f234_0.length + f678_0.length];
        System.arraycopy(f234_0, 0, f2345678, 0, f234_0.length);
        System.arraycopy(f678_0, 0, f2345678, f234_0.length, f678_0.length);
        sorter.sort(f2345678);
        sips.sendResult("f2345678", 0, f2345678);
        sips.endTask("1-2-3-4-5-6-7-8");

        sips.defineTask("1-2-3-4-5-6-7-8-9-10");
        sips.setDuration("1-2-3-4-5-6-7-8-9-10", 55);
        sips.setTaskDependency("1-2-3-4-5-6-7-8-9-10", "1-2-3-4-5-6-7-8","9-10");
        int[] f2345678_0 = (int[]) sips.receiveResult("f2345678", 0);
        int[] n10_0 = (int[]) sips.receiveResult("n10", 0);
        int[] f2345678910 = new int[f2345678_0.length + n10_0.length];
        System.arraycopy(f2345678_0, 0, f2345678910, 0, f2345678_0.length);
        System.arraycopy(n10_0, 0, f2345678910, f2345678_0.length, n10_0.length);
        sorter.sort(f2345678910);
        sips.sendResult("f2345678910", 0, f2345678910);
        sips.endTask("1-2-3-4-5-6-7-8-9-10");

        System.out.println("Time taken to sort :" + (System.currentTimeMillis() - start));
    }

    public void sort(int[] values) {
        this.numbers = values;
        number = values.length;
        this.helper = new int[number];
        mergesort(0, number - 1);
    }

    private void mergesort(int low, int high) {
        // check if low is smaller than high, if not then the array is sorted
        if (low < high) {
            // Get the index of the element which is in the middle
            int middle = low + (high - low) / 2;
            // Sort the left side of the array
            mergesort(low, middle);
            // Sort the right side of the array
            mergesort(middle + 1, high);
            // Combine them both
            merge(low, middle, high);
        }
    }

    private void merge(int low, int middle, int high) {

        // Copy both parts into the helper array
        for (int i = low; i <= high; i++) {
            helper[i] = numbers[i];
        }

        int i = low;
        int j = middle + 1;
        int k = low;
        // Copy the smallest values from either the left or the right side back
        // to the original array
        while (i <= middle && j <= high) {
            if (helper[i] <= helper[j]) {
                numbers[k] = helper[i];
                i++;
            } else {
                numbers[k] = helper[j];
                j++;
            }
            k++;
        }
        // Copy the rest of the left side of the array into the target array
        while (i <= middle) {
            numbers[k] = helper[i];
            k++;
            i++;
        }
        // Since we are sorting in-place any leftover elements from the right side
        // are already at the right position.

    }

}
