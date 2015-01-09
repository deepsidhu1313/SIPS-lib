/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Action;
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
   
    
    public static void main(String[] args) {
        new MainFrame();
        }
}
