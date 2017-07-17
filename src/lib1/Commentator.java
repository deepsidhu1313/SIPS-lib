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
package lib1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Navdeep Singh <navdeepsingh.sidhu95 at gmail.com>
 */
public class Commentator {

    public Commentator(String file, int startline, int startcolumn, int endline, int endcolumn) {
        System.out.println("File :" + file);
        System.out.println("SL :" + startline);
        System.out.println("SC :" + startcolumn);
        System.out.println("EL :" + endline);
        System.out.println("EC :" + endcolumn);
        // FileReader fReader;
        try {
            // fReader = new FileReader(file);
            FileInputStream fReader = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fReader));
            String cursor; // 
            String content = "";
            int lines = 0;
            boolean body = false;
            //int words = 0;
            int columns = 0;
            while ((cursor = reader.readLine()) != null) {
                // count lines
                lines += 1;
                if (lines == startline) {
                    System.out.println("Before Comm: "+cursor);
                    cursor = new StringBuilder("/*").append(cursor.substring(0)).toString() ;
                    System.out.println("After Comm: "+cursor);
                    body = true;
                }
                else if (lines == endline) {
                    cursor += "*/";
                    body = false;
                }
                else if (body) {
                    if (cursor.contains("/*")) {
                        cursor = cursor.replace("/*", "  ");
                    }
                    if (cursor.contains("*/")) {
                        cursor = cursor.replace("*/", "  ");

                    }
                }
                System.out.println(cursor);
                // count words
                content += cursor + "\n";
            }

            File f = new File(file);
            if (f.exists()) {
                f.delete();
            }
            PrintStream out = new PrintStream(f);
            out.append(content);
            out.close();

        } catch (IOException ex) {
            Logger.getLogger(UnCommentator.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
