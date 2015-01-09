/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

/**
 *
 * @author navdeep singh <navdeepsingh.sidhu95@gmail.com>
 */
public class Server implements Runnable{
  private ServerSocket serverSocket = null;
    private Socket socket = null;
    private ObjectInputStream inStream = null;
 
 
	
    public Server() {
	
    }
    @Override
    public void run() {
    
        
         try {
            serverSocket = new ServerSocket(4445);
            socket = serverSocket.accept();System.out.println("Connected");
            inStream = new ObjectInputStream(socket.getInputStream());
             Integer b []= (Integer[]) inStream.readObject();
            System.out.println("Object received = " + b.length);
            socket.close();
 
        } catch (SocketException se) {

            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException cn) {
            cn.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        Thread t= new Thread(new Server());
        t.start();
    }
}
