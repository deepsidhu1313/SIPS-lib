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
package in.co.s13.sips.lib1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Navdeep Singh <navdeepsingh.sidhu95 at gmail.com>
 */
public class UnCommentator {
    
    public UnCommentator(String file, int startline, int startcolumn, int endline, int endcolumn) {
        System.out.println("Uncommenting");
        System.out.println("File :" + file);
        System.out.println("SL :" + startline);
        System.out.println("SC :" + startcolumn);
        System.out.println("EL :" + endline);
        System.out.println("EC :" + endcolumn);
        //FileReader fReader;
        try {
          FileInputStream fReader=new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fReader));
             String cursor; // 
            String content = "";
            int lines = 0;
            //int words = 0;
            int columns = 0;
            while ((cursor = reader.readLine()) != null) {
                // count lines
                lines += 1;
                if (lines == startline) {
                    cursor = "" + cursor.replace("/*", "  ");
                }
                if (lines == endline) {
                    cursor =  cursor.replace("*/", "  ");
                    
                }
                
                if ((cursor.contains("saveDValues"))||(cursor.contains("saveDObject"))) {
                    cursor = "//" + cursor.substring(0);
                    
                }

                // count words
                content += cursor+"\n";
            }
            System.out.println(content);
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
