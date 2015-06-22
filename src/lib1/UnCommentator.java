/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lib1;

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
