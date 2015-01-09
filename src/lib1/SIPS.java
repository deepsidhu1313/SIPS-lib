/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lib1;

import db.SQLiteJDBC;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Navdeep Singh <navdeepsingh.sidhu95 at gmail.com>
 */
public class SIPS {

    public SQLiteJDBC db = new SQLiteJDBC();
    public SQLiteJDBC db2 = new SQLiteJDBC();
    public SQLiteJDBC db3 = new SQLiteJDBC();
    public ArrayList checkpoint = new ArrayList();
    int valcounter = 0, objcounter = 0;
    String dbloc = "", dbloc2 = "";
    public static String OS = System.getProperty("os.name").toLowerCase();
    public static int OS_Name = 0;
    String HOST, ID, CNO;

    public SIPS() {
        String workingDir = System.getProperty("user.dir");
        //int pid = Integer.parseInt(workingDir.substring(workingDir.lastIndexOf("/")));
        dbloc = "sim.db";
        dbloc2 = "parsing.db";

        if (isWindows()) {
            System.out.println("This is Windows");
            OS_Name = 0;
        } else if (isMac()) {
            System.out.println("This is Mac");
            OS_Name = 1;
        } else if (isUnix()) {
            System.out.println("This is Unix or Linux");
            OS_Name = 2;
        } else if (isSolaris()) {
            System.out.println("This is Solaris");
            OS_Name = 3;
        } else {
            System.out.println("Your OS is not support!!");
            OS_Name = 4;
        }
    }

    public void saveDValues(String... str) {
        String sql = "";
        for (int i = 0; i <= str.length - 1; i++) {
            System.out.println("" + str[i]);
            sql = "UPDATE VAL" + valcounter + " set VALUE='" + str[i] + "' WHERE ID='" + i + "';";
            db.Update(dbloc, sql);
        }
        db.closeConnection();

        sql = "SELECT * FROM VAL" + valcounter + " ;";
        ResultSet rs = null;
        String name, string, right;
        ArrayList<String> namelist = new ArrayList<>(), rightlist = new ArrayList<>();
        try {
            rs = db.select(dbloc, sql);
            while (rs.next()) {
                name = "" + rs.getString("NAME");
                sql = "SELECT * FROM BINARYEXP ;";

                ResultSet rs2 = db2.select(dbloc2, sql);
                while (rs2.next()) {
                    string = rs2.getString("String");
                    if (string.equalsIgnoreCase(name)) {
                        right = rs2.getString("Right");
                        rightlist.add(right);
                        namelist.add(name);
                        break;
                    }
                }
            }
            db.closeConnection();
            db2.closeConnection();
            for (int i = 0; i <= namelist.size() - 1; i++) {
                sql = "UPDATE VAL" + valcounter + " set NAME= REPLACE(NAME, '" + namelist.get(i) + "','" + rightlist.get(i) + "');";
                db3.Update(dbloc, sql);
            }

        } catch (SQLException ex) {
            Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
        }

        valcounter++;
        db3.closeConnection();
    }

    
    public void saveDObjects(Object obj) {
        String sql = "";
        //for (int i = 0; i <= obj.length - 1; i++) 
        {
            //checkpoint.add(i, obj);
            System.out.println("" + obj);

            sql = "UPDATE OBJ" + objcounter + " set VALUE=? WHERE ID='0';";
            db.Update(dbloc, sql, obj);

        }

        if (objcounter == 0) {
            sql = "CREATE TABLE CP "
                    + "(ID INT PRIMARY KEY     NOT NULL,"
                    + "VALUE LONGBLOB)";
            db.createtable(dbloc, sql);
        }
        sql = "INSERT INTO CP(ID,VALUE) VALUES('" + objcounter + "',?);";
        db.Update(dbloc, sql, obj);
        db.closeConnection();

        objcounter++;
    }

    
    public void breakLoop(){

            Socket s = null;
        String workingDir = System.getProperty("user.dir");
        //System.out.println("Current working directory : " + workingDir);
        if (workingDir.contains("-ID-")) {
            if (OS_Name == 2) {
                HOST = workingDir.substring(workingDir.lastIndexOf("/var/") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

            } else if (OS_Name == 0) {
                HOST = workingDir.substring(workingDir.lastIndexOf("\\var\\") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

            }

            try {
                s = new Socket();
                s.connect(new InetSocketAddress(HOST, 13131));
                OutputStream os = s.getOutputStream();
                DataOutputStream outToServer = new DataOutputStream(os);
                String sendmsg = "<Command>resolveObject</Command>"
                        + "<Body><PID>" + ID + "</PID>"
                        + "<CNO>" + CNO + "</CNO>"
                        + "</Body>";
                byte[] bytes = sendmsg.getBytes("UTF-8");
                outToServer.writeInt(bytes.length);
                outToServer.write(bytes);
                //ObjectInputStream inStream = new ObjectInputStream(s.getInputStream());
                //value = inStream.readObject();
                outToServer.close();
               // inStream.close();
                s.close();
            } catch (IOException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
                try {
                    s.close();
                } catch (IOException ex1) {
                    Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }

        }
    

}
    
    public Object resolveObject(String objectname, int Instancenumber) {
        Object value = null;
        Socket s = null;
        String workingDir = System.getProperty("user.dir");
        //System.out.println("Current working directory : " + workingDir);
        if (workingDir.contains("-ID-")) {
            if (OS_Name == 2) {
                HOST = workingDir.substring(workingDir.lastIndexOf("/var/") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

            } else if (OS_Name == 0) {
                HOST = workingDir.substring(workingDir.lastIndexOf("\\var\\") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

            }

            try {
                s = new Socket();
                s.connect(new InetSocketAddress(HOST, 13131));
                OutputStream os = s.getOutputStream();
                DataOutputStream outToServer = new DataOutputStream(os);
                String sendmsg = "<Command>resolveObject</Command>"
                        + "<Body><PID>" + ID + "</PID>"
                        + "<CNO>" + CNO + "</CNO>"
                        + "<OBJECT>" + objectname + "</OBJECT>"
                        + "<INSTANCE>" + Instancenumber + "</INSTANCE></Body>";
                byte[] bytes = sendmsg.getBytes("UTF-8");
                outToServer.writeInt(bytes.length);
                outToServer.write(bytes);
                ObjectInputStream inStream = new ObjectInputStream(s.getInputStream());
                value = inStream.readObject();
                outToServer.close();
                inStream.close();
                s.close();
            } catch (IOException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
                try {
                    s.close();
                } catch (IOException ex1) {
                    Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex1);
                }
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
        return value;
    }

    public void saveArrayElement(Object obj, String objectname, String position, int Instancenumber) {
        Object value = null;
        Socket s = null;
        String workingDir = System.getProperty("user.dir");
        // System.out.println("Current working directory : " + workingDir);
        if (workingDir.contains("-ID-")) {
            if (OS_Name == 2) {
                HOST = workingDir.substring(workingDir.lastIndexOf("/var/") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);
            } else if (OS_Name == 0) {
                HOST = workingDir.substring(workingDir.lastIndexOf("\\var\\") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

            }
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(HOST, 13131));
                OutputStream os = s.getOutputStream();
                DataOutputStream outToServer = new DataOutputStream(os);
                String sendmsg = "<Command>saveArrayElement</Command><Body><PID>" + ID + "</PID>"
                        + "<CNO>" + CNO + "</CNO>"
                        + "<OBJECT>" + objectname + "</OBJECT>"
                        + "<POSITION>" + position + "</POSITION>"
                        + "<INSTANCE>" + Instancenumber + "</INSTANCE></Body>";
                byte[] bytes = sendmsg.getBytes("UTF-8");
                outToServer.writeInt(bytes.length);
                outToServer.write(bytes);
                ObjectOutputStream outStream = new ObjectOutputStream(os);
                outStream.writeObject(obj);
                outToServer.close();
                s.close();

            } catch (IOException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
                try {
                    s.close();
                } catch (IOException ex1) {
                    Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }

        }
    }

    public void simulateDLoop() {

    }

    public void updateArrayElement(Object obj, String objectname, String position, int Instancenumber) {
        Object value = null;
        Socket s = null;
        String workingDir = System.getProperty("user.dir");
        //System.out.println("Current working directory : " + workingDir);
        if (workingDir.contains("-ID-")) {
            if (OS_Name == 2) {
                HOST = workingDir.substring(workingDir.lastIndexOf("/var/") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);
            } else if (OS_Name == 0) {
                HOST = workingDir.substring(workingDir.lastIndexOf("\\var\\") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

            }
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(HOST, 13131));
                OutputStream os = s.getOutputStream();
                DataOutputStream outToServer = new DataOutputStream(os);
                String sendmsg = "<Command>updateArrayElement</Command>"
                        + "<Body><PID>" + ID + "</PID>"
                        + "<CNO>" + CNO + "</CNO>"
                        + "<OBJECT>" + objectname + "</OBJECT>"
                        + "<POSITION>" + position + "</POSITION>"
                        + "<INSTANCE>" + Instancenumber + "</INSTANCE></Body>";
                byte[] bytes = sendmsg.getBytes("UTF-8");
                outToServer.writeInt(bytes.length);
                outToServer.write(bytes);
                ObjectOutputStream inStream = new ObjectOutputStream(s.getOutputStream());
                inStream.writeObject(obj);
                outToServer.close();
                inStream.close();
                s.close();
            } catch (IOException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
                try {
                    s.close();
                } catch (IOException ex1) {
                    Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }

        }
    }

    public Object resolveArrayElement(String objectname, String position, int Instancenumber) {
        Object value = null;
        Socket s = null;
        String workingDir = System.getProperty("user.dir");
        // System.out.println("Current working directory : " + workingDir);
        if (workingDir.contains("-ID-")) {
            if (OS_Name == 2) {
                HOST = workingDir.substring(workingDir.lastIndexOf("/var/") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);
            } else if (OS_Name == 0) {
                HOST = workingDir.substring(workingDir.lastIndexOf("\\var\\") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

            }
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(HOST, 13131));
                OutputStream os = s.getOutputStream();
                DataOutputStream outToServer = new DataOutputStream(os);
                String sendmsg = "<Command>resolveArrayElement</Command>"
                        + "<Body><PID>" + ID + "</PID>"
                        + "<CNO>" + CNO + "</CNO>"
                        + "<OBJECT>" + objectname + "</OBJECT>"
                        + "<POSITION>" + position + "</POSITION>"
                        + "<INSTANCE>" + Instancenumber + "</INSTANCE></Body>";
                byte[] bytes = sendmsg.getBytes("UTF-8");
                outToServer.writeInt(bytes.length);
                outToServer.write(bytes);
                ObjectInputStream inStream = new ObjectInputStream(s.getInputStream());
                value = inStream.readObject();
                s.close();
                outToServer.close();
                inStream.close();
            } catch (IOException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
                try {
                    s.close();
                } catch (IOException ex1) {
                    Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex1);
                }
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
        return value;

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
