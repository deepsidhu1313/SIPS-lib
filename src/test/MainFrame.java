/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.SecureRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 *
 * @author navdeep singh <navdeepsingh.sidhu95@gmail.com>
 */
public class MainFrame {
public static JLabel jl[]= new JLabel[2];
public static JTextArea jta= new JTextArea();
public static JScrollBar verticalScrollBar;
    public MainFrame() {
   JFrame jf= new JFrame();
        // jl[0]= new JLabel("Thread 1");
         //jl[1]= new JLabel("Thread 2");
        //jl[0].setBounds(10,10, 500, 40);
   JScrollPane sp = new JScrollPane(jta);
        sp.setBounds(10,50, 500, 400);
   
        JButton b1= new JButton("Start");
        b1.addActionListener(new ActionListener() {

       @Override
       public void actionPerformed(ActionEvent e) {
           for (int i = 0; i < 2; i++) {
               Thread t= new Thread(new simpleThread(i));
           t.start();
           }
           System.out.println("Button Clicked");
       }
   });
        b1.setBounds(10,600, 200, 40);
       jf.add(sp);
      verticalScrollBar = sp.getVerticalScrollBar(); //jf.add(jl[1]);
       jf.add(b1);
        jf.setLayout(null);
        jf.setSize(700, 700);
       jf.setVisible(true);
    
    }
   public static String encryptString(String str) throws Exception {
        return encrypt("KiNgKeePer143",str);
    }
    public static String decryptString(String str) throws Exception {
        return decrypt("KiNgKeePer143", str);
    }

    public static String encrypt(String seed, String cleartext) throws Exception {
        byte[] rawKey = getRawKey(seed.getBytes());
        byte[] result = encrypt(rawKey, cleartext.getBytes());
        return toHex(result);
    }

    public static String decrypt(String seed, String encrypted) throws Exception {
        byte[] rawKey = getRawKey(seed.getBytes());
        byte[] enc = toByte(encrypted);
        byte[] result = decrypt(rawKey, enc);
        return new String(result);
    }

    private static byte[] getRawKey(byte[] seed) throws Exception {
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        sr.setSeed(seed);
        kgen.init(128, sr); // 192 and 256 bits may not be available
        SecretKey skey = kgen.generateKey();
        byte[] raw = skey.getEncoded();
        return raw;
    }


    private static byte[] encrypt(byte[] raw, byte[] clear) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
        byte[] encrypted = cipher.doFinal(clear);
        return encrypted;
    }

    private static byte[] decrypt(byte[] raw, byte[] encrypted) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, skeySpec);
        byte[] decrypted = cipher.doFinal(encrypted);
        return decrypted;
    }

    public static String toHex(String txt) {
        return toHex(txt.getBytes());
    }
    public static String fromHex(String hex) {
        return new String(toByte(hex));
    }

    public static byte[] toByte(String hexString) {
        int len = hexString.length()/2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++)
            result[i] = Integer.valueOf(hexString.substring(2*i, 2*i+2), 16).byteValue();
        return result;
    }

    public static String toHex(byte[] buf) {
        if (buf == null)
            return "";
        StringBuffer result = new StringBuffer(2*buf.length);
        for (int i = 0; i < buf.length; i++) {
            appendHex(result, buf[i]);
        }
        return result.toString();
    }
    private final static String HEX = "0123456789ABCDEF";
    private static void appendHex(StringBuffer sb, byte b) {
        sb.append(HEX.charAt((b>>4)&0x0f)).append(HEX.charAt(b&0x0f));
    }

    
    public static void main(String[] args) {
    try {
        String enc=MainFrame.encryptString("Hello");
        System.out. println("Encrpted String "+enc);
        System.out.println("Decrypted "+MainFrame.decryptString(enc));
    } catch (Exception ex) {
        Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
    }
    }
}
