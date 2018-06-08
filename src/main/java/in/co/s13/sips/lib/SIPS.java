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
package in.co.s13.sips.lib;

import in.co.s13.SIPS.db.SQLiteJDBC;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
    int valcounter = 0, objcounter = 0, fileDownloadServerPort = 13132, taskServerPort = 13133, fileServerPort = 13135;
    String simDBLoc = "", parsedCodeDBLoc = "";
    public static String OS = System.getProperty("os.name").toLowerCase();
    public static int OS_Name = 0;
    String HOST, PID, CNO, ClassName;

    String homeDir = System.getProperty("user.dir");

    public SIPS(String className) {
        this.ClassName = className;
        //int pid = Integer.parseInt(workingDir.substring(workingDir.lastIndexOf("/")));
        if (ClassName.contains(".")) {
            ClassName = ClassName.replaceAll("\\.", "/");
        }
        simDBLoc = homeDir + "/.simulated/" + ClassName + "-sim.db";
        parsedCodeDBLoc = homeDir + "/.parsed/" + ClassName + "-parsed.db";
        System.out.println("SIM DB Location: " + simDBLoc);
        System.out.println("PARSED DB Location: " + parsedCodeDBLoc);
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
            db.update(simDBLoc, sql);
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
                sql = "SELECT * FROM BINARYEXP;";

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
                db3.update(simDBLoc, sql);
            }

        } catch (SQLException ex) {
            Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
        }

        valcounter++;
        db3.closeConnection();
    }

    public void saveObject(Object obj) {
        String sql = "";
        {
            System.out.println("" + obj);
            sql = "UPDATE OBJ" + objcounter + " set VALUE=? WHERE ID='0';";
            db.update(simDBLoc, sql, obj);
        }
        db.closeConnection();
        objcounter++;
    }

    public void saveObject(String objectName, int instance, Object obj) {
        Thread t = new Thread(() -> {
            try {
                // Mobile m1 = new Mobile(obj);
                String path = homeDir + "/.simulated/" + ClassName + "/";
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
                th.start();
                System.out.println("Saved The Object at " + path);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        t.start();

    }

    public void sendResult(String objectName, int instance, Object obj) {
        Thread t = new Thread(() -> {
            try {
                String path = homeDir + "/.result/" + ClassName + "/";
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
                String checksum = tools.getCheckSum(path2);
                String workingDir = System.getProperty("user.dir");
                JSONObject meta = tools.readJSONFile(workingDir + "/task.json");
                PID = meta.getString("JOB_TOKEN");
                HOST = meta.getString("SENDER_IP");
                CNO = meta.getString("CHUNK_NO");
                String senderUUID = meta.getString("SENDER_UUID");
                String nodeUUID = meta.getString("UUID");
                String projectName = meta.getString("PROJECT");

                System.out.println("Host : " + HOST + " Port: " + fileServerPort);
                String cacheParentDir = workingDir.substring(0, workingDir.lastIndexOf("/proc/"));
                final String finalPath = path;
                new Thread(() -> {
                    File ipDir = new File(cacheParentDir + "/cache/" + senderUUID);
                    if (!ipDir.exists()) {
                        ipDir.mkdirs();
                    }
                    File ip2Dir = new File(ipDir.getAbsolutePath() + "/" + PID + "/.result/" + ClassName + "/" + objectName + "-instance-" + instance + ".obj");
                    if (!ip2Dir.getParentFile().exists()) {
                        ip2Dir.getParentFile().mkdirs();
                    }
                    File tmpFile = new File(ip2Dir.getAbsolutePath() + ".tmp");

                    int r = 0;
                    while (tmpFile.exists()) {
                        tmpFile = new File(ip2Dir.getAbsolutePath() + ".tmp." + r);
                        r++;
                    }
                    util.tools.copyFileUsingStream(finalPath, tmpFile.getAbsolutePath());
                    new File(ip2Dir.getAbsolutePath() + ".tmp").renameTo(ip2Dir);
                }).start();
//                if (!new File(ip2Dir.getAbsolutePath() + ".sha").exists()) {
//                    String lchecksum = tools.getCheckSum(ip2Dir.getAbsolutePath());
//                }

                try (Socket socket = new Socket(HOST, fileServerPort);
                        OutputStream outputStream = socket.getOutputStream();
                        DataOutputStream outToServer = new DataOutputStream(outputStream);
                        DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {
                    JSONObject body = new JSONObject();
                    body.put("PID", PID);
                    body.put("CNO", CNO);
                    body.put("CLASSNAME", ClassName);
                    body.put("OBJECT", objectName);
                    body.put("UUID", nodeUUID);
                    body.put("INSTANCE", instance);
                    body.put("PROJECT", projectName);
                    JSONObject msg = new JSONObject();
                    msg.put("Command", "UPLOAD_RESULT");
                    msg.put("Body", body);
                    String sendmsg = msg.toString();

                    byte[] bytes = sendmsg.getBytes("UTF-8");
                    outToServer.writeInt(bytes.length);
                    outToServer.write(bytes);

                    File fileToSend = new File(path2);
                    long flength = fileToSend.length();
                    outToServer.writeLong(flength);

                    try (FileInputStream fis = new FileInputStream(fileToSend); BufferedInputStream bis = new BufferedInputStream(fis)) {
                        int theByte = 0;
                        int count;
                        byte[] mybytearray = new byte[1024];
                        try (BufferedOutputStream bos = new BufferedOutputStream(outputStream)) {
                            while ((count = bis.read(mybytearray)) > -1) {
                                bos.write(mybytearray, 0, count);
                            }
                            bos.flush();
                        }
                    }
                }
                System.out.println("Sent result from " + path);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        t.start();

    }

    private String resolveObjectChecksum(String HOST, int fileServerPort, String objectname, String nodeUUID, String Instancenumber, String projectName, String senderUUID, boolean isResult) {
        String checksum = "", lchecksum;
        String workingDir = System.getProperty("user.dir");
        try (Socket s = new Socket(HOST, fileServerPort); OutputStream os = s.getOutputStream(); DataOutputStream outToServer = new DataOutputStream(os); DataInputStream dIn = new DataInputStream(s.getInputStream())) {
            JSONObject body = new JSONObject();
            body.put("PID", PID);
            body.put("CNO", CNO);
            body.put("CLASSNAME", ClassName);
            body.put("OBJECT", objectname);
            body.put("UUID", nodeUUID);
            body.put("INSTANCE", Instancenumber);
            body.put("PROJECT", projectName);
            JSONObject msg = new JSONObject();
            if (isResult) {
                msg.put("Command", "resolveResultChecksum");
            } else {
                msg.put("Command", "resolveObjectChecksum");
            }
            msg.put("Body", body);
            String sendmsg = msg.toString();

            byte[] bytes = sendmsg.getBytes("UTF-8");
            outToServer.writeInt(bytes.length);
            outToServer.write(bytes);

            int length = dIn.readInt();                    // read length of incoming message
            byte[] message = new byte[length];
            if (length > 0) {
                dIn.readFully(message, 0, message.length); // read the message
            }
            String reply = new String(message);
            System.out.println("Recieved " + reply + " from " + HOST);
            if (reply.equalsIgnoreCase("foundobj")) {
                length = dIn.readInt();                    // read length of incoming message
                message = new byte[length];

                if (length > 0) {
                    dIn.readFully(message, 0, message.length); // read the message
                }
                checksum = new String(message);
                System.out.println("CheckSum Recieved " + checksum);
            }

        } catch (IOException ex) {
            Logger.getLogger(SIPS.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
        return checksum;
    }

    public Object receiveResult(String objectname, int instanceNumber) {
        return receiveResult(objectname, instanceNumber, 500, 600);
    }

    public Object receiveResult(String objectname, int instanceNumber, long sleep, long maxTries) {
        Object value = null;
        String workingDir = System.getProperty("user.dir");
        {
            String lchecksum = "";
            String path = "";
            String checksum = "";
            File ipDir, ip2Dir = null;
            JSONObject meta = tools.readJSONFile(workingDir + "/task.json");
            PID = meta.getString("JOB_TOKEN");
            HOST = meta.getString("SENDER_IP");
            CNO = meta.getString("CHUNK_NO");
            String senderUUID = meta.getString("SENDER_UUID");
            String nodeUUID = meta.getString("UUID");
            String projectName = meta.getString("PROJECT");
            System.out.println("Host : " + HOST + " Port: " + fileServerPort);
            path = homeDir + "/.result/" + ClassName + "/";
            path += "" + objectname + "-instance-" + instanceNumber + ".obj";

            String cacheParentDir = workingDir.substring(0, workingDir.lastIndexOf("/proc/"));
            ipDir = new File(cacheParentDir + "/cache/" + senderUUID);
            if (!ipDir.exists()) {
                ipDir.mkdirs();
            }
            ip2Dir = new File(ipDir.getAbsolutePath() + "/" + PID + "/.result/" + ClassName + "/" + objectname + "-instance-" + instanceNumber + ".obj");
            if (new File(ip2Dir.getAbsolutePath() + ".sha").exists()) {
                lchecksum = tools.LoadCheckSum(ip2Dir.getAbsolutePath() + ".sha");
            }
            boolean Ndownloaded = true;

            if (lchecksum.trim().length() > 0) {
                util.tools.copyFileUsingStream(ip2Dir.getAbsolutePath(), path);
                Thread sendCacheHitThread = new Thread(new sendCacheHit("127.0.0.1", PID, CNO, nodeUUID, senderUUID, ip2Dir.length(), 0, 0, 0));
                sendCacheHitThread.start();
                Ndownloaded = false;
                try (FileInputStream fis = new FileInputStream(path); GZIPInputStream gs = new GZIPInputStream(fis); ObjectInputStream ois = new ObjectInputStream(gs)) {
                    value = ois.readObject();

                } catch (FileNotFoundException ex) {
                    Logger.getLogger(SIPS.class
                            .getName()).log(Level.SEVERE, null, ex);

                } catch (IOException | ClassNotFoundException ex) {
                    Logger.getLogger(SIPS.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
                return value;
            }
            if (Ndownloaded) {
                checksum = resolveObjectChecksum(HOST, fileServerPort, objectname, nodeUUID, "" + instanceNumber, projectName, senderUUID, true);
                int tries = 0;
                while (checksum.trim().length() < 1) {
                    if (tries >= maxTries) {
                        System.out.println("Ran out of tries !!!!\n\t\t Either Result is not on Master Node or hasn't been uploaded yet!"
                                + "\n\t\t If you think your data flow is correct, try increasing maxTries parameter or/and sleep parameter"
                                + "\n\t\t Returning null for now");
                        return value;
                    }
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(SIPS.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    checksum = resolveObjectChecksum(HOST, fileServerPort, objectname, nodeUUID, "" + instanceNumber, projectName, senderUUID, true);
                    tries++;
                }
            }
            long totalSleepTime = 0;
            boolean iRequestedFile = false, alreadyInQue = false;
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
                            body.put("PID", PID);
                            body.put("CNO", CNO);
                            body.put("CLASSNAME", ClassName);
                            body.put("OBJECT", objectname);
                            body.put("INSTANCE", instanceNumber);
                            body.put("IP", HOST);
                            body.put("UUID", senderUUID);
                            body.put("PROJECT", projectName);
                            body.put("CHECKSUM", checksum);

                            JSONObject msg = new JSONObject();
                            msg.put("Command", "downloadResult");
                            msg.put("Body", body);
                            String sendmsg = msg.toString();

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
                                    totalSleepTime += vl;
                                    if (!iRequestedFile) {
                                        alreadyInQue = true;
                                    }
                                } else if (rpl.equalsIgnoreCase("addedinq")) {
                                    sock.close();
                                    iRequestedFile = true;
                                } else {
                                    System.out.println("Couldn't find file");

                                }
                            } catch (InterruptedException ex) {
                                Logger.getLogger(SIPS.class
                                        .getName()).log(Level.SEVERE, null, ex);

                            }
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(SIPS.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

            /*New Logic While wala*/
            long endTime = System.currentTimeMillis();
            if (iRequestedFile && !alreadyInQue) {
                long totalTime = endTime - starttime;
                Thread sendCacheMissThread = new Thread(new sendCacheMiss("127.0.0.1", PID, CNO, nodeUUID, senderUUID, ip2Dir.length(), ip2Dir.length() / (double) ((double) (totalTime) / 1000), (endTime - starttime), totalSleepTime));
                sendCacheMissThread.start();
            }
            if (alreadyInQue) {
                Thread sendCacheHitThread = new Thread(new sendCacheHit("127.0.0.1", PID, CNO, nodeUUID, senderUUID, ip2Dir.length(), 0, (endTime - starttime), totalSleepTime));
                sendCacheHitThread.start();
            }
            Thread t2 = new Thread(new sendCommOverHead("ComOH", HOST, PID, CNO, "", "" + (endTime - starttime), nodeUUID));
            t2.start();
            //   tools.copyFileUsingStream(path, ip2Dir.getAbsolutePath());
            //  tools.saveCheckSum(ip2Dir.getAbsolutePath() + ".sha", checksum);
            try (FileInputStream fis = new FileInputStream(path); GZIPInputStream gs = new GZIPInputStream(fis); ObjectInputStream ois = new ObjectInputStream(gs)) {
                value = ois.readObject();

            } catch (FileNotFoundException ex) {
                Logger.getLogger(SIPS.class
                        .getName()).log(Level.SEVERE, null, ex);

            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(SIPS.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }
        return value;

    }

    public void breakLoop() {

        String workingDir = System.getProperty("user.dir");
        //System.out.println("Current working directory : " + workingDir);
        if (workingDir.contains("-ID-")) {
            if (OS_Name == 2) {
                HOST = workingDir.substring(workingDir.lastIndexOf("/proc/") + 5, workingDir.indexOf("-ID"));
                PID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("-CN-"));
                CNO = workingDir.substring(workingDir.lastIndexOf("-CN-") + 4);

            } else if (OS_Name == 0) {
                HOST = workingDir.substring(workingDir.lastIndexOf("\\proc\\") + 5, workingDir.indexOf("-ID"));
                PID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("-CN-"));
                CNO = workingDir.substring(workingDir.lastIndexOf("-CN-") + 4);

            }

            try (Socket s = new Socket(HOST, taskServerPort); OutputStream os = s.getOutputStream();
                    DataOutputStream outToServer = new DataOutputStream(os);) {
                JSONObject msg = new JSONObject();
                msg.put("Command", "breakLoop");
                JSONObject body = new JSONObject();
                body.put("PID", PID);
                body.put("CNO", CNO);
                msg.put("body", body);
                String sendmsg = msg.toString(2);
                byte[] bytes = sendmsg.getBytes("UTF-8");
                outToServer.writeInt(bytes.length);
                outToServer.write(bytes);
                //ObjectInputStream inStream = new ObjectInputStream(s.getInputStream());
                //value = inStream.readObject();

            } catch (IOException ex) {
                Logger.getLogger(SIPS.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Deprecated
    public void simulateLoop() {

    }

    public void parallelFor() {
    }

    public void endParallelFor() {
    }

    public void simulateSection() {

    }

    public void endSimulateSection() {

    }

    public void defineTask(String taskname) {

    }

    public void setTaskResourcePriority(String taskname, String... resources) {

    }

    public void setTaskDependency(String taskname, String... dependencies) {

    }

    public void setDuration(String taskname, long duration) {

    }

    public void setTimeout(String taskname, long duration) {

    }

    public void endTask(String taskname) {

    }

    public Object resolveObject(String objectname, int instanceNumber) {
        Object value = null;
        String workingDir = System.getProperty("user.dir");
        System.out.println("Current working directory : " + workingDir);
        {
            String lchecksum = "";
            String checksum = null;
            File ipDir, ip2Dir = null;
            JSONObject meta = tools.readJSONFile(workingDir + "/task.json");
            PID = meta.getString("JOB_TOKEN");
            HOST = meta.getString("SENDER_IP");
            CNO = meta.getString("CHUNK_NO");
            String senderUUID = meta.getString("SENDER_UUID");
            String nodeUUID = meta.getString("UUID");
            String projectName = meta.getString("PROJECT");
            System.out.println("Host : " + HOST + " Port: " + fileServerPort);
            String path = homeDir + "/.build/.simulated/" + ClassName + "/";
            path += "" + objectname + "-instance-" + instanceNumber + ".obj";

            boolean Ndownloaded = true;
            long starttime = System.currentTimeMillis();
            boolean iRequestedFile = false, alreadyInQue = false;

            String cacheParentDir = workingDir.substring(0, workingDir.lastIndexOf("/proc/"));
            ipDir = new File(cacheParentDir + "/cache/" + senderUUID);
            if (!ipDir.exists()) {
                ipDir.mkdirs();
            }
            ip2Dir = new File(ipDir.getAbsolutePath() + "/" + PID + "/" + projectName + "/sim/" + ClassName + "/" + objectname + "-instance-" + instanceNumber + ".obj");
            if (new File(ip2Dir.getAbsolutePath() + ".sha").exists()) {
                lchecksum = tools.LoadCheckSum(ip2Dir.getAbsolutePath() + ".sha");
            }

            if (lchecksum.trim().length() > 0) {
                util.tools.copyFileUsingStream(ip2Dir.getAbsolutePath(), path);
                Thread sendCacheHitThread = new Thread(new sendCacheHit("127.0.0.1", PID, CNO, nodeUUID, senderUUID, ip2Dir.length(), 0, 0, 0));
                sendCacheHitThread.start();
                Ndownloaded = false;
            }
            if (Ndownloaded) {
                checksum = resolveObjectChecksum(HOST, fileServerPort, objectname, nodeUUID, "" + instanceNumber, projectName, senderUUID, false);
            }
            long totalSleepTime = 0;
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
                            body.put("PID", PID);
                            body.put("CNO", CNO);
                            body.put("CLASSNAME", ClassName);
                            body.put("OBJECT", objectname);
                            body.put("INSTANCE", instanceNumber);
                            body.put("IP", HOST);
                            body.put("UUID", senderUUID);
                            body.put("PROJECT", projectName);
                            body.put("CHECKSUM", checksum);

                            JSONObject msg = new JSONObject();
                            msg.put("Command", "downloadObject");
                            msg.put("Body", body);
                            String sendmsg = msg.toString();

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
                                    totalSleepTime += vl;
                                    if (!iRequestedFile) {
                                        alreadyInQue = true;
                                    }
                                } else if (rpl.equalsIgnoreCase("addedinq")) {
                                    sock.close();
                                    iRequestedFile = true;
                                } else {
                                    System.out.println("Couldn't find file");

                                }
                            } catch (InterruptedException ex) {
                                Logger.getLogger(SIPS.class
                                        .getName()).log(Level.SEVERE, null, ex);

                            }
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(SIPS.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }

                }
            }
            long endTime = System.currentTimeMillis();
            if (iRequestedFile && !alreadyInQue) {
                long totalTime = endTime - starttime;
                Thread sendCacheMissThread = new Thread(new sendCacheMiss("127.0.0.1", PID, CNO, nodeUUID, senderUUID, ip2Dir.length(), ip2Dir.length() / (double) ((double) (totalTime) / 1000), (endTime - starttime), totalSleepTime));
                sendCacheMissThread.start();
            }

            if (alreadyInQue) {
                Thread sendCacheHitThread = new Thread(new sendCacheHit("127.0.0.1", PID, CNO, nodeUUID, senderUUID, ip2Dir.length(), 0, (endTime - starttime), totalSleepTime));
                sendCacheHitThread.start();

            }
            Thread t2 = new Thread(new sendCommOverHead("ComOH", HOST, PID, CNO, "", "" + (endTime - starttime), nodeUUID));
            t2.start();
            //   tools.copyFileUsingStream(path, ip2Dir.getAbsolutePath());
            //  tools.saveCheckSum(ip2Dir.getAbsolutePath() + ".sha", checksum);
            try (FileInputStream fis = new FileInputStream(path); GZIPInputStream gs = new GZIPInputStream(fis); ObjectInputStream ois = new ObjectInputStream(gs)) {
                value = ois.readObject();

            } catch (FileNotFoundException ex) {
                Logger.getLogger(SIPS.class
                        .getName()).log(Level.SEVERE, null, ex);

            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(SIPS.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }
        return value;

    }

    public void saveArrayElement(Object obj, String objectname, String position, int Instancenumber) {
        String workingDir = System.getProperty("user.dir");
        if (workingDir.contains("-ID-")) {
            if (OS_Name == 2) {
                HOST = workingDir.substring(workingDir.lastIndexOf("/proc/") + 5, workingDir.indexOf("-ID"));
                PID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("-CN-"));
                CNO = workingDir.substring(workingDir.lastIndexOf("-CN-") + 4);
            } else if (OS_Name == 0) {
                HOST = workingDir.substring(workingDir.lastIndexOf("\\proc\\") + 5, workingDir.indexOf("-ID"));
                PID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("-CN-"));
                CNO = workingDir.substring(workingDir.lastIndexOf("-CN-") + 4);
            }
            try (Socket s = new Socket(HOST, fileServerPort); OutputStream os = s.getOutputStream();
                    DataOutputStream outToServer = new DataOutputStream(os);) {
                JSONObject body = new JSONObject();
                body.put("PID", PID);
                body.put("CNO", CNO);
                body.put("OBJECT", objectname);
                body.put("INSTANCE", Instancenumber);
                body.put("POSITION", position);

                JSONObject msg = new JSONObject();
                msg.put("Command", "saveArrayElement");
                msg.put("body", body);
                String sendmsg = msg.toString(2);

                byte[] bytes = sendmsg.getBytes("UTF-8");
                outToServer.writeInt(bytes.length);
                outToServer.write(bytes);
                ObjectOutputStream outStream = new ObjectOutputStream(os);
                outStream.writeObject(obj);
            } catch (IOException ex) {
                Logger.getLogger(SIPS.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void updateArrayElement(Object obj, String objectname, String position, int Instancenumber) {
        Object value = null;
        Socket s = null;
        String workingDir = System.getProperty("user.dir");
        //System.out.println("Current working directory : " + workingDir);
        if (workingDir.contains("-ID-")) {
            if (OS_Name == 2) {
                HOST = workingDir.substring(workingDir.lastIndexOf("/proc/") + 5, workingDir.indexOf("-ID"));
                PID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("-CN-"));
                CNO = workingDir.substring(workingDir.lastIndexOf("-CN-") + 4);
            } else if (OS_Name == 0) {
                HOST = workingDir.substring(workingDir.lastIndexOf("\\proc\\") + 5, workingDir.indexOf("-ID"));
                PID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("-CN-"));
                CNO = workingDir.substring(workingDir.lastIndexOf("-CN-") + 4);

            }
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(HOST, fileServerPort));
                OutputStream os = s.getOutputStream();
                ObjectOutputStream inStream;
                try (DataOutputStream outToServer = new DataOutputStream(os)) {
                    JSONObject body = new JSONObject();
                    body.put("PID", PID);
                    body.put("CNO", CNO);
                    body.put("OBJECT", objectname);
                    body.put("INSTANCE", Instancenumber);
                    body.put("POSITION", position);

                    JSONObject msg = new JSONObject();
                    msg.put("Command", "updateArrayElement");
                    msg.put("body", body);
                    String sendmsg = msg.toString(2);

//                    String sendmsg = "<Command>updateArrayElement</Command>"
//                            + "<Body><PID>" + PID + "</PID>"
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
                    if (s != null && !s.isClosed()) {
                        s.close();
                    }

                } catch (IOException ex1) {
                    Logger.getLogger(SIPS.class
                            .getName()).log(Level.SEVERE, null, ex1);
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
                PID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("-CN-"));
                CNO = workingDir.substring(workingDir.lastIndexOf("-CN-") + 4);
            } else if (OS_Name == 0) {
                HOST = workingDir.substring(workingDir.lastIndexOf("\\proc\\") + 5, workingDir.indexOf("-ID"));
                PID = workingDir.substring(workingDir.lastIndexOf("-ID-") + 4, workingDir.lastIndexOf("-CN-"));
                CNO = workingDir.substring(workingDir.lastIndexOf("-CN-") + 4);

            }
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(HOST, fileServerPort));
                OutputStream os = s.getOutputStream();
                DataOutputStream outToServer = new DataOutputStream(os);
                JSONObject body = new JSONObject();
                body.put("PID", PID);
                body.put("CNO", CNO);
                body.put("OBJECT", objectname);
                body.put("INSTANCE", Instancenumber);
                body.put("POSITION", position);

                JSONObject msg = new JSONObject();
                msg.put("Command", "resolveArrayElement");
                msg.put("body", body);
                String sendmsg = msg.toString(2);

//                String sendmsg = "<Command>resolveArrayElement</Command>"
//                        + "<Body><PID>" + PID + "</PID>"
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
                    Logger.getLogger(SIPS.class
                            .getName()).log(Level.SEVERE, null, ex1);

                }
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(SIPS.class
                        .getName()).log(Level.SEVERE, null, ex);
            }

        }
        return value;

    }

    public static boolean isWindows() {

        return (OS.contains("win"));

    }

    public static boolean isMac() {

        return (OS.contains("mac"));

    }

    public static boolean isUnix() {

        return (OS.contains("nix") || OS.contains("nux") || OS.indexOf("aix") > 0);

    }

    public static boolean isSolaris() {

        return (OS.contains("sunos"));

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
