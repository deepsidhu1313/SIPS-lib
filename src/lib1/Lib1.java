/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lib1;

import db.SQLiteJDBC;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Navdeep Singh <navdeepsingh.sidhu95 at gmail.com>
 */
public class Lib1 {

    public static String OS = System.getProperty("os.name").toLowerCase();
    public static int OS_Name = 0;
    String dbloc = "";
    SQLiteJDBC db = new SQLiteJDBC();
    ArrayList<Integer> combeginline = new ArrayList<>();
    ArrayList<Integer> comendline = new ArrayList<>();
    ArrayList<Integer> combegincol = new ArrayList<>();
    ArrayList<Integer> comendcol = new ArrayList<>();
    ArrayList<Integer> comlevel = new ArrayList<>();
    ArrayList<Integer> uncombeginline = new ArrayList<>();
    ArrayList<Integer> uncomendline = new ArrayList<>();
    ArrayList<Integer> uncombegincol = new ArrayList<>();
    ArrayList<Integer> uncomendcol = new ArrayList<>();
    ArrayList<Integer> uncomlevel = new ArrayList<>();
    int comlastbl = 0, comlastel = 0, comlastlvl = 0;

    public Lib1(int mode, String file) {

        System.out.println(OS);
        System.out.println(file);
        if (isWindows()) {
            System.out.println("This is Windows");
            OS_Name = 0;
            
            dbloc = file.substring(0, file.lastIndexOf("."));
            dbloc += "-parsing.db";

        } else if (isMac()) {
            System.out.println("This is Mac");
            OS_Name = 1;
        } else if (isUnix()) {
            System.out.println("This is Unix or Linux");

            OS_Name = 2;
            if (file.contains("/")) {
                dbloc = file.substring(0, file.lastIndexOf("."));
            }
            dbloc += "-parsing.db";

        } else if (isSolaris()) {
            System.out.println("This is Solaris");
            OS_Name = 3;
        } else {
            System.out.println("Your OS is not support!!");
            OS_Name = 4;

        }

        String sql = "SELECT * FROM SYNTAX";
        try {
            ResultSet rs = db.select(dbloc, sql);
            //System.out.println("Commenting Done");

            while (rs.next()) {
                System.out.println("" + rs.getString("Category"));
                if (rs.getString("Category").contains("ForLoop") || rs.getString("Category").contains("WhileLoop")) {
                    if (mode == 0) {
                        if (rs.getString("SIM").contains("TRUE")) {

                        } else {
                            combeginline.add(rs.getInt("BeginLine"));
                            combegincol.add(rs.getInt("BeginColumn"));
                            comendline.add(rs.getInt("EndLine"));
                            comendcol.add(rs.getInt("EndColumn"));
                            comlevel.add(rs.getInt("Level"));
                        }
                    } else if (mode == 1) {
                        if (rs.getString("SIM").contains("TRUE")) {
                            combeginline.add(rs.getInt("BeginLine"));
                            combegincol.add(rs.getInt("BeginColumn"));
                            comendline.add(rs.getInt("EndLine"));
                            comendcol.add(rs.getInt("EndColumn"));
                            comlevel.add(rs.getInt("Level"));

                            //   Commentator cm = new Commentator(file, rs.getInt("BeginLine"), rs.getInt("BeginColumn"), rs.getInt("EndLine"), rs.getInt("EndColumn"));
                            //  System.out.println("Commenting Done");
                        } else {

                            uncombeginline.add(rs.getInt("BeginLine"));
                            uncombegincol.add(rs.getInt("BeginColumn"));
                            uncomendline.add(rs.getInt("EndLine"));
                            uncomendcol.add(rs.getInt("EndColumn"));
                            uncomlevel.add(rs.getInt("Level"));

                            //  UnCommentator cm = new UnCommentator(file, rs.getInt("BeginLine"), rs.getInt("BeginColumn"), rs.getInt("EndLine"), rs.getInt("EndColumn"));
                            //  System.out.println("UnCommenting Done");
                        }
                    }

                }

            }
            rs.close();
            db.closeConnection();

            System.out.println("comment Bl " + combeginline);
            System.out.println("comment El " + comendline);
            System.out.println("comment LVl " + comlevel);
            System.out.println("uncomment Bl " + uncombeginline);
            System.out.println("uncomment El " + uncomendline);
            System.out.println("uncomment LVl " + uncomlevel);

            if (mode == 0) {
                for (int i = 0; i < combeginline.size(); i++) {
                    if ((combeginline.get(i) >= comlastbl) && (comendline.get(i) <= comlastel) && (comlevel.get(i) > comlastlvl)) {

                    } else {
                        Commentator cm = new Commentator(file, combeginline.get(i), combegincol.get(i), comendline.get(i), comendcol.get(i));
                        System.out.println("Commenting Done");
                        comlastbl = combeginline.get(i);
                        comlastel = comendline.get(i);
                        comlastlvl = comlevel.get(i);
                    }

                }

            } else if (mode == 1) {

                for (int i = 0; i < combeginline.size(); i++) {
                    if ((combeginline.get(i) >= comlastbl) && (comendline.get(i) <= comlastel) && (comlevel.get(i) > comlastlvl)) {

                    } else {
                        Commentator cm = new Commentator(file, combeginline.get(i), combegincol.get(i), comendline.get(i), comendcol.get(i));
                        System.out.println("Commenting Done");
                        comlastbl = combeginline.get(i);
                        comlastel = comendline.get(i);
                        comlastlvl = comlevel.get(i);

                    }

                }
                for (int i = 0; i < uncombeginline.size(); i++) {
                    UnCommentator cm = new UnCommentator(file, uncombeginline.get(i), uncombegincol.get(i), uncomendline.get(i), uncomendcol.get(i));
                    System.out.println("UnCommenting Done");

                }
            }
        } catch (SQLException ex) {
            System.err.println(ex);
        }

    }

    public static void main(String[] args) {
        int i = Integer.parseInt(args[0]);
        Lib1 l = new Lib1(i, args[1]);
    }

    public static boolean isWindows() {

        return (OS.indexOf("win") >= 0);

    }

    public static boolean isMac() {

        return (OS.indexOf("mac") >= 0);

    }

    public static boolean isUnix() {

        return (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0);

    }

    public static boolean isSolaris() {

        return (OS.indexOf("sunos") >= 0);

    }

}
