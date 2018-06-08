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
package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;

public class tools {

     public static String getCheckSum(String datafile) {
        StringBuilder sb = new StringBuilder("");
        if (datafile.substring(datafile.lastIndexOf(".")).equalsIgnoreCase("sha")) {
            System.out.println("Didn't computed CheckSum for " + datafile);
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            FileInputStream fis = new FileInputStream(datafile);
            byte[] dataBytes = new byte[1024];

            int nread = 0;

            while ((nread = fis.read(dataBytes)) != -1) {
                md.update(dataBytes, 0, nread);
            };

            byte[] mdbytes = md.digest();

            //convert the byte to hex format
            for (int i = 0; i < mdbytes.length; i++) {
                sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
            }

            System.out.println("Digest(in hex format):: " + sb.toString());
        } catch (NoSuchAlgorithmException | IOException ex) {
            Logger.getLogger(tools.class.getName()).log(Level.SEVERE, null, ex);
        }
        saveCheckSum(datafile + ".sha", sb.toString());
        return sb.toString();
    }

    public static String LoadCheckSum(String ld) {
        File f = new File(ld);
        if (!f.exists()) {
            getCheckSum(ld.substring(0, ld.length() - 3));
        }
        return readFile(ld).trim();
    }
    public static void saveCheckSum(String Filename, String con) {
        File f = new File(Filename);
        if (f.exists()) {
            f.delete();
        }
        try (PrintStream cstream = new PrintStream(Filename)) {
            cstream.append(con);
            cstream.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(tools.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static void generateCheckSumsInDirectory(String filename) {
        File f = new File(filename);
        if (f.isDirectory()) {
            //generateCheckSumsInDirectory(f.getAbsolutePath());
            for (File listFile : f.listFiles()) {
                if (listFile.isDirectory()) {
                    generateCheckSumsInDirectory(listFile.getAbsolutePath());
                } else {
                    getCheckSum(listFile.getAbsolutePath());
                }
            }
        } else {
            getCheckSum(f.getAbsolutePath());

        }
    }

    public static void copyFileUsingStream(File source, File dest) {
        if (dest.exists()) {
            dest.delete();
        }
        if (!dest.getParentFile().exists()) {
            dest.getParentFile().mkdirs();
        }
        if (!source.exists()) {
            try {
                System.out.println("" + source.getCanonicalPath() + " does not exist");
                return;
            } catch (IOException ex) {
                Logger.getLogger(tools.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        try (InputStream is = new FileInputStream(source); OutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            System.out.println("" + source.getAbsolutePath() + " copied to " + dest.getAbsolutePath() + " ");
        } catch (IOException ex) {
            Logger.getLogger(tools.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static void copyFileUsingStream(String pathtosrc, String pathtodest) {
        File source = new File(pathtosrc);
        File dest = new File(pathtodest);
        if (dest.exists()) {
            dest.delete();
        }
        if (!dest.getParentFile().exists()) {
            dest.getParentFile().mkdirs();
        }
        if (!source.exists()) {
            System.out.println("" + pathtosrc + " does not exist");
            return;
        }

        try (InputStream is = new FileInputStream(source); OutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            System.out.println("" + source.getAbsolutePath() + " copied to " + dest.getAbsolutePath() + " ");

        } catch (FileNotFoundException ex) {
            Logger.getLogger(tools.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(tools.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static String readFile(String location) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(location))) {
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
        } catch (IOException ex) {
            Logger.getLogger(tools.class.getName()).log(Level.SEVERE, null, ex);
        }
        return sb.toString();
    }

    public static JSONObject readJSONFile(String location) {
        String content = readFile(location).trim();
        return new JSONObject((content.length() < 1) ? "{}" : content);
    }

    public static void write(File f, String text) {
        f.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(f);
                PrintWriter pw = new PrintWriter(fw)) {
            pw.print(text);
            pw.close();
            fw.close();
        } catch (IOException ex) {
            Logger.getLogger(tools.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static void write(String path, String text) {
        File file = new File(path);
        file.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(new File(path));
                PrintWriter pw = new PrintWriter(fw)) {
            pw.print(text);
            pw.close();
            fw.close();
        } catch (IOException ex) {
            Logger.getLogger(tools.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
