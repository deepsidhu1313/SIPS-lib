/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import com.sun.management.OperatingSystemMXBean;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navdeep singh <navdeepsingh.sidhu95@gmail.com>
 */
public class TestDir {

    public static void main(String[] args) {
        ProcessBuilder pb = null;
        Process p;
        String CPUname = null;
        String workingDir = System.getProperty("user.dir");
        System.out.println("Current working directory : " + workingDir);
//        System.out.println("Current working directory : " + workingDir.substring(workingDir.lastIndexOf("\\")));
        OperatingSystemMXBean osMBean
                = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        RuntimeMXBean runtimeMBean = ManagementFactory.getRuntimeMXBean();
        System.out.println("SystemLoadAverage \t\t\t\t" + osMBean.getSystemLoadAverage());
        System.out.println("ProcessCpuLoad \t\t\t\t" + osMBean.getProcessCpuLoad());
        System.out.println("ProcessCpuTime \t\t\t\t" + osMBean.getProcessCpuTime());
        System.out.println("SystemCpuLoad \t\t\t\t" + osMBean.getSystemCpuLoad());
        System.out.println("TotalPhysicalMemorySize \t\t\t\t" + Runtime.getRuntime().maxMemory());
        System.out.println("FreePhysicalMemorySize \t\t\t\t" + Runtime.getRuntime().freeMemory());
        System.out.println("CmmitedMemorySize \t\t\t\t" + osMBean.getCommittedVirtualMemorySize());
        for (float i = 0; i < 10; i += 0.1) {
            //    System.out.println(""+i);
        }
//double load = osMBean.getSystemLoadAverage();
        //     getCheckSum("/home/nika/NetBeansProjects/ParallelFramework/workspace/MatMul.java/libs/lib1.jar");
    }

    public static String getCheckSum(String datafile) {
        StringBuilder sb = new StringBuilder("");
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
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(TestDir.class.getName()).log(Level.SEVERE, null, ex);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(TestDir.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(TestDir.class.getName()).log(Level.SEVERE, null, ex);
        }
        // saveCheckSum(datafile + ".sha", sb.toString());
        return sb.toString();
    }

}
