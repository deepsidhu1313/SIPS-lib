/* 
 * Copyright (C) 2017 Navdeep Singh Sidhu
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 *
 * @author navdeep singh <navdeepsingh.sidhu95@gmail.com>
 */
public class ChunkCalculator implements Runnable {

    int Process_ID;
    String filename;
    int vartype;
    byte min_byte, max_byte, diff_byte, low_byte, up_byte, cs_byte, lcs_byte, lupper_byte, fc_byte, lc_byte, TSSred_byte, QSSa_byte, QSSb_byte, QSSc_byte, QSSn_byte;
    short min_short, max_short, diff_short, low_short, up_short, cs_short, lcs_short, lupper_short, fc_short, lc_short, TSSred_short, QSSa_short, QSSb_short, QSSc_short, QSSn_short;
    int min_int, max_int, diff_int, low_int, up_int, cs_int, lcs_int, lupper_int, fc_int, lc_int, TSSred_int, QSSa_int, QSSb_int, QSSc_int, QSSn_int;
    long min_long, max_long, diff_long, low_long, up_long, cs_long, lcs_long, lupper_long, fc_long, lc_long, TSSred_long, QSSa_long, QSSb_long, QSSc_long, QSSn_long;
    float min_float, max_float, diff_float, low_float, up_float, cs_float, lcs_float, lupper_float, fc_float, lc_float, TSSred_float, QSSa_float, QSSb_float, QSSc_float, QSSn_float;
    double min_double, max_double, diff_double, low_double, up_double, cs_double, lcs_double, lupper_double, fc_double, lc_double, TSSred_double, QSSa_double, QSSb_double, QSSc_double, QSSn_double;
    double FCFactor, LCFactor;
    double QSSdelta = 0, QSSLCFactor;
    boolean reverseloop = Boolean.FALSE;
    public static boolean chunksCreated = false;

    int bl = 0, scheduler;
    long startime, stoptime, diff, gstarttime, tempgpoh;
    String tchunks, tnodes, gpoh;
    String sizeofJob = "";
    ArrayList<String> poh = new ArrayList<>();
    //ArrayList<String> livenodes;
    ArrayList<String> Content = new ArrayList<>();
    ArrayList<String> ChunkSize = new ArrayList<>();
    ArrayList<String> lowList = new ArrayList<>();
    ArrayList<String> upList = new ArrayList<>();
    int totalnodes;

    public ChunkCalculator(int variabletype, boolean looptype, String LowerBoundProblem, String UpperBoundProblem, int Scheduler, double FirstChunkFactorTSS, double LastChunkFactorTSS, double deltaQSS, double LastChunkfactorQSS, int totalNodes) {
        chunksCreated = false;
        vartype = variabletype;
        reverseloop = looptype;
        scheduler = Scheduler;
        QSSdelta = deltaQSS;
        LCFactor = LastChunkFactorTSS;
        FCFactor = FirstChunkFactorTSS;
        QSSLCFactor = LastChunkfactorQSS;
        totalnodes = totalNodes;
//        System.out.println("Vartype " + variabletype + " LT: " + looptype + " Minimum:" + Min + "  Maximum:" + Max + " Scheduler:" + Scheduler + " FCF:" + fcf + " LCF:" + lcf + " DELTA" + delta + " QSSLCF:" + qsslcf + " TOTAL NODES:" + totalNodes);
        switch (variabletype) {
            case 0:

                min_byte = Byte.parseByte(LowerBoundProblem);
                max_byte = Byte.parseByte(UpperBoundProblem);
                diff_byte = (byte) (max_byte - min_byte);
                sizeofJob = "" + diff_byte;
//                System.out.println("Min:" + min_byte);
                break;

            case 1:
                min_short = Short.parseShort(LowerBoundProblem);
                max_short = Short.parseShort(UpperBoundProblem);
                diff_short = (short) (max_short - min_short);
                sizeofJob = "" + diff_short;

                break;
            case 2:
                min_int = Integer.parseInt(LowerBoundProblem);
                max_int = Integer.parseInt(UpperBoundProblem);
                diff_int = max_int - min_int;
                sizeofJob = "" + diff_int;
//                System.out.println("Min:" + min_int);
//                System.out.println("Max:" + max_int);
//                System.out.println("Diff:" + diff_int);
                break;
            case 3:
                min_long = Long.parseLong(LowerBoundProblem);
                max_long = Long.parseLong(UpperBoundProblem);
                diff_long = max_long - min_long;
                sizeofJob = "" + diff_long;
                break;
            case 4:
                min_float = Float.parseFloat(LowerBoundProblem);
                max_float = Float.parseFloat(UpperBoundProblem);
                diff_float = max_float - min_float;
                sizeofJob = "" + diff_float;
                break;
            case 5:
                min_double = Double.parseDouble(LowerBoundProblem);
                max_double = Double.parseDouble(UpperBoundProblem);
                diff_double = max_double - min_double;
                sizeofJob = "" + diff_double;

                break;
        }

    }

    public boolean createChunks() {
        String chunksize = null;
        String lower = null, upper = null;
        int i = 1;
        startime = System.currentTimeMillis();

        if (totalnodes == 1) {
            switch (vartype) {
                case 0:
                    lower = "" + min_byte;
                    upper = "" + max_byte;
                    chunksize = "" + diff_byte;
                    break;

                case 1:
                    lower = "" + min_short;
                    upper = "" + max_short;
                    chunksize = "" + diff_short;

                    break;
                case 2:
                    lower = "" + min_int;
                    upper = "" + max_int;
                    chunksize = "" + diff_int;
                    break;
                case 3:
                    lower = "" + min_long;
                    upper = "" + max_long;
                    chunksize = "" + diff_long;
                    break;
                case 4:
                    lower = "" + min_float;
                    upper = "" + max_float;
                    chunksize = "" + diff_float;
                    break;
                case 5:
                    lower = "" + min_double;
                    upper = "" + max_double;
                    chunksize = "" + diff_double;

                    break;
            }
            ChunkSize.add(chunksize.trim());
            lowList.add(lower.trim());
            upList.add(upper.trim());
            chunksCreated = true;
        }

        while (!chunksCreated) {

            switch (scheduler) {
                // Equal Chunk Size
                case 0:
                    switch (vartype) {
                        case 0:
                            chunksize = "" + diff_byte;
                            if (i == 1) {
                                low_byte = (byte) (((diff_byte / totalnodes) * (i - 1)));
                            } else {
                                low_byte = (byte) (((diff_byte / totalnodes) * (i - 1)) + 1);
                            }

                            up_byte = (byte) ((diff_byte / totalnodes) * (i));
                            if (reverseloop) {
                                lower = "" + (min_byte - low_byte);
                                if (i == totalnodes) {
                                    upper = "" + ((max_byte - (min_byte - up_byte)) + (min_byte - up_byte));
                                    chunksCreated = true;
                                } else {
                                    upper = "" + (min_byte - up_byte);
                                }

                            } else {
                                lower = "" + (min_byte + low_byte);
                                if (i == totalnodes) {
                                    upper = "" + ((max_byte - (min_byte + up_byte)) + (min_byte + up_byte));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_byte + up_byte);
                                }

                            }
                            break;
                        case 1:
                            chunksize = "" + diff_short;
                            if (i == 1) {
                                low_short = (short) (((diff_short / totalnodes) * (i - 1)));
                            } else {
                                low_short = (short) (((diff_short / totalnodes) * (i - 1)) + 1);
                            }
                            up_short = (short) ((diff_short / totalnodes) * (i));
                            if (reverseloop) {
                                lower = "" + (min_short - low_short);
                                if (i == totalnodes) {
                                    upper = "" + ((max_short - (min_short - up_short)) + (min_short - up_short));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_short - up_short);
                                }

                            } else {
                                lower = "" + (min_short + low_short);
                                if (i == totalnodes) {
                                    upper = "" + ((max_short - (min_short + up_short)) + (min_short + up_short));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_short + up_short);
                                }

                            }
                            break;
                        case 2:
                            chunksize = "" + diff_int;
                            if (i == 1) {
                                low_int = (((diff_int / totalnodes) * (i - 1)));
                            } else {
                                low_int = (((diff_int / totalnodes) * (i - 1)) + 1);
                            }
                            up_int = ((diff_int / totalnodes) * (i));
                            if (reverseloop) {
                                lower = "" + (min_int - low_int);
                                if (i == totalnodes) {
                                    upper = "" + ((max_int - (min_int - up_int)) + (min_int - up_int));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_int - up_int);
                                }

                            } else {
                                lower = "" + (min_int + low_int);
                                if (i == totalnodes) {
                                    upper = "" + ((max_int - (min_int + up_int)) + (min_int + up_int));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_int + up_int);
                                }

                            }
                            break;
                        case 3:

                            chunksize = "" + diff_long;
                            if (i == 1) {
                                low_long = (((diff_long / totalnodes) * (i - 1)));
                            } else {
                                low_long = (((diff_long / totalnodes) * (i - 1)) + 1);
                            }
                            up_long = ((diff_long / totalnodes) * (i));
                            if (reverseloop) {
                                lower = "" + (min_long - low_long);
                                if (i == totalnodes) {
                                    upper = "" + ((max_long - (min_long - up_long)) + (min_long - up_long));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_long - up_long);
                                }

                            } else {
                                lower = "" + (min_long + low_long);
                                if (i == totalnodes) {
                                    upper = "" + ((max_long - (min_long + up_long)) + (min_long + up_long));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_long + up_long);
                                }
                            }
                            break;
                        case 4:
                            chunksize = "" + diff_float;
                            if (i == 1) {
                                low_float = (((diff_float / totalnodes) * (i - 1)));
                            } else {
                                low_float = (((diff_float / totalnodes) * (i - 1)) + 1);
                            }
                            up_float = ((diff_float / totalnodes) * (i));
                            if (reverseloop) {
                                lower = "" + (min_float - low_float);
                                if (i == totalnodes) {
                                    upper = "" + ((max_float - (min_float - up_float)) + (min_float - up_float));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_float - up_float);
                                }
                            } else {
                                lower = "" + (min_float + low_float);
                                if (i == totalnodes) {
                                    upper = "" + ((max_float - (min_float + up_float)) + (min_float + up_float));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_float + up_float);
                                }

                            }
                            break;
                        case 5:
                            chunksize = "" + diff_double;
                            if (i == 1) {
                                low_double = (((diff_double / totalnodes) * (i - 1)));
                            } else {
                                low_double = (((diff_double / totalnodes) * (i - 1)) + 1);
                            }
                            up_double = ((diff_double / totalnodes) * (i));
                            if (reverseloop) {
                                lower = "" + (min_double - low_double);

                                if (i == totalnodes) {
                                    upper = "" + ((max_double - (min_double - up_double)) + (min_double - up_double));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_double - up_double);
                                }
                            } else {
                                lower = "" + (min_double + low_double);
                                if (i == totalnodes) {
                                    upper = "" + ((max_double - (min_double + up_double)) + (min_double + up_double));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_double + up_double);
                                }

                            }
                            break;

                    }
                    break;

                // GSS
                case 1:
                    switch (vartype) {
                        case 0:
                            cs_byte = (byte) Math.ceil((double) diff_byte / (double) totalnodes);
                            if (cs_byte < 1) {
                                cs_byte = 1;
                            }
                            chunksize = "" + cs_byte;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_byte = (byte) (0);
                                    low_byte = (byte) (min_byte - low_byte);

                                } else {
                                    low_byte = (byte) (lupper_byte - 1);
                                }

                                lower = "" + (low_byte);
                                up_byte = (byte) (low_byte - cs_byte + 1);
                                upper = "" + (up_byte);

                                if (lupper_byte <= max_byte) {
                                    upper = "" + (max_byte);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_byte = (byte) (0);
                                    low_byte = (byte) (min_byte + low_byte);

                                } else {

                                    low_byte = (byte) (lupper_byte + 1);

                                }
                                lower = "" + (low_byte);

                                up_byte = (byte) (low_byte + cs_byte - 1);
                                upper = "" + (up_byte);
                                if (up_byte >= max_byte) {
                                    upper = "" + (max_byte);
                                    chunksCreated = true;
                                }
                                lupper_byte = up_byte;
                                diff_byte = (byte) (diff_byte - cs_byte);
                            }
                            break;
                        case 1:
                            cs_short = (short) Math.ceil((double) diff_short / (double) totalnodes);
                            chunksize = "" + cs_short;
                            if (cs_short < 1) {
                                cs_short = 1;
                            }
                            if (reverseloop) {
                                if (i == 1) {
                                    low_short = (short) (0);
                                    low_short = (short) (min_short - low_short);

                                } else {
                                    low_short = (short) (lupper_short - 1);
                                }

                                lower = "" + (low_short);
                                up_short = (short) (low_short - cs_short + 1);
                                upper = "" + (up_short);

                                if (lupper_short <= max_short) {
                                    upper = "" + (max_short);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_short = (short) (0);
                                    low_short = (short) (min_short + low_short);

                                } else {

                                    low_short = (short) (lupper_short + 1);

                                }
                                lower = "" + (low_short);

                                up_short = (short) (low_short + cs_short - 1);
                                upper = "" + (up_short);
                                if (up_short >= max_short) {
                                    upper = "" + (max_short);
                                    chunksCreated = true;
                                }
                                lupper_short = up_short;
                                diff_short = (short) (diff_short - cs_short);
                            }
                            break;
                        case 2:
                            cs_int = (int) Math.ceil((double) diff_int / (double) totalnodes);
                            chunksize = "" + cs_int;
                            if (cs_int < 1) {
                                cs_int = 1;
                            }
                            if (reverseloop) {
                                if (i == 1) {
                                    low_int = (int) (0);
                                    low_int = (int) (min_int - low_int);

                                } else {
                                    low_int = (int) (lupper_int - 1);
                                }

                                lower = "" + (low_int);
                                up_int = (int) (low_int - cs_int + 1);
                                upper = "" + (up_int);

                                if (lupper_int <= max_int) {
                                    upper = "" + (max_int);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_int = (int) (0);
                                    low_int = (int) (min_int + low_int);

                                } else {

                                    low_int = (int) (lupper_int + 1);

                                }
                                lower = "" + (low_int);

                                up_int = (int) (low_int + cs_int - 1);
                                upper = "" + (up_int);
                                if (up_int >= max_int) {
                                    upper = "" + (max_int);
                                    chunksCreated = true;
                                }
                                lupper_int = up_int;
                                diff_int = (int) (diff_int - cs_int);
                            }
                            break;
                        case 3:
                            cs_long = (long) Math.ceil((double) diff_long / (double) totalnodes);
                            chunksize = "" + cs_long;
                            if (cs_long < 1) {
                                cs_long = 1;
                            }
                            if (reverseloop) {
                                if (i == 1) {
                                    low_long = (long) (0);
                                    low_long = (long) (min_long - low_long);

                                } else {
                                    low_long = (long) (lupper_long - 1);
                                }

                                lower = "" + (low_long);
                                up_long = (long) (low_long - cs_long + 1);
                                upper = "" + (up_long);

                                if (lupper_long <= max_long) {
                                    upper = "" + (max_long);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_long = (long) (0);
                                    low_long = (long) (min_long + low_long);

                                } else {

                                    low_long = (long) (lupper_long + 1);

                                }
                                lower = "" + (low_long);

                                up_long = (long) (low_long + cs_long - 1);
                                upper = "" + (up_long);
                                if (up_long >= max_long) {
                                    upper = "" + (max_long);
                                    chunksCreated = true;
                                }
                                lupper_long = up_long;
                                diff_long = (long) (diff_long - cs_long);
                            }
                            break;
                        case 4:
                            cs_float = (float) Math.ceil((double) diff_float / (double) totalnodes);
                            chunksize = "" + cs_float;
                            if (cs_float < 1) {
                                cs_float = 1;
                            }
                            if (reverseloop) {
                                if (i == 1) {
                                    low_float = (float) (0);
                                    low_float = (float) (min_float - low_float);

                                } else {
                                    low_float = (float) (lupper_float - 1);
                                }

                                lower = "" + (low_float);
                                up_float = (float) (low_float - cs_float + 1);
                                upper = "" + (up_float);

                                if (lupper_float <= max_float) {
                                    upper = "" + (max_float);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_float = (float) (0);
                                    low_float = (float) (min_float + low_float);

                                } else {

                                    low_float = (float) (lupper_float + 1);

                                }
                                lower = "" + (low_float);

                                up_float = (float) (low_float + cs_float - 1);
                                upper = "" + (up_float);
                                if (up_float >= max_float) {
                                    upper = "" + (max_float);
                                    chunksCreated = true;
                                }
                                lupper_float = up_float;
                                diff_float = (float) (diff_float - cs_float);
                            }
                            break;
                        case 5:
                            cs_double = (double) Math.ceil((double) diff_double / (double) totalnodes);
                            chunksize = "" + cs_double;
                            if (cs_double < 1) {
                                cs_double = 1;
                            }
                            if (reverseloop) {
                                if (i == 1) {
                                    low_double = (double) (0);
                                    low_double = (double) (min_double - low_double);

                                } else {
                                    low_double = (double) (lupper_double - 1);
                                }

                                lower = "" + (low_double);
                                up_double = (double) (low_double - cs_double + 1);
                                upper = "" + (up_double);

                                if (lupper_double <= max_double) {
                                    upper = "" + (max_double);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_double = (double) (0);
                                    low_double = (double) (min_double + low_double);

                                } else {

                                    low_double = (double) (lupper_double + 1);

                                }
                                lower = "" + (low_double);

                                up_double = (double) (low_double + cs_double - 1);
                                upper = "" + (up_double);
                                if (up_double >= max_double) {
                                    upper = "" + (max_double);
                                    chunksCreated = true;
                                }
                                lupper_double = up_double;
                                diff_double = (double) (diff_double - cs_double);
                            }
                            break;

                    }

                    break;

                //Factoring
                case 2:
                    switch (vartype) {
                        case 0:
                            cs_byte = (byte) Math.ceil((double) diff_byte / (double) (2 * totalnodes));
                            if (cs_byte < 1) {
                                cs_byte = 1;
                            }
                            chunksize = "" + cs_byte;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_byte = (byte) (0);
                                    low_byte = (byte) (min_byte - low_byte);

                                } else {
                                    low_byte = (byte) (lupper_byte - 1);
                                }

                                lower = "" + (low_byte);
                                up_byte = (byte) (low_byte - cs_byte + 1);
                                upper = "" + (up_byte);

                                if (lupper_byte <= max_byte) {
                                    upper = "" + (max_byte);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_byte = (byte) (0);
                                    low_byte = (byte) (min_byte + low_byte);

                                } else {

                                    low_byte = (byte) (lupper_byte + 1);

                                }
                                lower = "" + (low_byte);

                                up_byte = (byte) (low_byte + cs_byte - 1);
                                upper = "" + (up_byte);
                                if (up_byte >= max_byte) {
                                    upper = "" + (max_byte);
                                    chunksCreated = true;
                                }
                                lupper_byte = up_byte;
                                if (i % totalnodes == 0) {
                                    diff_byte = (byte) (diff_byte - (cs_byte * totalnodes));
                                }
                            }
                            break;
                        case 1:
                            cs_short = (short) Math.ceil((double) diff_short / (double) (2 * totalnodes));
                            if (cs_short < 1) {
                                cs_short = 1;
                            }
                            chunksize = "" + cs_short;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_short = (short) (0);
                                    low_short = (short) (min_short - low_short);

                                } else {
                                    low_short = (short) (lupper_short - 1);
                                }

                                lower = "" + (low_short);
                                up_short = (short) (low_short - cs_short + 1);
                                upper = "" + (up_short);

                                if (lupper_short <= max_short) {
                                    upper = "" + (max_short);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_short = (short) (0);
                                    low_short = (short) (min_short + low_short);

                                } else {

                                    low_short = (short) (lupper_short + 1);

                                }
                                lower = "" + (low_short);

                                up_short = (short) (low_short + cs_short - 1);
                                upper = "" + (up_short);
                                if (up_short >= max_short) {
                                    upper = "" + (max_short);
                                    chunksCreated = true;
                                }
                                lupper_short = up_short;
                                if (i % totalnodes == 0) {
                                    diff_short = (short) (diff_short - (cs_short * totalnodes));
                                }
                            }
                            break;
                        case 2:
                            cs_int = (int) Math.ceil((double) diff_int / (double) (2 * totalnodes));
                            if (cs_int < 1) {
                                cs_int = 1;
                            }
                            chunksize = "" + cs_int;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_int = (int) (0);
                                    low_int = (int) (min_int - low_int);

                                } else {
                                    low_int = (int) (lupper_int - 1);
                                }

                                lower = "" + (low_int);
                                up_int = (int) (low_int - cs_int + 1);
                                upper = "" + (up_int);

                                if (lupper_int <= max_int) {
                                    upper = "" + (max_int);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_int = (int) (0);
                                    low_int = (int) (min_int + low_int);

                                } else {

                                    low_int = (int) (lupper_int + 1);

                                }
                                lower = "" + (low_int);

                                up_int = (int) (low_int + cs_int - 1);
                                upper = "" + (up_int);
                                if (up_int >= max_int) {
                                    upper = "" + (max_int);
                                    chunksCreated = true;
                                }
                                lupper_int = up_int;
                                if (i % totalnodes == 0) {
                                    diff_int = (int) (diff_int - (cs_int * totalnodes));
                                }
                            }
                            break;
                        case 3:
                            cs_long = (long) Math.ceil((double) diff_long / ((double) 2 * totalnodes));
                            if (cs_long < 1) {
                                cs_long = 1;
                            }
                            chunksize = "" + cs_long;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_long = (long) (0);
                                    low_long = (long) (min_long - low_long);

                                } else {
                                    low_long = (long) (lupper_long - 1);
                                }

                                lower = "" + (low_long);
                                up_long = (long) (low_long - cs_long + 1);
                                upper = "" + (up_long);

                                if (lupper_long <= max_long) {
                                    upper = "" + (max_long);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_long = (long) (0);
                                    low_long = (long) (min_long + low_long);

                                } else {

                                    low_long = (long) (lupper_long + 1);

                                }
                                lower = "" + (low_long);

                                up_long = (long) (low_long + cs_long - 1);
                                upper = "" + (up_long);
                                if (up_long >= max_long) {
                                    upper = "" + (max_long);
                                    chunksCreated = true;
                                }
                                lupper_long = up_long;
                                if (i % totalnodes == 0) {
                                    diff_long = (long) (diff_long - (cs_long * totalnodes));
                                }
                            }
                            break;
                        case 4:
                            cs_float = (float) Math.ceil((double) diff_float / (double) (2 * totalnodes));
                            if (cs_float < 1) {
                                cs_float = 1;
                            }
                            chunksize = "" + cs_float;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_float = (float) (0);
                                    low_float = (float) (min_float - low_float);

                                } else {
                                    low_float = (float) (lupper_float - 1);
                                }

                                lower = "" + (low_float);
                                up_float = (float) (low_float - cs_float + 1);
                                upper = "" + (up_float);

                                if (lupper_float <= max_float) {
                                    upper = "" + (max_float);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_float = (float) (0);
                                    low_float = (float) (min_float + low_float);

                                } else {

                                    low_float = (float) (lupper_float + 1);

                                }
                                lower = "" + (low_float);

                                up_float = (float) (low_float + cs_float - 1);
                                upper = "" + (up_float);
                                if (up_float >= max_float) {
                                    upper = "" + (max_float);
                                    chunksCreated = true;
                                }
                                lupper_float = up_float;
                                if (i % totalnodes == 0) {
                                    diff_float = (float) (diff_float - (cs_float * totalnodes));
                                }
                            }
                            break;
                        case 5:
                            cs_double = (double) Math.ceil((double) diff_double / (double) (2 * totalnodes));
                            if (cs_double < 1) {
                                cs_double = 1;
                            }
                            chunksize = "" + cs_double;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_double = (double) (0);
                                    low_double = (double) (min_double - low_double);

                                } else {
                                    low_double = (double) (lupper_double - 1);
                                }

                                lower = "" + (low_double);
                                up_double = (double) (low_double - cs_double + 1);
                                upper = "" + (up_double);

                                if (lupper_double <= max_double) {
                                    upper = "" + (max_double);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_double = (double) (0);
                                    low_double = (double) (min_double + low_double);

                                } else {

                                    low_double = (double) (lupper_double + 1);

                                }
                                lower = "" + (low_double);

                                up_double = (double) (low_double + cs_double - 1);
                                upper = "" + (up_double);
                                if (up_double >= max_double) {
                                    upper = "" + (max_double);
                                    chunksCreated = true;
                                }
                                lupper_double = up_double;
                                if (i % totalnodes == 0) {
                                    diff_double = (double) (diff_double - (cs_double * totalnodes));
                                }
                            }
                            break;

                    }
                    break;

                // TSS
                case 3:
                    switch (vartype) {
                        case 0:
                            if (i == 1) {
                                fc_byte = (byte) (FCFactor * diff_byte);
                                lc_byte = (byte) (LCFactor * diff_byte);
                                byte M = (byte) ((2 * diff_byte) / (fc_byte + lc_byte));
                                TSSred_byte = (byte) ((fc_byte - lc_byte) / (M - 1));
                                cs_byte = fc_byte;
                            } else {
                                cs_byte = (byte) (lcs_byte - TSSred_byte);
                            }
                            lcs_byte = cs_byte;

                            chunksize = "" + cs_byte;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_byte = (byte) (0);
                                    low_byte = (byte) (min_byte - low_byte);

                                } else {
                                    low_byte = (byte) (lupper_byte - 1);
                                }

                                lower = "" + (low_byte);
                                up_byte = (byte) (low_byte - cs_byte + 1);
                                upper = "" + (up_byte);

                                if (lupper_byte <= max_byte) {
                                    upper = "" + (max_byte);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_byte = (byte) (0);
                                    low_byte = (byte) (min_byte + low_byte);

                                } else {

                                    low_byte = (byte) (lupper_byte + 1);

                                }
                                lower = "" + (low_byte);

                                up_byte = (byte) (low_byte + cs_byte - 1);
                                upper = "" + (up_byte);
                                if (up_byte >= max_byte) {
                                    upper = "" + (max_byte);
                                    chunksCreated = true;
                                }
                                lupper_byte = up_byte;
                                if (i % totalnodes == 0) {
                                    diff_byte = (byte) (diff_byte - (cs_byte * totalnodes));
                                }
                            }
                            break;
                        case 1:
                            if (i == 1) {
                                fc_short = (short) (FCFactor * diff_short);
                                lc_short = (short) (LCFactor * diff_short);
                                short M = (short) ((2 * diff_short) / (fc_short + lc_short));
                                TSSred_short = (short) ((fc_short - lc_short) / (M - 1));
                                cs_short = fc_short;
                            } else {
                                cs_short = (short) (lcs_short - TSSred_short);
                            }
                            lcs_short = cs_short;
                            chunksize = "" + cs_short;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_short = (short) (0);
                                    low_short = (short) (min_short - low_short);

                                } else {
                                    low_short = (short) (lupper_short - 1);
                                }

                                lower = "" + (low_short);
                                up_short = (short) (low_short - cs_short + 1);
                                upper = "" + (up_short);

                                if (lupper_short <= max_short) {
                                    upper = "" + (max_short);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_short = (short) (0);
                                    low_short = (short) (min_short + low_short);

                                } else {

                                    low_short = (short) (lupper_short + 1);

                                }
                                lower = "" + (low_short);

                                up_short = (short) (low_short + cs_short - 1);
                                upper = "" + (up_short);
                                if (up_short >= max_short) {
                                    upper = "" + (max_short);
                                    chunksCreated = true;
                                }
                                lupper_short = up_short;
                                if (i % totalnodes == 0) {
                                    diff_short = (short) (diff_short - (cs_short * totalnodes));
                                }
                            }
                            break;
                        case 2:

                            if (i == 1) {
                                fc_int = (int) (FCFactor * diff_int);
                                lc_int = (int) (LCFactor * diff_int);
                                int M = (int) ((2 * diff_int) / (fc_int + lc_int));
                                TSSred_int = (int) ((fc_int - lc_int) / (M - 1));
                                cs_int = fc_int;
                            } else {
                                cs_int = (int) (lcs_int - TSSred_int);
                            }
                            lcs_int = cs_int;
                            chunksize = "" + cs_int;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_int = (int) (0);
                                    low_int = (int) (min_int - low_int);

                                } else {
                                    low_int = (int) (lupper_int - 1);
                                }

                                lower = "" + (low_int);
                                up_int = (int) (low_int - cs_int + 1);
                                upper = "" + (up_int);

                                if (lupper_int <= max_int) {
                                    upper = "" + (max_int);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_int = (int) (0);
                                    low_int = (int) (min_int + low_int);

                                } else {

                                    low_int = (int) (lupper_int + 1);

                                }
                                lower = "" + (low_int);

                                up_int = (int) (low_int + cs_int - 1);
                                upper = "" + (up_int);
                                if (up_int >= max_int) {
                                    upper = "" + (max_int);
                                    chunksCreated = true;
                                }
                                lupper_int = up_int;
                                if (i % totalnodes == 0) {
                                    diff_int = (int) (diff_int - (cs_int * totalnodes));
                                }
                            }
                            break;
                        case 3:

                            if (i == 1) {
                                fc_long = (long) (FCFactor * diff_long);
                                lc_long = (long) (LCFactor * diff_long);
                                long M = (long) ((2 * diff_long) / (fc_long + lc_long));
                                TSSred_long = (long) ((fc_long - lc_long) / (M - 1));
                                cs_long = fc_long;
                            } else {
                                cs_long = (long) (lcs_long - TSSred_long);
                            }
                            lcs_long = cs_long;
                            chunksize = "" + cs_long;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_long = (long) (0);
                                    low_long = (long) (min_long - low_long);

                                } else {
                                    low_long = (long) (lupper_long - 1);
                                }

                                lower = "" + (low_long);
                                up_long = (long) (low_long - cs_long + 1);
                                upper = "" + (up_long);

                                if (lupper_long <= max_long) {
                                    upper = "" + (max_long);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_long = (long) (0);
                                    low_long = (long) (min_long + low_long);

                                } else {

                                    low_long = (long) (lupper_long + 1);

                                }
                                lower = "" + (low_long);

                                up_long = (long) (low_long + cs_long - 1);
                                upper = "" + (up_long);
                                if (up_long >= max_long) {
                                    upper = "" + (max_long);
                                    chunksCreated = true;
                                }
                                lupper_long = up_long;
                                if (i % totalnodes == 0) {
                                    diff_long = (long) (diff_long - (cs_long * totalnodes));
                                }
                            }
                            break;
                        case 4:

                            if (i == 1) {
                                fc_float = (float) (FCFactor * diff_float);
                                lc_float = (float) (LCFactor * diff_float);
                                float M = (float) ((2 * diff_float) / (fc_float + lc_float));
                                TSSred_float = (float) ((fc_float - lc_float) / (M - 1));
                                cs_float = fc_float;
                            } else {
                                cs_float = (float) (lcs_float - TSSred_float);
                            }
                            lcs_float = cs_float;
                            chunksize = "" + cs_float;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_float = (float) (0);
                                    low_float = (float) (min_float - low_float);

                                } else {
                                    low_float = (float) (lupper_float - 1);
                                }

                                lower = "" + (low_float);
                                up_float = (float) (low_float - cs_float + 1);
                                upper = "" + (up_float);

                                if (lupper_float <= max_float) {
                                    upper = "" + (max_float);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_float = (float) (0);
                                    low_float = (float) (min_float + low_float);

                                } else {

                                    low_float = (float) (lupper_float + 1);

                                }
                                lower = "" + (low_float);

                                up_float = (float) (low_float + cs_float - 1);
                                upper = "" + (up_float);
                                if (up_float >= max_float) {
                                    upper = "" + (max_float);
                                    chunksCreated = true;
                                }
                                lupper_float = up_float;
                                if (i % totalnodes == 0) {
                                    diff_float = (float) (diff_float - (cs_float * totalnodes));
                                }
                            }
                            break;
                        case 5:

                            if (i == 1) {
                                fc_double = (double) (FCFactor * diff_double);
                                lc_double = (double) (LCFactor * diff_double);
                                double M = (double) ((2 * diff_double) / (fc_double + lc_double));
                                TSSred_double = (double) ((fc_double - lc_double) / (M - 1));
                                cs_double = fc_double;
                            } else {
                                cs_double = (double) (lcs_double - TSSred_double);
                            }
                            lcs_double = cs_double;
                            chunksize = "" + cs_double;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_double = (double) (0);
                                    low_double = (double) (min_double - low_double);

                                } else {
                                    low_double = (double) (lupper_double - 1);
                                }

                                lower = "" + (low_double);
                                up_double = (double) (low_double - cs_double + 1);
                                upper = "" + (up_double);

                                if (lupper_double <= max_double) {
                                    upper = "" + (max_double);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_double = (double) (0);
                                    low_double = (double) (min_double + low_double);

                                } else {

                                    low_double = (double) (lupper_double + 1);

                                }
                                lower = "" + (low_double);

                                up_double = (double) (low_double + cs_double - 1);
                                upper = "" + (up_double);
                                if (up_double >= max_double) {
                                    upper = "" + (max_double);
                                    chunksCreated = true;
                                }
                                lupper_double = up_double;
                                if (i % totalnodes == 0) {
                                    diff_double = (double) (diff_double - (cs_double * totalnodes));
                                }
                            }
                            break;

                    }

                    break;

                // QSS
                case 4:
                    switch (vartype) {
                        case 0:
                            if (i == 1) {
                                fc_byte = (byte) (diff_byte / (2 * totalnodes));
                                lc_byte = (byte) (QSSLCFactor * diff_byte);
                                if (lc_byte < 1) {
                                    lc_byte = 1;
                                }
                                byte CN2 = (byte) ((fc_byte + lc_byte) / (QSSdelta));
                                QSSn_byte = (byte) ((6 * diff_byte) / ((4 * CN2) + lc_byte + fc_byte));
                                QSSa_byte = fc_byte;
                                QSSb_byte = (byte) (((4 * CN2) - lc_byte - (3 * fc_byte)) / QSSn_byte);
                                QSSc_byte = (byte) (((2 * fc_byte) + (2 * lc_byte) - (4 * CN2)) / (QSSn_byte * QSSn_byte));
                                cs_byte = fc_byte;
                            } else {
                                cs_byte = (byte) (QSSa_byte + (QSSb_byte * i) + (QSSc_byte * (i * i)));
                            }
                            if (cs_byte < 1) {
                                cs_byte = 1;
                            }
                            lcs_byte = cs_byte;

                            chunksize = "" + cs_byte;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_byte = (byte) (0);
                                    low_byte = (byte) (min_byte - low_byte);

                                } else {
                                    low_byte = (byte) (lupper_byte - 1);
                                }

                                lower = "" + (low_byte);
                                up_byte = (byte) (low_byte - cs_byte + 1);
                                upper = "" + (up_byte);

                                if (lupper_byte <= max_byte) {
                                    upper = "" + (max_byte);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_byte = (byte) (0);
                                    low_byte = (byte) (min_byte + low_byte);

                                } else {

                                    low_byte = (byte) (lupper_byte + 1);

                                }
                                lower = "" + (low_byte);

                                up_byte = (byte) (low_byte + cs_byte - 1);
                                upper = "" + (up_byte);
                                if (up_byte >= max_byte) {
                                    upper = "" + (max_byte);
                                    chunksCreated = true;
                                }
                                lupper_byte = up_byte;
                                if (i % totalnodes == 0) {
                                    diff_byte = (byte) (diff_byte - (cs_byte * totalnodes));
                                }
                            }
                            break;
                        case 1:
                            if (i == 1) {
                                fc_short = (short) (diff_short / (2 * totalnodes));
                                lc_short = (short) (QSSLCFactor * diff_short);
                                if (lc_short < 1) {
                                    lc_short = 1;
                                }
                                short CN2 = (short) ((fc_short + lc_short) / (QSSdelta));
                                QSSn_short = (short) ((6 * diff_short) / ((4 * CN2) + lc_short + fc_short));
                                QSSa_short = fc_short;
                                QSSb_short = (short) (((4 * CN2) - lc_short - (3 * fc_short)) / QSSn_short);
                                QSSc_short = (short) (((2 * fc_short) + (2 * lc_short) - (4 * CN2)) / (QSSn_short * QSSn_short));
                                cs_short = fc_short;
                            } else {
                                cs_short = (short) (QSSa_short + (QSSb_short * i) + (QSSc_short * (i * i)));
                            }

                            if (cs_short < 1) {
                                cs_short = 1;
                            }
                            lcs_short = cs_short;
                            chunksize = "" + cs_short;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_short = (short) (0);
                                    low_short = (short) (min_short - low_short);

                                } else {
                                    low_short = (short) (lupper_short - 1);
                                }

                                lower = "" + (low_short);
                                up_short = (short) (low_short - cs_short + 1);
                                upper = "" + (up_short);

                                if (lupper_short <= max_short) {
                                    upper = "" + (max_short);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_short = (short) (0);
                                    low_short = (short) (min_short + low_short);

                                } else {

                                    low_short = (short) (lupper_short + 1);

                                }
                                lower = "" + (low_short);

                                up_short = (short) (low_short + cs_short - 1);
                                upper = "" + (up_short);
                                if (up_short >= max_short) {
                                    upper = "" + (max_short);
                                    chunksCreated = true;
                                }
                                lupper_short = up_short;
                                if (i % totalnodes == 0) {
                                    diff_short = (short) (diff_short - (cs_short * totalnodes));
                                }
                            }
                            break;
                        case 2:
                            if (i == 1) {
                                fc_int = (int) (diff_int / (2 * totalnodes));
                                lc_int = (int) (QSSLCFactor * diff_int);
                                if (lc_int < 1) {
                                    lc_int = 1;
                                }

                                int CN2 = (int) ((fc_int + lc_int) / (QSSdelta));
                                QSSn_int = (int) ((6 * diff_int) / ((4 * CN2) + lc_int + fc_int));
                                QSSa_int = fc_int;
                                QSSb_int = (int) (((4 * CN2) - lc_int - (3 * fc_int)) / QSSn_int);
                                QSSc_int = (int) (((2 * fc_int) + (2 * lc_int) - (4 * CN2)) / (QSSn_int * QSSn_int));
                                cs_int = fc_int;
                            } else {
                                cs_int = (int) (QSSa_int + (QSSb_int * i) + (QSSc_int * (i * i)));
                            }

                            if (cs_int < 1) {
                                cs_int = 1;
                            }
                            lcs_int = cs_int;
                            chunksize = "" + cs_int;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_int = (int) (0);
                                    low_int = (int) (min_int - low_int);

                                } else {
                                    low_int = (int) (lupper_int - 1);
                                }

                                lower = "" + (low_int);
                                up_int = (int) (low_int - cs_int + 1);
                                upper = "" + (up_int);

                                if (lupper_int <= max_int) {
                                    upper = "" + (max_int);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_int = (int) (0);
                                    low_int = (int) (min_int + low_int);

                                } else {

                                    low_int = (int) (lupper_int + 1);

                                }
                                lower = "" + (low_int);

                                up_int = (int) (low_int + cs_int - 1);
                                upper = "" + (up_int);
                                if (up_int >= max_int) {
                                    upper = "" + (max_int);
                                    chunksCreated = true;
                                }
                                lupper_int = up_int;
                                if (i % totalnodes == 0) {
                                    diff_int = (int) (diff_int - (cs_int * totalnodes));
                                }
                            }
                            break;
                        case 3:

                            if (i == 1) {
                                fc_long = (long) (diff_long / (2 * totalnodes));
                                lc_long = (long) (QSSLCFactor * diff_long);
                                if (lc_long < 1) {
                                    lc_long = 1;
                                }

                                long CN2 = (long) ((fc_long + lc_long) / (QSSdelta));
                                QSSn_long = (long) ((6 * diff_long) / ((4 * CN2) + lc_long + fc_long));
                                QSSa_long = fc_long;
                                QSSb_long = (long) (((4 * CN2) - lc_long - (3 * fc_long)) / QSSn_long);
                                QSSc_long = (long) (((2 * fc_long) + (2 * lc_long) - (4 * CN2)) / (QSSn_long * QSSn_long));
                                cs_long = fc_long;
                            } else {
                                cs_long = (long) (QSSa_long + (QSSb_long * i) + (QSSc_long * (i * i)));
                            }

                            if (cs_long < 1) {
                                cs_long = 1;
                            }

                            lcs_long = cs_long;
                            chunksize = "" + cs_long;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_long = (long) (0);
                                    low_long = (long) (min_long - low_long);

                                } else {
                                    low_long = (long) (lupper_long - 1);
                                }

                                lower = "" + (low_long);
                                up_long = (long) (low_long - cs_long + 1);
                                upper = "" + (up_long);

                                if (lupper_long <= max_long) {
                                    upper = "" + (max_long);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_long = (long) (0);
                                    low_long = (long) (min_long + low_long);

                                } else {

                                    low_long = (long) (lupper_long + 1);

                                }
                                lower = "" + (low_long);

                                up_long = (long) (low_long + cs_long - 1);
                                upper = "" + (up_long);
                                if (up_long >= max_long) {
                                    upper = "" + (max_long);
                                    chunksCreated = true;
                                }
                                lupper_long = up_long;
                                if (i % totalnodes == 0) {
                                    diff_long = (long) (diff_long - (cs_long * totalnodes));
                                }
                            }
                            break;
                        case 4:

                            if (i == 1) {
                                fc_float = (float) (diff_float / (2 * totalnodes));
                                lc_float = (float) (QSSLCFactor * diff_float);
                                if (lc_float < 1) {
                                    lc_float = 1;
                                }
                                float CN2 = (float) ((fc_float + lc_float) / (QSSdelta));
                                QSSn_float = (float) ((6 * diff_float) / ((4 * CN2) + lc_float + fc_float));
                                QSSa_float = fc_float;
                                QSSb_float = (float) (((4 * CN2) - lc_float - (3 * fc_float)) / QSSn_float);
                                QSSc_float = (float) (((2 * fc_float) + (2 * lc_float) - (4 * CN2)) / (QSSn_float * QSSn_float));
                                cs_float = fc_float;
                            } else {
                                cs_float = (float) (QSSa_float + (QSSb_float * i) + (QSSc_float * (i * i)));
                            }

                            if (cs_float < 1) {
                                cs_float = 1;
                            }

                            lcs_float = cs_float;
                            chunksize = "" + cs_float;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_float = (float) (0);
                                    low_float = (float) (min_float - low_float);

                                } else {
                                    low_float = (float) (lupper_float - 1);
                                }

                                lower = "" + (low_float);
                                up_float = (float) (low_float - cs_float + 1);
                                upper = "" + (up_float);

                                if (lupper_float <= max_float) {
                                    upper = "" + (max_float);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_float = (float) (0);
                                    low_float = (float) (min_float + low_float);

                                } else {

                                    low_float = (float) (lupper_float + 1);

                                }
                                lower = "" + (low_float);

                                up_float = (float) (low_float + cs_float - 1);
                                upper = "" + (up_float);
                                if (up_float >= max_float) {
                                    upper = "" + (max_float);
                                    chunksCreated = true;
                                }
                                lupper_float = up_float;
                                if (i % totalnodes == 0) {
                                    diff_float = (float) (diff_float - (cs_float * totalnodes));
                                }
                            }
                            break;
                        case 5:

                            if (i == 1) {
                                fc_double = (double) (diff_double / (2 * totalnodes));
                                lc_double = (double) (QSSLCFactor * diff_double);
                                if (lc_double < 1) {
                                    lc_double = 1;
                                }

                                double CN2 = (double) ((fc_double + lc_double) / (QSSdelta));
                                QSSn_double = (double) ((6 * diff_double) / ((4 * CN2) + lc_double + fc_double));
                                QSSa_double = fc_double;
                                QSSb_double = (double) (((4 * CN2) - lc_double - (3 * fc_double)) / QSSn_double);
                                QSSc_double = (double) (((2 * fc_double) + (2 * lc_double) - (4 * CN2)) / (QSSn_double * QSSn_double));
                                cs_double = fc_double;
                            } else {
                                cs_double = (double) (QSSa_double + (QSSb_double * i) + (QSSc_double * (i * i)));
                            }

                            if (cs_double < 1) {
                                cs_double = 1;
                            }
                            lcs_double = cs_double;
                            chunksize = "" + cs_double;

                            if (reverseloop) {
                                if (i == 1) {
                                    low_double = (double) (0);
                                    low_double = (double) (min_double - low_double);

                                } else {
                                    low_double = (double) (lupper_double - 1);
                                }

                                lower = "" + (low_double);
                                up_double = (double) (low_double - cs_double + 1);
                                upper = "" + (up_double);

                                if (lupper_double <= max_double) {
                                    upper = "" + (max_double);
                                    chunksCreated = true;
                                }

                            } else {

                                if (i == 1) {

                                    low_double = (double) (0);
                                    low_double = (double) (min_double + low_double);

                                } else {

                                    low_double = (double) (lupper_double + 1);

                                }
                                lower = "" + (low_double);

                                up_double = (double) (low_double + cs_double - 1);
                                upper = "" + (up_double);
                                if (up_double >= max_double) {
                                    upper = "" + (max_double);
                                    chunksCreated = true;
                                }
                                lupper_double = up_double;
                                if (i % totalnodes == 0) {
                                    diff_double = (double) (diff_double - (cs_double * totalnodes));
                                }
                            }
                            break;

                    }

                    break;

                //ESS
                case 5:
                    switch (vartype) {
                        case 0:
                            chunksize = "" + diff_byte;
                            if (i == 1) {
                                low_byte = (byte) (((diff_byte / totalnodes) * (i - 1)));
                            } else {
                                low_byte = (byte) (((diff_byte / totalnodes) * (i - 1)) + 1);
                            }

                            up_byte = (byte) ((diff_byte / totalnodes) * (i));
                            if (reverseloop) {
                                lower = "" + (min_byte - low_byte);
                                if (i == totalnodes) {
                                    upper = "" + ((max_byte - (min_byte - up_byte)) + (min_byte - up_byte));
                                    chunksCreated = true;
                                } else {
                                    upper = "" + (min_byte - up_byte);
                                }

                            } else {
                                lower = "" + (min_byte + low_byte);
                                if (i == totalnodes) {
                                    upper = "" + ((max_byte - (min_byte + up_byte)) + (min_byte + up_byte));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_byte + up_byte);
                                }

                            }
                            break;
                        case 1:
                            chunksize = "" + diff_short;
                            if (i == 1) {
                                low_short = (short) (((diff_short / totalnodes) * (i - 1)));
                            } else {
                                low_short = (short) (((diff_short / totalnodes) * (i - 1)) + 1);
                            }
                            up_short = (short) ((diff_short / totalnodes) * (i));
                            if (reverseloop) {
                                lower = "" + (min_short - low_short);
                                if (i == totalnodes) {
                                    upper = "" + ((max_short - (min_short - up_short)) + (min_short - up_short));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_short - up_short);
                                }

                            } else {
                                lower = "" + (min_short + low_short);
                                if (i == totalnodes) {
                                    upper = "" + ((max_short - (min_short + up_short)) + (min_short + up_short));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_short + up_short);
                                }

                            }
                            break;
                        case 2:
                            chunksize = "" + diff_int;
                            if (i == 1) {
                                low_int = (((diff_int / totalnodes) * (i - 1)));
                            } else {
                                low_int = (((diff_int / totalnodes) * (i - 1)) + 1);
                            }
                            up_int = ((diff_int / totalnodes) * (i));
                            if (reverseloop) {
                                lower = "" + (min_int - low_int);
                                if (i == totalnodes) {
                                    upper = "" + ((max_int - (min_int - up_int)) + (min_int - up_int));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_int - up_int);
                                }

                            } else {
                                lower = "" + (min_int + low_int);
                                if (i == totalnodes) {
                                    upper = "" + ((max_int - (min_int + up_int)) + (min_int + up_int));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_int + up_int);
                                }

                            }
                            break;
                        case 3:

                            chunksize = "" + diff_long;
                            if (i == 1) {
                                low_long = (((diff_long / totalnodes) * (i - 1)));
                            } else {
                                low_long = (((diff_long / totalnodes) * (i - 1)) + 1);
                            }
                            up_long = ((diff_long / totalnodes) * (i));
                            if (reverseloop) {
                                lower = "" + (min_long - low_long);
                                if (i == totalnodes) {
                                    upper = "" + ((max_long - (min_long - up_long)) + (min_long - up_long));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_long - up_long);
                                }

                            } else {
                                lower = "" + (min_long + low_long);
                                if (i == totalnodes) {
                                    upper = "" + ((max_long - (min_long + up_long)) + (min_long + up_long));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_long + up_long);
                                }
                            }
                            break;
                        case 4:
                            chunksize = "" + diff_float;
                            if (i == 1) {
                                low_float = (((diff_float / totalnodes) * (i - 1)));
                            } else {
                                low_float = (((diff_float / totalnodes) * (i - 1)) + 1);
                            }
                            up_float = ((diff_float / totalnodes) * (i));
                            if (reverseloop) {
                                lower = "" + (min_float - low_float);
                                if (i == totalnodes) {
                                    upper = "" + ((max_float - (min_float - up_float)) + (min_float - up_float));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_float - up_float);
                                }
                            } else {
                                lower = "" + (min_float + low_float);
                                if (i == totalnodes) {
                                    upper = "" + ((max_float - (min_float + up_float)) + (min_float + up_float));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_float + up_float);
                                }

                            }
                            break;
                        case 5:
                            chunksize = "" + diff_double;
                            if (i == 1) {
                                low_double = (((diff_double / totalnodes) * (i - 1)));
                            } else {
                                low_double = (((diff_double / totalnodes) * (i - 1)) + 1);
                            }
                            up_double = ((diff_double / totalnodes) * (i));
                            if (reverseloop) {
                                lower = "" + (min_double - low_double);

                                if (i == totalnodes) {
                                    upper = "" + ((max_double - (min_double - up_double)) + (min_double - up_double));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_double - up_double);
                                }
                            } else {
                                lower = "" + (min_double + low_double);
                                if (i == totalnodes) {
                                    upper = "" + ((max_double - (min_double + up_double)) + (min_double + up_double));
                                    chunksCreated = true;
                                } else {

                                    upper = "" + (min_double + up_double);
                                }

                            }
                            break;

                    }

                    break;

            }
            ChunkSize.add(chunksize.trim());
            lowList.add(lower.trim());
            upList.add(upper.trim());
            String cs = chunksize, ll = lower, upl = upper;
            int tempi = i;
//            Platform.runLater(new Runnable() {
//
//                @Override
//                public void run() {
//                  //  FXSplitTabs.chunkCalcTA.appendText("\n SrNo:" + tempi + "\t ChunkSize:" + cs + "\t Low:" + ll + "\tUP:" + upl);
//                }
//            });
            // System.out.println("\n SrNo:" + tempi + "\t ChunkSize:" + cs + "\t Low:" + ll + "\tUP:" + upl);
            //settings.outPrintln("reched here2");
            i++;
        }
        // System.out.println("Total Number Of Chunks " + ChunkSize.size());
        return true;
    }

    
    @Override
    public void run() {
        createChunks();
    }

    boolean isInDescendingOrder() {
        int lastvalue = Integer.MAX_VALUE;
        for (int i = 0; i < ChunkSize.size(); i++) {
            String get = ChunkSize.get(i);
            int val = Integer.parseInt(get);
            if (val > lastvalue) {
                return false;
            }
            lastvalue = val;
        }

        return true;
    }

    public static void write(File f, String text) throws IOException {
        try (FileWriter fw = new FileWriter(f);
                PrintWriter pw = new PrintWriter(fw)) {
            pw.print(text);
            pw.close();
            fw.close();
        }

    }

    public static void main(String[] args) throws IOException {
        ArrayList<Double> QSSdeltaVal = new ArrayList<>();
        StringBuilder csvContent = new StringBuilder("Total Nodes, Delta, Total No Of Chunks, Chunks\n");
        double incrementor = 0.0001;
        int totalNodes = 26;
        int lastChunkSize = 0;
        int lasttn = 0;
        int problemSize = 1001;
        double lastDelta = Double.MIN_VALUE;
        String lastChunk = "";
        for (int tn = 2; tn < totalNodes; tn++) {
            System.out.println("For Node: " + tn);
            for (double i = -10; i < 10; i += incrementor) {
                ChunkCalculator cc = new ChunkCalculator(2, false, "0", "" + problemSize, 4, 0.076, 0.1, i, 0.1, tn);
                try {

                    cc.createChunks();
                    if (cc.isInDescendingOrder() && cc.ChunkSize.size() < (tn * 5)) {
                        if (lastChunkSize != cc.ChunkSize.size() && !(lastChunk.trim().equalsIgnoreCase(cc.ChunkSize.toString().trim()))) {
                            QSSdeltaVal.add(i);
                            System.out.println("Delta: " + i);
                            System.out.println("Total Nodes: " + tn);
//System.out.println("" + cc.ChunkSize);
                            System.out.println("ChunkSize: " + cc.ChunkSize.size() + "");
                            System.out.println("Last Chunk equals this chunk : " + lastChunk.trim().equalsIgnoreCase(cc.ChunkSize.toString().trim()));
                            // System.out.println("Chunk: " + cc.ChunkSize.toString() + "");
                            // System.out.println("Last CHunk " + lastChunk);
                            System.out.println("LastChunkSize: " + lastChunkSize + "\n");
                            //csvContent.append(lasttn).append(", ").append(lastDelta).append(", ").append(lastChunkSize).append(", ").append(lastChunk).append("\n");
                            csvContent.append(tn).append(", ").append(i).append(", ").append(cc.ChunkSize.size()).append(", ").append(cc.ChunkSize.toString().substring(1, cc.ChunkSize.toString().length() - 1)).append("\n");
                            lastChunk = cc.ChunkSize.toString();
                            lastChunkSize = cc.ChunkSize.size();
                            lasttn = tn;
                            lastDelta = i;
                            ChunkCalculator.write(new File("calculatedDelta" + problemSize + ".csv"), csvContent.toString());
                        }
                    }
                } catch (Exception e) {
                }
                // System.out.println(""+i);
                // incrementor*=10;
            }
        }
    }

}
