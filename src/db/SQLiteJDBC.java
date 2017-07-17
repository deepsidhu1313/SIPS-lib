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
package db;

/**
 *
 * @author Nika
 */
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SQLiteJDBC {

    Connection c = null;
    Statement stmt = null;
    ResultSet rs = null;

    public SQLiteJDBC() {
    }

    public void closeConnection() {
        try {
            stmt.close();
            c.close();
        } catch (SQLException ex) {
            Logger.getLogger(SQLiteJDBC.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void createtable(String db, String sql) {
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:" + db);
            //System.out.println("Opened database successfully");

            stmt = c.createStatement();
            stmt.executeUpdate(sql);
            // stmt.close();
            // c.close();
            System.out.println(sql);
            System.out.println("Table created successfully on DB " + db);

        } catch (ClassNotFoundException | SQLException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());

        }

    }

    public void insert(String db, String sql) {
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:" + db);
            c.setAutoCommit(false);
            //  System.out.println("Opened database successfully");

            stmt = c.createStatement();
            stmt.executeUpdate(sql);

            //    stmt.close();
            c.commit();
            //    c.close();
            System.out.println(sql);
            System.out.println("Records created successfully on DB " + db);

        } catch (ClassNotFoundException | SQLException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());

        }
    }

    public ResultSet select(String db, String sql) throws SQLException {

        ResultSet rs2 = null;
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:" + db);
            c.setAutoCommit(false);
            //  System.out.println("Opened database successfully");

            stmt = c.createStatement();
            rs2 = stmt.executeQuery(sql);
//            System.out.println(sql);
//            System.out.println("Select Operation done successfully on DB " + db);
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.err.println("Select Operation was not done successfully on DB " + db);
            return null;
        }

        return rs2;

    }

    public void Update(String db, String sql) {
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:" + db);
            c.setAutoCommit(false);

            stmt = c.createStatement();
            stmt.executeUpdate(sql);
            c.commit();

            //      stmt.close();
            //    c.close();
            System.out.println(sql);
            System.out.println("Update Operation done successfully on DB " + db);
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());

        }

    }

    public void Update(String db, String sql, Object obj) {
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:" + db);
            c.setAutoCommit(false);
            PreparedStatement ps = null;

            byte[] data;
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos))) {
                    oos.writeObject(obj);
                    oos.reset();
                    oos.flush();
                }
                data = bos.toByteArray();
            }

//            sql = "insert into javaobject (javaObject) values(?)";
            ps = c.prepareStatement(sql);
            ps.setObject(1, data);
            ps.executeUpdate();

            c.commit();

            System.out.println(sql);
            System.out.println("Update Operation done successfully on DB " + db);
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());

        } catch (IOException ex) {
            Logger.getLogger(SQLiteJDBC.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public Object getObject(String db, String sql) {
        Object rmObj = null;
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:" + db);
            c.setAutoCommit(false);
            PreparedStatement ps = null;
            ResultSet rs = null;
            //String sql=null;

            //sql="select * from javaobject where id=1";
            ps = c.prepareStatement(sql);

            rs = ps.executeQuery();

            if (rs.next()) {
                ByteArrayInputStream bais;

                ObjectInputStream ins;

                try {

                    bais = new ByteArrayInputStream(rs.getBytes("VALUE"));

                    ins = new ObjectInputStream(bais);

                    ArrayList mc = (ArrayList) ins.readObject();

                    System.out.println("Object in value ::" + mc);
                    ins.close();

                    rmObj = mc;
                } catch (Exception e) {

                    e.printStackTrace();
                }

            }

            return rmObj;
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(SQLiteJDBC.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SQLException ex) {
            Logger.getLogger(SQLiteJDBC.class.getName()).log(Level.SEVERE, null, ex);
        }
        return rmObj;
    }

    public void delete(String db, String sql) {
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:" + db);
            c.setAutoCommit(false);
            // System.out.println("Opened database successfully");

            stmt = c.createStatement();
            stmt.executeUpdate(sql);
            c.commit();

            // stmt.close();
            // c.close();
            System.out.println(sql);
            System.out.println("Delete Operation done successfully on DB " + db);
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());

        }
    }

    public void execute(String db, String sql) {
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:" + db);
            c.setAutoCommit(false);
            // System.out.println("Opened database successfully");

            stmt = c.createStatement();
            stmt.executeUpdate(sql);
            c.commit();

            // stmt.close();
            // c.close();
            System.out.println(sql);
            System.out.println("Query Executed Operation done successfully on DB " + db);
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());

        }

    }

    public void toFile(String dbloc, String sql, String file) {
        try {
            ResultSet result = this.select(dbloc, sql);
            ResultSetMetaData rsm = result.getMetaData();
            int columncount = rsm.getColumnCount();
            PrintStream out = new PrintStream(file); //new AppendFileStream
            for (int i = 1; i <= columncount; i++) {
                out.print(rsm.getColumnName(i) + "\t");
            }
            out.print("\n");
            while (result.next()) {
                for (int i = 1; i <= columncount; i++) {
                    out.print(result.getString(i) + "\t");
                }
                out.print("\n");

            }
            out.close();
        } catch (SQLException ex) {
            Logger.getLogger(SQLiteJDBC.class.getName()).log(Level.SEVERE, null, ex);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(SQLiteJDBC.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static void main(String args[]) {
        SQLiteJDBC sqLiteJDBC = new SQLiteJDBC();
    }
}
