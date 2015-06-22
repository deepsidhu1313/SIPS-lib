/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lib1;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Nika
 */
public class sendCommOverHead implements Runnable {

    String ipadd = "", ID = "", outPut = "", filename = "", value = "", cmd, chunkno;

    Socket s;

    public sendCommOverHead(String overheadName, String ip, String PID, String chunknumber, String Filename, String value) {
        ipadd = ip;
        ID = PID;
        filename = Filename;
        this.value = value;
        cmd = overheadName;
        chunkno = chunknumber;
    }

    @Override
    public void run() {
        try {
            s = new Socket();
            s.connect(new InetSocketAddress(ipadd, 13131));
            try (OutputStream os = s.getOutputStream(); DataOutputStream outToServer = new DataOutputStream(os)) {
                String sendmsg = "<Command>" + cmd + "</Command>"
                        + "<Body><PID>" + ID + "</PID>"
                        + "<CNO>" + chunkno + "</CNO>"
                        + "<FILENAME>" + filename + "</FILENAME>"
                        + "<OUTPUT>" + value + "</OUTPUT>"
                        + "</Body>";
                byte[] bytes = sendmsg.getBytes("UTF-8");
                outToServer.writeInt(bytes.length);
                outToServer.write(bytes);
                try (DataInputStream dIn = new DataInputStream(s.getInputStream())) {
                    int length = dIn.readInt();                    // read length of incoming message
                    byte[] message = new byte[length];
                    
                    if (length > 0) {
                        dIn.readFully(message, 0, message.length); // read the message
                    }
                    String reply = new String(message);
                    if (reply.contains("OK")) {
                    } else {
                    }
                    s.close();
                } // read length of incoming message
 }
        } catch (IOException ex) {
            Logger.getLogger(sendCommOverHead.class.getName()).log(Level.SEVERE, null, ex);
            try {
                s.close();
            } catch (IOException ex1) {
                Logger.getLogger(sendCommOverHead.class.getName()).log(Level.SEVERE, null, ex1);
            }
        }

    }

}
