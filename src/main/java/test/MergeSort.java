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
        first = (int[]) sips.resolveObject("first", 0);
        sorter.sort(first);
        sips.endTask("first");

        sips.defineTask("second");
        second = (int[]) sips.resolveObject("second", 0);
        sorter.sort(second);
        sips.endTask("second");

        sips.defineTask("third");
        third = (int[]) sips.resolveObject("third", 0);
        sorter.sort(third);
        sips.endTask("third");

        sips.defineTask("fourth");
        fourth = (int[]) sips.resolveObject("fourth", 0);
        sorter.sort(fourth);
        sips.endTask("fourth");

        sips.defineTask("fifth");
        fifth = (int[]) sips.resolveObject("fifth", 0);
        sorter.sort(fifth);
        sips.endTask("fifth");

        sips.defineTask("sixth");
        sixth = (int[]) sips.resolveObject("sixth", 0);
        sorter.sort(sixth);
        sips.endTask("sixth");

        sips.defineTask("seventh");
        seventh = (int[]) sips.resolveObject("seventh", 0);
        sorter.sort(seventh);
        sips.endTask("seventh");

        sips.defineTask("eigth");
        eigth = (int[]) sips.resolveObject("eigth", 0);
        sorter.sort(eigth);
        sips.endTask("eigth");

        sips.defineTask("ninth");
        ninth = (int[]) sips.resolveObject("ninth", 0);
        sorter.sort(ninth);
        sips.endTask("ninth");

        sips.defineTask("tenth");
        tenth = (int[]) sips.resolveObject("tenth", 0);
        sorter.sort(tenth);
        sips.endTask("tenth");

        sips.defineTask("1-2");
        first = (int[]) sips.resolveObject("first", 1);
        second = (int[]) sips.resolveObject("second", 1);
        int[] firstSecond = new int[first.length + second.length];
        System.arraycopy(first, 0, firstSecond, 0, first.length);
        System.arraycopy(second, 0, firstSecond, first.length, second.length);
        sorter.sort(firstSecond);
        sips.endTask("1-2");

        sips.defineTask("3-4");
        third = (int[]) sips.resolveObject("third", 1);
        fourth = (int[]) sips.resolveObject("fourth", 1);
        int[] thirdFourth = new int[third.length + fourth.length];
        System.arraycopy(third, 0, thirdFourth, 0, third.length);
        System.arraycopy(fourth, 0, thirdFourth, third.length, fourth.length);
        sorter.sort(thirdFourth);
        sips.endTask("3-4");

        sips.defineTask("5-6");
        fifth = (int[]) sips.resolveObject("fifth", 1);
        sixth = (int[]) sips.resolveObject("sixth", 1);
        int[] fifthSixth = new int[fifth.length + sixth.length];
        System.arraycopy(fifth, 0, fifthSixth, 0, fifth.length);
        System.arraycopy(sixth, 0, fifthSixth, fifth.length, sixth.length);
        sorter.sort(fifthSixth);
        sips.endTask("5-6");

        sips.defineTask("7-8");
        seventh = (int[]) sips.resolveObject("seventh", 1);
        eigth = (int[]) sips.resolveObject("eigth", 1);
        int[] sevenEigth = new int[seventh.length + eigth.length];
        System.arraycopy(seventh, 0, sevenEigth, 0, seventh.length);
        System.arraycopy(eigth, 0, sevenEigth, seventh.length, eigth.length);
        sorter.sort(sevenEigth);
        sips.endTask("7-8");

        sips.defineTask("9-10");
        ninth = (int[]) sips.resolveObject("ninth", 1);
        tenth = (int[]) sips.resolveObject("tenth", 1);
        int[] ninthTenth = new int[ninth.length + tenth.length];
        System.arraycopy(ninth, 0, ninthTenth, 0, ninth.length);
        System.arraycopy(tenth, 0, ninthTenth, ninth.length, tenth.length);
        sorter.sort(ninthTenth);
        sips.endTask("9-10");

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
