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
 * I know i am not setting good example here for naming the objects
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
        int eleven[] = new int[1000000];
        int twelve[] = new int[2000000];
        int thirteen[] = new int[3000000];
        int fourteen[] = new int[4000000];
        int fifteen[] = new int[5000000];
        int sixteen[] = new int[6000000];
        int seventeen[] = new int[7000000];
        int eighteen[] = new int[8000000];
        int nineteen[] = new int[9000000];
        int twenty[] = new int[10000000];
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

        for (int i = 0; i < eleven.length; i++) {
            eleven[i] = rand.nextInt();
        }
        sips.saveObject("eleven", 0, eleven);
        for (int i = 0; i < twelve.length; i++) {
            twelve[i] = rand.nextInt();
        }

        sips.saveObject("twelve", 0, twelve);

        for (int i = 0; i < thirteen.length; i++) {
            thirteen[i] = rand.nextInt();
        }

        sips.saveObject("thirteen", 0, thirteen);

        for (int i = 0; i < fourteen.length; i++) {
            fourteen[i] = rand.nextInt();
        }

        sips.saveObject("fourteen", 0, fourteen);
        for (int i = 0; i < fifteen.length; i++) {
            fifteen[i] = rand.nextInt();
        }

        sips.saveObject("fifteen", 0, fifteen);
        for (int i = 0; i < sixteen.length; i++) {
            sixteen[i] = rand.nextInt();
        }

        sips.saveObject("sixteen", 0, sixteen);
        for (int i = 0; i < seventeen.length; i++) {
            seventeen[i] = rand.nextInt();
        }

        sips.saveObject("seventeen", 0, seventeen);
        for (int i = 0; i < eighteen.length; i++) {
            eighteen[i] = rand.nextInt();
        }
        sips.saveObject("eighteen", 0, eighteen);
        for (int i = 0; i < nineteen.length; i++) {
            nineteen[i] = rand.nextInt();
        }

        sips.saveObject("nineteen", 0, nineteen);

        for (int i = 0; i < twenty.length; i++) {
            twenty[i] = rand.nextInt();
        }

        sips.saveObject("twenty", 0, twenty);
        sips.endSimulateSection();
        long start = System.currentTimeMillis();

        sips.defineTask("1");
        sips.setDuration("1", 1);
        first = (int[]) sips.resolveObject("first", 0);
        sorter.sort(first);
        sips.sendResult("first", 1, first);
        sips.endTask("1");

        sips.defineTask("2");
        sips.setDuration("2", 2);
        second = (int[]) sips.resolveObject("second", 0);
        sorter.sort(second);
        sips.sendResult("second", 1, second);
        sips.endTask("2");

        sips.defineTask("3");
        sips.setDuration("3", 3);
        third = (int[]) sips.resolveObject("third", 0);
        sorter.sort(third);
        sips.sendResult("third", 1, third);
        sips.endTask("3");

        sips.defineTask("4");
        sips.setDuration("4", 4);
        fourth = (int[]) sips.resolveObject("fourth", 0);
        sorter.sort(fourth);
        sips.sendResult("fourth", 1, fourth);
        sips.endTask("4");

        sips.defineTask("5");
        sips.setDuration("5", 5);
        fifth = (int[]) sips.resolveObject("fifth", 0);
        sorter.sort(fifth);
        sips.sendResult("fifth", 1, fifth);
        sips.endTask("5");

        sips.defineTask("6");
        sips.setDuration("6", 6);
        sixth = (int[]) sips.resolveObject("sixth", 0);
        sorter.sort(sixth);
        sips.sendResult("sixth", 1, sixth);
        sips.endTask("6");

        sips.defineTask("7");
        sips.setDuration("7", 7);
        seventh = (int[]) sips.resolveObject("seventh", 0);
        sorter.sort(seventh);
        sips.sendResult("seventh", 1, seventh);
        sips.endTask("7");

        sips.defineTask("8");
        sips.setDuration("8", 8);
        eigth = (int[]) sips.resolveObject("eigth", 0);
        sorter.sort(eigth);
        sips.sendResult("eigth", 1, eigth);
        sips.endTask("8");

        sips.defineTask("9");
        sips.setDuration("9", 9);
        ninth = (int[]) sips.resolveObject("ninth", 0);
        sorter.sort(ninth);
        sips.sendResult("ninth", 1, ninth);
        sips.endTask("9");

        sips.defineTask("10");
        sips.setDuration("10", 10);
        tenth = (int[]) sips.resolveObject("tenth", 0);
        sorter.sort(tenth);
        sips.sendResult("tenth", 1, tenth);
        sips.endTask("10");

        sips.defineTask("11");
        sips.setDuration("11", 1);
        eleven = (int[]) sips.resolveObject("eleven", 0);
        sorter.sort(eleven);
        sips.sendResult("eleven", 1, eleven);
        sips.endTask("11");

        sips.defineTask("12");
        sips.setDuration("12", 2);
        twelve = (int[]) sips.resolveObject("twelve", 0);
        sorter.sort(twelve);
        sips.sendResult("twelve", 1, twelve);
        sips.endTask("12");

        sips.defineTask("13");
        sips.setDuration("13", 3);
        thirteen = (int[]) sips.resolveObject("thirteen", 0);
        sorter.sort(thirteen);
        sips.sendResult("thirteen", 1, thirteen);
        sips.endTask("13");

        sips.defineTask("14");
        sips.setDuration("14", 4);
        fourteen = (int[]) sips.resolveObject("fourteen", 0);
        sorter.sort(fourteen);
        sips.sendResult("fourteen", 1, fourteen);
        sips.endTask("14");

        sips.defineTask("15");
        sips.setDuration("15", 5);
        fifteen = (int[]) sips.resolveObject("fifteen", 0);
        sorter.sort(fifteen);
        sips.sendResult("fifteen", 1, fifteen);
        sips.endTask("15");

        sips.defineTask("16");
        sips.setDuration("16", 6);
        sixteen = (int[]) sips.resolveObject("sixteen", 0);
        sorter.sort(sixteen);
        sips.sendResult("sixteen", 1, sixteen);
        sips.endTask("16");

        sips.defineTask("17");
        sips.setDuration("17", 7);
        seventeen = (int[]) sips.resolveObject("seventeen", 0);
        sorter.sort(seventeen);
        sips.sendResult("seventeen", 1, seventeen);
        sips.endTask("17");

        sips.defineTask("18");
        sips.setDuration("18", 8);
        eighteen = (int[]) sips.resolveObject("eighteen", 0);
        sorter.sort(eighteen);
        sips.sendResult("eighteen", 1, eighteen);
        sips.endTask("18");

        sips.defineTask("19");
        sips.setDuration("19", 9);
        nineteen = (int[]) sips.resolveObject("nineteen", 0);
        sorter.sort(nineteen);
        sips.sendResult("nineteen", 1, nineteen);
        sips.endTask("19");

        sips.defineTask("20");
        sips.setDuration("20", 10);
        twenty = (int[]) sips.resolveObject("twenty", 0);
        sorter.sort(twenty);
        sips.sendResult("twenty", 1, twenty);
        sips.endTask("20");

        sips.defineTask("1-2");
        sips.setDuration("1-2", 3);
        sips.setTaskDependency("1-2", "1", "2");
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
        sips.setTaskDependency("3-4", "3", "4");
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
        sips.setTaskDependency("5-6", "5", "6");
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
        sips.setTaskDependency("7-8", "7", "8");
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
        sips.setTaskDependency("9-10", "9", "10");
        ninth = (int[]) sips.receiveResult("ninth", 1);
        tenth = (int[]) sips.receiveResult("tenth", 1);
        int[] n10 = new int[ninth.length + tenth.length];
        System.arraycopy(ninth, 0, n10, 0, ninth.length);
        System.arraycopy(tenth, 0, n10, ninth.length, tenth.length);
        sorter.sort(n10);
        sips.sendResult("n10", 0, n10);
        sips.endTask("9-10");

        sips.defineTask("11-12");
        sips.setDuration("11-12", 3);
        sips.setTaskDependency("11-12", "11", "12");
        eleven = (int[]) sips.receiveResult("eleven", 1);
        twelve = (int[]) sips.receiveResult("twelve", 1);
        int[] e12 = new int[eleven.length + twelve.length];
        System.arraycopy(eleven, 0, e12, 0, eleven.length);
        System.arraycopy(twelve, 0, e12, eleven.length, twelve.length);
        sorter.sort(e12);
        sips.sendResult("e12", 0, e12);
        sips.endTask("11-12");

        sips.defineTask("13-14");
        sips.setDuration("13-14", 7);
        sips.setTaskDependency("13-14", "13", "14");
        thirteen = (int[]) sips.receiveResult("thirteen", 1);
        fourteen = (int[]) sips.receiveResult("fourteen", 1);
        int[] t14 = new int[thirteen.length + fourteen.length];
        System.arraycopy(thirteen, 0, t14, 0, thirteen.length);
        System.arraycopy(fourteen, 0, t14, thirteen.length, fourteen.length);
        sorter.sort(t14);
        sips.sendResult("t14", 0, t14);
        sips.endTask("13-14");

        sips.defineTask("15-16");
        sips.setDuration("15-16", 11);
        sips.setTaskDependency("15-16", "15", "16");
        fifteen = (int[]) sips.receiveResult("fifteen", 1);
        sixteen = (int[]) sips.receiveResult("sixteen", 1);
        int[] f16 = new int[fifteen.length + sixteen.length];
        System.arraycopy(fifteen, 0, f16, 0, fifteen.length);
        System.arraycopy(sixteen, 0, f16, fifteen.length, sixteen.length);
        sorter.sort(f16);
        sips.sendResult("f16", 0, f16);
        sips.endTask("15-16");

        sips.defineTask("17-18");
        sips.setDuration("17-18", 15);
        sips.setTaskDependency("17-18", "17", "18");
        seventeen = (int[]) sips.receiveResult("seventeen", 1);
        eighteen = (int[]) sips.receiveResult("eighteen", 1);
        int[] s18 = new int[seventeen.length + eighteen.length];
        System.arraycopy(seventeen, 0, s18, 0, seventeen.length);
        System.arraycopy(eighteen, 0, s18, seventeen.length, eighteen.length);
        sorter.sort(s18);
        sips.sendResult("s18", 0, s18);
        sips.endTask("17-18");

        sips.defineTask("19-20");
        sips.setDuration("19-20", 19);
        sips.setTaskDependency("19-20", "19", "20");
        nineteen = (int[]) sips.receiveResult("nineteen", 1);
        twenty = (int[]) sips.receiveResult("twenty", 1);
        int[] n20 = new int[nineteen.length + twenty.length];
        System.arraycopy(nineteen, 0, n20, 0, nineteen.length);
        System.arraycopy(twenty, 0, n20, nineteen.length, twenty.length);
        sorter.sort(n20);
        sips.sendResult("n20", 0, n20);
        sips.endTask("19-20");

        sips.defineTask("1-2-3-4");
        sips.setDuration("1-2-3-4", 10);
        sips.setTaskDependency("1-2-3-4", "1-2", "3-4");
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
        sips.setTaskDependency("5-6-7-8", "5-6", "7-8");
        int[] f6_0 = (int[]) sips.receiveResult("f6", 0);
        int[] s8_0 = (int[]) sips.receiveResult("s8", 0);
        int[] f678 = new int[f6_0.length + s8_0.length];
        System.arraycopy(f6_0, 0, f678, 0, f6_0.length);
        System.arraycopy(s8_0, 0, f678, f6_0.length, s8_0.length);
        sorter.sort(f678);
        sips.sendResult("f678", 0, f678);
        sips.endTask("5-6-7-8");

        sips.defineTask("9-10-11-12");
        sips.setDuration("9-10-11-12", 10);
        sips.setTaskDependency("9-10-11-12", "9-10", "11-12");
        int[] n10_0 = (int[]) sips.receiveResult("n10", 0);
        int[] e12_0 = (int[]) sips.receiveResult("e12", 0);
        int[] n101112 = new int[n10_0.length + e12_0.length];
        System.arraycopy(n10_0, 0, n101112, 0, n10_0.length);
        System.arraycopy(e12_0, 0, n101112, n10_0.length, e12_0.length);
        sorter.sort(n101112);
        sips.sendResult("n101112", 0, n101112);
        sips.endTask("9-10-11-12");

        sips.defineTask("13-14-15-16");
        sips.setDuration("13-14-15-16", 26);
        sips.setTaskDependency("13-14-15-16", "13-14", "15-16");
        int[] t14_0 = (int[]) sips.receiveResult("t14", 0);
        int[] f16_0 = (int[]) sips.receiveResult("f16", 0);
        int[] t141516 = new int[t14_0.length + f16_0.length];
        System.arraycopy(t14_0, 0, t141516, 0, t14_0.length);
        System.arraycopy(f16_0, 0, t141516, t14_0.length, f16_0.length);
        sorter.sort(t141516);
        sips.sendResult("t141516", 0, t141516);
        sips.endTask("13-14-15-16");

        sips.defineTask("17-18-19-20");
        sips.setDuration("17-18-19-20", 26);
        sips.setTaskDependency("17-18-19-20", "17-18", "19-20");
        int[] s18_0 = (int[]) sips.receiveResult("s18", 0);
        int[] n20_0 = (int[]) sips.receiveResult("n20", 0);
        int[] s181920 = new int[s18_0.length + n20_0.length];
        System.arraycopy(s18_0, 0, s181920, 0, s18_0.length);
        System.arraycopy(n20_0, 0, s181920, s18_0.length, n20_0.length);
        sorter.sort(s181920);
        sips.sendResult("s181920", 0, s181920);
        sips.endTask("17-18-19-20");

        sips.defineTask("1-2-3-4-5-6-7-8");
        sips.setDuration("1-2-3-4-5-6-7-8", 36);
        sips.setTaskDependency("1-2-3-4-5-6-7-8", "1-2-3-4", "5-6-7-8");
        int[] f234_0 = (int[]) sips.receiveResult("f234", 0);
        int[] f678_0 = (int[]) sips.receiveResult("f678", 0);
        int[] f2345678 = new int[f234_0.length + f678_0.length];
        System.arraycopy(f234_0, 0, f2345678, 0, f234_0.length);
        System.arraycopy(f678_0, 0, f2345678, f234_0.length, f678_0.length);
        sorter.sort(f2345678);
        sips.sendResult("f2345678", 0, f2345678);
        sips.endTask("1-2-3-4-5-6-7-8");

        sips.defineTask("9-10-11-12-13-14-15-16");
        sips.setDuration("9-10-11-12-13-14-15-16", 36);
        sips.setTaskDependency("9-10-11-12-13-14-15-16", "9-10-11-12", "13-14-15-16");
        int[] n101112_0 = (int[]) sips.receiveResult("n101112", 0);
        int[] t141516_0 = (int[]) sips.receiveResult("t141516", 0);
        int[] n10111213141516 = new int[n101112_0.length + t141516_0.length];
        System.arraycopy(n101112_0, 0, n10111213141516, 0, n101112_0.length);
        System.arraycopy(t141516_0, 0, n10111213141516, n101112_0.length, t141516_0.length);
        sorter.sort(n10111213141516);
        sips.sendResult("n10111213141516", 0, n10111213141516);
        sips.endTask("9-10-11-12-13-14-15-16");

        sips.defineTask("1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16");
        sips.setDuration("1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16", 55);
        sips.setTaskDependency("1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16", "1-2-3-4-5-6-7-8", "9-10-11-12-13-14-15-16");
        int[] f2345678_0 = (int[]) sips.receiveResult("f2345678", 0);
        int[] n10111213141516_0 = (int[]) sips.receiveResult("n10111213141516", 0);
        int[] f2345678910111213141516 = new int[f2345678_0.length + n10111213141516_0.length];
        System.arraycopy(f2345678_0, 0, f2345678910111213141516, 0, f2345678_0.length);
        System.arraycopy(n10111213141516_0, 0, f2345678910111213141516, f2345678_0.length, n10111213141516_0.length);
        sorter.sort(f2345678910111213141516);
        sips.sendResult("f2345678910111213141516", 0, f2345678910111213141516);
        sips.endTask("1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16");

        sips.defineTask("1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18-19-20");
        sips.setDuration("1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18-19-20", 55);
        sips.setTaskDependency("1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18-19-20", "1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16","17-18-19-20");
        int[] f2345678910111213141516_0 = (int[]) sips.receiveResult("f2345678910111213141516", 0);
        int[] s181920_0 = (int[]) sips.receiveResult("s181920", 0);
        int[] f234567891011121314151617181920 = new int[f2345678910111213141516_0.length + s181920_0.length];
        System.arraycopy(f2345678910111213141516_0, 0, f234567891011121314151617181920, 0, f2345678910111213141516_0.length);
        System.arraycopy(s181920_0, 0, f234567891011121314151617181920, f2345678910111213141516_0.length, s181920_0.length);
        sorter.sort(f234567891011121314151617181920);
        sips.sendResult("f234567891011121314151617181920", 0, f234567891011121314151617181920);
        sips.endTask("1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18-19-20");

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
