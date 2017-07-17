/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import db.SQLiteJDBC;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author nika
 */
public class AvgCalculator {
    public static void main(String[] args) {
        try {
            SQLiteJDBC db= new SQLiteJDBC();
            int scheduler=4;
            for (int i = 1; i <= 25; i++) {
                ResultSet rs=db.select("/home/nika/study/publications/1st/appdb2/dw-result (another copy).db", "SELECT AVG(TOTALTIME) FROM RESULTWHnew WHERE CHUNKSIZE='2501' AND TNODES='"+i+"' AND SCHEDULER='"+scheduler+"';");
                while (rs.next()) {
                    System.out.println(""+rs.getString(1));
                }
            }
            } catch (SQLException ex) {
            Logger.getLogger(AvgCalculator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
