/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

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
        System.out.println("TotalPhysicalMemorySize \t\t\t\t" + osMBean.getTotalPhysicalMemorySize());

//double load = osMBean.getSystemLoadAverage();
    }
}
