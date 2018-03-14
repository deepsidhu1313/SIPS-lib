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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;

/**
 *
 * @author Nika
 */
public class sendCacheMiss implements Runnable {

    private String ipadd, PID, chunkno, nodeUUID, senderUUID;
    private int taskServerPort = 13133;
    private long dataSize;
    private double speed;

    public sendCacheMiss(String ipadd, String PID, String chunkno, String nodeUUID, String senderUUID, long dataSize, double speed) {
        this.ipadd = ipadd;
        this.PID = PID;
        this.chunkno = chunkno;
        this.nodeUUID = nodeUUID;
        this.senderUUID = senderUUID;
        this.dataSize = dataSize;
        this.speed = speed;
    }

    @Override
    public void run() {

        try (Socket s = new Socket(ipadd, taskServerPort); OutputStream os = s.getOutputStream(); DataOutputStream outToServer = new DataOutputStream(os)) {
            JSONObject sendmsgJsonObj = new JSONObject();
            sendmsgJsonObj.put("Command", "CACHEMISS");
            JSONObject sendmsgBodyJsonObj = new JSONObject();
            sendmsgBodyJsonObj.put("PID", PID);
            sendmsgBodyJsonObj.put("UUID", nodeUUID);
            sendmsgBodyJsonObj.put("SENDER_UUID", senderUUID);
            sendmsgBodyJsonObj.put("CNO", chunkno);
            sendmsgBodyJsonObj.put("SIZE", dataSize);
            sendmsgBodyJsonObj.put("SPEED", speed);
            sendmsgJsonObj.put("Body", sendmsgBodyJsonObj);
            String sendmsg = sendmsgJsonObj.toString();

            byte[] bytes = sendmsg.getBytes("UTF-8");
            outToServer.writeInt(bytes.length);
            outToServer.write(bytes);
//            try (DataInputStream dIn = new DataInputStream(s.getInputStream())) {
//                int length = dIn.readInt();                    // read length of incoming message
//                byte[] message = new byte[length];
//
//                if (length > 0) {
//                    dIn.readFully(message, 0, message.length); // read the message
//                }
//                String reply = new String(message);
//                if (reply.contains("OK")) {
//                } else {
//                }
//            } // read length of incoming message
        } catch (IOException ex) {
            Logger.getLogger(sendCacheMiss.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
