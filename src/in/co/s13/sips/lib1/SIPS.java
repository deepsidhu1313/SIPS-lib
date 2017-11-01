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

import in.co.s13.SIPS.db.SQLiteJDBC;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.json.JSONObject;
import util.tools;

public class SIPS implements Serializable {

    public SQLiteJDBC db = new SQLiteJDBC();
    public SQLiteJDBC db2 = new SQLiteJDBC();
    public SQLiteJDBC db3 = new SQLiteJDBC();
    public ArrayList checkpoint = new ArrayList();
    int valcounter = 0, objcounter = 0, fileDownloadServerPort = 13132, fileServerPort = 13135, taskServerPort = 13133;
    String simDBLoc = "", parsedCodeDBLoc = "";
    public static String OS = System.getProperty("os.name").toLowerCase();
    public static int OS_Name = 0;
    String HOST, ID, CNO, ClassName;

    public SIPS(String projectName, String className) {
        this.ClassName = className;
        String homeDir = System.getProperty("user.home") + "/.SIPS";
        //int pid = Integer.parseInt(workingDir.substring(workingDir.lastIndexOf("/")));
        if (ClassName.contains(".")) {
            ClassName = ClassName.replaceAll("\\.", "/");
        }
        simDBLoc = homeDir + "/.simulated/" + projectName + "/" + ClassName + "sim.db";
        parsedCodeDBLoc = homeDir + "/.parser/" + projectName + "/" + ClassName + "-parsing.db";

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

    public void saveValues(String... str) {
        String sql = "";
        for (int i = 0; i <= str.length - 1; i++) {
            System.out.println("" + str[i]);
            sql = "UPDATE VAL" + valcounter + " set VALUE='" + str[i] + "' WHERE ID='" + i + "';";
            db.Update(simDBLoc, sql);
        }
        db.closeConnection();

        sql = "SELECT * FROM VAL" + valcounter + " ;";
        ResultSet rs = null;
        String name, string, right;
        ArrayList<String> namelist = new ArrayList<>(), rightlist = new ArrayList<>();
        try {
            rs = db.select(simDBLoc, sql);
            while (rs.next()) {
                name = "" + rs.getString("NAME");
                sql = "SELECT * FROM BINARYEXP ;";

                ResultSet rs2 = db2.select(parsedCodeDBLoc, sql);
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
                db3.Update(simDBLoc, sql);
            }

        } catch (SQLException ex) {
            Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
        }

        valcounter++;
        db3.closeConnection();
    }

    public void saveObject(Object obj) {
        String sql = "";
        //for (int i = 0; i <= obj.length - 1; i++) 
        {
            //checkpoint.add(i, obj);
            System.out.println("" + obj);

            sql = "UPDATE OBJ" + objcounter + " set VALUE=? WHERE ID='0';";
            db.Update(simDBLoc, sql, obj);

        }

        /*  if (objcounter == 0) {
         sql = "CREATE TABLE CP "
         + "(ID INT PRIMARY KEY     NOT NULL,"
         + "VALUE LONGBLOB)";
         //    db.createtable(simDBLoc, sql);
         }
         sql = "INSERT INTO CP(ID,VALUE) VALUES('" + objcounter + "',?);";
         //   db.Update(simDBLoc, sql, obj);
         */ db.closeConnection();

        objcounter++;
    }

    public void saveObject(String objectName, int instance, Object obj) {
        Thread t = new Thread(() -> {
            try {
                // Mobile m1 = new Mobile(obj);
                String path = "sim/" + ClassName + "/";
                File df = new File(path);
                if (!df.exists()) {
                    df.mkdirs();
                }
                path += "" + objectName + "-instance-" + instance + ".obj";
                try (FileOutputStream fos = new FileOutputStream(path); GZIPOutputStream gos = new GZIPOutputStream(fos); ObjectOutputStream oos = new ObjectOutputStream(gos)) {
                    oos.writeObject(obj);
                    oos.flush();
                }
                String path2 = path;
                Thread th = new Thread(() -> {
                    tools.getCheckSum(path2);
                });
                System.out.println("Saved The Object at" + path);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        t.start();

    }

    public void breakLoop() {

        Socket s = null;
        String workingDir = System.getProperty("user.dir");
        //System.out.println("Current working directory : " + workingDir);
        if (workingDir.contains("-ID-")) {
            if (OS_Name == 2) {
                HOST = workingDir.substring(workingDir.lastIndexOf("/proc/") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

            } else if (OS_Name == 0) {
                HOST = workingDir.substring(workingDir.lastIndexOf("\\proc\\") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

            }

            try {
                s = new Socket();
                s.connect(new InetSocketAddress(HOST, taskServerPort));
                OutputStream os = s.getOutputStream();
                DataOutputStream outToServer = new DataOutputStream(os);
                JSONObject msg = new JSONObject();
                msg.put("Command", "breakLoop");
                JSONObject body = new JSONObject();
                body.put("PID", ID);
                body.put("CNO", CNO);
                msg.put("body", body);
                String sendmsg = msg.toString(2);
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

    public void parallelFor() {
    }

    public void endParallelFor() {
    }

    public void simulateSection() {

    }

    public void endSimulateSection() {

    }

    public Object resolveObject(String objectname, int Instancenumber) {
        Object value = null;
        //   Socket s = null;
        String workingDir = System.getProperty("user.dir");
        //System.out.println("Current working directory : " + workingDir);
        if (workingDir.contains("-ID-")) {
            if (OS_Name == 2) {
                HOST = workingDir.substring(workingDir.lastIndexOf("/proc/") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

            } else if (OS_Name == 0) {
                HOST = workingDir.substring(workingDir.lastIndexOf("\\proc\\") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

            }
            String lchecksum = "";
            String path = null;
            String checksum = null;
            File ipDir, ip2Dir = null;
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(HOST, fileServerPort));
                OutputStream os = s.getOutputStream();
                try (DataOutputStream outToServer = new DataOutputStream(os)) {
                    JSONObject body = new JSONObject();
                    body.put("PID", ID);
                    body.put("CNO", CNO);
                    body.put("CLASSNAME", ClassName);
                    body.put("OBJECT", objectname);
                    body.put("INSTANCE", Instancenumber);

                    JSONObject msg = new JSONObject();
                    msg.put("Command", "resolveObjectChecksum");
                    msg.put("body", body);
                    String sendmsg = msg.toString(2);

//                    String sendmsg = "<Command>resolveObjectChecksum</Command>"
//                            + "<Body><PID>" + ID + "</PID>"
//                            + "<CNO>" + CNO + "</CNO>"
//                            + "<CLASSNAME>" + ClassName + "</CLASSNAME>"
//                            + "<OBJECT>" + objectname + "</OBJECT>"
//                            + "<INSTANCE>" + Instancenumber + "</INSTANCE></Body>";
                    byte[] bytes = sendmsg.getBytes("UTF-8");
                    outToServer.writeInt(bytes.length);
                    outToServer.write(bytes);

                    path = "sim/" + ClassName + "/";
                    path += "" + objectname + "-instance-" + Instancenumber + ".obj";

                    try (DataInputStream dIn = new DataInputStream(s.getInputStream())) {
                        int length = dIn.readInt();                    // read length of incoming message
                        byte[] message = new byte[length];

                        if (length > 0) {
                            dIn.readFully(message, 0, message.length); // read the message
                        }
                        String reply = new String(message);
                        System.out.println("Recieved " + reply + " from " + HOST);
                        String cachedir = workingDir.substring(0, workingDir.lastIndexOf("/proc/"));
                        ipDir = new File(cachedir + "/cache/" + HOST);
                        if (!ipDir.exists()) {
                            ipDir.mkdirs();
                        }
//String filename = new File(_item).getName();
                        ip2Dir = new File(ipDir.getAbsolutePath() + "/" + path);
                        if (new File(ip2Dir.getAbsolutePath() + ".sha").exists()) {
                            lchecksum = tools.LoadCheckSum(ip2Dir.getAbsolutePath() + ".sha");
                        }

                        if (reply.equalsIgnoreCase("foundobj")) {
                            // receive file
                            length = dIn.readInt();                    // read length of incoming message
                            message = new byte[length];

                            if (length > 0) {
                                dIn.readFully(message, 0, message.length); // read the message
                            }
                            checksum = new String(message);
                            System.out.println("CheckSum Recieved " + checksum);
                        }
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
            }

            /*New Logic While wala*/
            boolean Ndownloaded = true;
            long starttime = System.currentTimeMillis();

            while (Ndownloaded) {
                String nmsg = "";
                if (new File(ip2Dir.getAbsolutePath() + ".sha").exists()) {
                    lchecksum = util.tools.LoadCheckSum(ip2Dir.getAbsolutePath() + ".sha");
                }
                if (lchecksum.trim().equalsIgnoreCase(checksum.trim())) {
                    util.tools.copyFileUsingStream(ip2Dir.getAbsolutePath(), path);
                    Ndownloaded = false;
                } else {

                    try (Socket sock = new Socket("127.0.0.1", fileDownloadServerPort)) {
                        //System.out.println("Connecting...");
                        try (OutputStream os = sock.getOutputStream(); DataOutputStream outToServer = new DataOutputStream(os)) {
                            JSONObject body = new JSONObject();
                            body.put("PID", ID);
                            body.put("CNO", CNO);
                            body.put("CLASSNAME", ClassName);
                            body.put("OBJECT", objectname);
                            body.put("INSTANCE", Instancenumber);
                            body.put("IP", HOST);
                            body.put("CHECKSUM", checksum);

                            JSONObject msg = new JSONObject();
                            msg.put("Command", "downloadObject");
                            msg.put("body", body);
                            String sendmsg = msg.toString(2);

//                    String sendmsg = "<Command>downloadObject</Command>"
//                                    + "<Body><PID>" + ID + "</PID>"
//                                    + "<CNO>" + CNO + "</CNO>"
//                                    + "<CLASSNAME>" + ClassName + "</CLASSNAME>"
//                                    + "<OBJECT>" + objectname + "</OBJECT>"
//                                    + "<INSTANCE>" + Instancenumber + "</INSTANCE>"
//                                    + "<IP>" + HOST + "</IP><CHECKSUM>" + checksum + "</CHECKSUM></Body>";
                            byte[] bytes = sendmsg.getBytes("UTF-8");
                            outToServer.writeInt(bytes.length);
                            outToServer.write(bytes);
                            try (DataInputStream dIn = new DataInputStream(sock.getInputStream())) {
                                int length = dIn.readInt();                    // read length of incoming message
                                byte[] message = new byte[length];

                                if (length > 0) {
                                    dIn.readFully(message, 0, message.length); // read the message
                                }
                                JSONObject reply = new JSONObject(new String(message));
                                String rpl = reply.getString("MSG");//substring(reply.indexOf("<MSG>") + 5, reply.indexOf("</MSG>"));
                                if (rpl.equalsIgnoreCase("finished")) {
                                    // receive file

                                    sock.close();
                                    if (new File(ip2Dir.getAbsolutePath() + ".sha").exists()) {
                                        lchecksum = util.tools.LoadCheckSum(ip2Dir.getAbsolutePath() + ".sha");
                                    }
                                    if (lchecksum.trim().equalsIgnoreCase(checksum.trim())) {
                                        util.tools.copyFileUsingStream(ip2Dir.getAbsolutePath(), path);
                                        Ndownloaded = false;
                                    }

                                } else if (rpl.equalsIgnoreCase("inque")) {
                                    Long vl = reply.getLong("RT");//substring(reply.indexOf("<RT>") + 4, reply.indexOf("</RT>"));
                                    sock.close();
                                    Thread.currentThread().sleep((vl) + 10);

                                } else if (rpl.equalsIgnoreCase("addedinq")) {
                                    sock.close();
                                } else {
                                    System.out.println("Couldn't find file");
                                }
                            } catch (InterruptedException ex) {
                                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
            }

            /*New Logic While wala*/
            long endTime = System.currentTimeMillis();

            Thread t2 = new Thread(new sendCommOverHead("ComOH", HOST, ID, CNO, "", "" + (endTime - starttime)));
            t2.start();
            //   tools.copyFileUsingStream(path, ip2Dir.getAbsolutePath());
            //  tools.saveCheckSum(ip2Dir.getAbsolutePath() + ".sha", checksum);
            try (FileInputStream fis = new FileInputStream(path); GZIPInputStream gs = new GZIPInputStream(fis); ObjectInputStream ois = new ObjectInputStream(gs)) {
                value = ois.readObject();
            } catch (FileNotFoundException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return value;

    }

    /*public Object resolveObject(String objectname, int Instancenumber) {
     Object value = null;
     Socket s = null;
     String workingDir = System.getProperty("user.dir");
     //System.out.println("Current working directory : " + workingDir);
     if (workingDir.contains("-ID-")) {
     if (OS_Name == 2) {
     HOST = workingDir.substring(workingDir.lastIndexOf("/proc/") + 5, workingDir.indexOf("-ID"));
     ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
     CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

     } else if (OS_Name == 0) {
     HOST = workingDir.substring(workingDir.lastIndexOf("\\proc\\") + 5, workingDir.indexOf("-ID"));
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
     }*/
    public void saveArrayElement(Object obj, String objectname, String position, int Instancenumber) {
        Object value = null;
        // = null;
        String workingDir = System.getProperty("user.dir");
        // System.out.println("Current working directory : " + workingDir);
        if (workingDir.contains("-ID-")) {
            if (OS_Name == 2) {
                HOST = workingDir.substring(workingDir.lastIndexOf("/proc/") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);
            } else if (OS_Name == 0) {
                HOST = workingDir.substring(workingDir.lastIndexOf("\\proc\\") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

            }
            try {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(HOST, fileServerPort));
                    OutputStream os = s.getOutputStream();
                    try (DataOutputStream outToServer = new DataOutputStream(os)) {
                        JSONObject body = new JSONObject();
                        body.put("PID", ID);
                        body.put("CNO", CNO);
                        body.put("OBJECT", objectname);
                        body.put("INSTANCE", Instancenumber);
                        body.put("POSITION", position);

                        JSONObject msg = new JSONObject();
                        msg.put("Command", "saveArrayElement");
                        msg.put("body", body);
                        String sendmsg = msg.toString(2);

//                        String sendmsg = "<Command>saveArrayElement</Command><Body><PID>" + ID + "</PID>"
//                                + "<CNO>" + CNO + "</CNO>"
//                                + "<OBJECT>" + objectname + "</OBJECT>"
//                                + "<POSITION>" + position + "</POSITION>"
//                                + "<INSTANCE>" + Instancenumber + "</INSTANCE></Body>";
                        byte[] bytes = sendmsg.getBytes("UTF-8");
                        outToServer.writeInt(bytes.length);
                        outToServer.write(bytes);
                        ObjectOutputStream outStream = new ObjectOutputStream(os);
                        outStream.writeObject(obj);

                    }
                }

            } catch (IOException ex) {
                Logger.getLogger(SIPS.class
                        .getName()).log(Level.SEVERE, null, ex);

            }

        }
    }

    public void simulateLoop() {

    }

    public void initTask(String name) {

    }

    public void endTask(String name) {

    }

    public void updateArrayElement(Object obj, String objectname, String position, int Instancenumber) {
        Object value = null;
        Socket s = null;
        String workingDir = System.getProperty("user.dir");
        //System.out.println("Current working directory : " + workingDir);
        if (workingDir.contains("-ID-")) {
            if (OS_Name == 2) {
                HOST = workingDir.substring(workingDir.lastIndexOf("/proc/") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);
            } else if (OS_Name == 0) {
                HOST = workingDir.substring(workingDir.lastIndexOf("\\proc\\") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

            }
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(HOST, 13131));
                OutputStream os = s.getOutputStream();
                ObjectOutputStream inStream;
                try (DataOutputStream outToServer = new DataOutputStream(os)) {
                    JSONObject body = new JSONObject();
                    body.put("PID", ID);
                    body.put("CNO", CNO);
                    body.put("OBJECT", objectname);
                    body.put("INSTANCE", Instancenumber);
                    body.put("POSITION", position);

                    JSONObject msg = new JSONObject();
                    msg.put("Command", "updateArrayElement");
                    msg.put("body", body);
                    String sendmsg = msg.toString(2);

//                    String sendmsg = "<Command>updateArrayElement</Command>"
//                            + "<Body><PID>" + ID + "</PID>"
//                            + "<CNO>" + CNO + "</CNO>"
//                            + "<OBJECT>" + objectname + "</OBJECT>"
//                            + "<POSITION>" + position + "</POSITION>"
//                            + "<INSTANCE>" + Instancenumber + "</INSTANCE></Body>";
                    byte[] bytes = sendmsg.getBytes("UTF-8");
                    outToServer.writeInt(bytes.length);
                    outToServer.write(bytes);
                    inStream = new ObjectOutputStream(s.getOutputStream());
                    inStream.writeObject(obj);
                }
                inStream.close();
                s.close();

            } catch (IOException ex) {
                Logger.getLogger(SIPS.class
                        .getName()).log(Level.SEVERE, null, ex);

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
                HOST = workingDir.substring(workingDir.lastIndexOf("/proc/") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);
            } else if (OS_Name == 0) {
                HOST = workingDir.substring(workingDir.lastIndexOf("\\proc\\") + 5, workingDir.indexOf("-ID"));
                ID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("c"));
                CNO = workingDir.substring(workingDir.lastIndexOf("c") + 1);

            }
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(HOST, fileServerPort));
                OutputStream os = s.getOutputStream();
                DataOutputStream outToServer = new DataOutputStream(os);
                JSONObject body = new JSONObject();
                body.put("PID", ID);
                body.put("CNO", CNO);
                body.put("OBJECT", objectname);
                body.put("INSTANCE", Instancenumber);
                body.put("POSITION", position);

                JSONObject msg = new JSONObject();
                msg.put("Command", "resolveArrayElement");
                msg.put("body", body);
                String sendmsg = msg.toString(2);

//                String sendmsg = "<Command>resolveArrayElement</Command>"
//                        + "<Body><PID>" + ID + "</PID>"
//                        + "<CNO>" + CNO + "</CNO>"
//                        + "<OBJECT>" + objectname + "</OBJECT>"
//                        + "<POSITION>" + position + "</POSITION>"
//                        + "<INSTANCE>" + Instancenumber + "</INSTANCE></Body>";
                byte[] bytes = sendmsg.getBytes("UTF-8");
                outToServer.writeInt(bytes.length);
                outToServer.write(bytes);
                ObjectInputStream inStream = new ObjectInputStream(s.getInputStream());
                value = inStream.readObject();
                s.close();
                outToServer.close();
                inStream.close();

            } catch (IOException ex) {
                Logger.getLogger(SIPS.class
                        .getName()).log(Level.SEVERE, null, ex);

                try {
                    s.close();
                } catch (IOException ex1) {
                    Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex1);
                }
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(SIPS.class
                        .getName()).log(Level.SEVERE, null, ex);
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

    public int getFileDownloadServerPort() {
        return fileDownloadServerPort;
    }

    public void setFileDownloadServerPort(int fileDownloadServerPort) {
        this.fileDownloadServerPort = fileDownloadServerPort;
    }

    public int getFileServerPort() {
        return fileServerPort;
    }

    public void setFileServerPort(int fileServerPort) {
        this.fileServerPort = fileServerPort;
    }

}
