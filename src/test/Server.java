/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 *
 * @author navdeep singh <navdeepsingh.sidhu95@gmail.com>
 */
public class Server implements Runnable {

    private ServerSocket serverSocket = null;
    private Socket socket = null;
    private ObjectInputStream inStream = null;

    public Server() {

    }

    @Override
    public void run() {

        try {
            serverSocket = new ServerSocket(4445);
            socket = serverSocket.accept();
            DataInputStream dIn = new DataInputStream(socket.getInputStream());
            OutputStream os = socket.getOutputStream();
            DataOutputStream outToClient = new DataOutputStream(os);

            System.out.println("Connected");
            File myFile = new File("store/lib1.jar");
            long flength = myFile.length();
            System.out.println("File Length"+flength);
            outToClient.writeLong(flength);
            FileInputStream fis;
            BufferedInputStream bis;
            byte[] mybytearray = new byte[(int)flength];
            fis = new FileInputStream(myFile);
            bis = new BufferedInputStream(fis);
            int theByte = 0;
            System.out.println("Sending " + myFile.getAbsolutePath() + "(" + myFile.length() + " bytes)");
               while ((theByte = bis.read()) != -1) {
             outToClient.write(theByte);
             // bos.flush();
             }
            /*int count;
            BufferedOutputStream bos= new BufferedOutputStream(os);
            while ((count = bis.read(mybytearray))>0) {
                bos.write(mybytearray, 0, count);
            }*/

            bis.close();
            socket.close();

        } catch (SocketException se) {

            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Thread t = new Thread(new Server());
        t.start();
    }
}
