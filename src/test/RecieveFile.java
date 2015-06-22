/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RecieveFile {

    public final static int SOCKET_PORT = 4445;      // you may change this
    String SERVER = "127.0.0.1";  // localhost
    ArrayList<String> logmsg = new ArrayList<>();
    public static void main(String[] args) {
        new RecieveFile();
    }
    
    public RecieveFile() {
        try (Socket sock = new Socket(SERVER, SOCKET_PORT)) {
            
            System.out.println("Connecting...");
            try (OutputStream os = sock.getOutputStream(); DataOutputStream outToServer = new DataOutputStream(os)) {
                try (DataInputStream dIn = new DataInputStream(sock.getInputStream())) {
                    long fileLen, downData;
                    int bufferSize = sock.getReceiveBufferSize();
                    
                    long starttime = System.currentTimeMillis();
                    File myFIle = new File("lib1.zip");
                    try (FileOutputStream fos = new FileOutputStream(myFIle); BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                        fileLen = dIn.readLong();
                        /*for (long j = 0; j <= fileLen; j++) {
                         int tempint = is.read();
                         bos.write(tempint);
                         }*/
                        downData = fileLen;
                        int n = 0;
                        byte[] buf = new byte[8192];
                            while (fileLen > 0 && ((n = dIn.read(buf, 0, buf.length)) != -1)) {
                         bos.write(buf, 0, n);
                         fileLen -= n;
                         //            System.out.println("Remaining "+fileLen);
                         }
                        /*while ((n = dIn.read(buf)) > 0) {
                            bos.write(buf, 0, n);
                        }*/
                        bos.flush();
                        long endtime = System.currentTimeMillis();
                        System.out.println("File " + myFIle.getAbsolutePath()
                                + " downloaded (" + downData + " bytes read) in " + (endtime - starttime) + " ms");

                    }
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(RecieveFile.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
