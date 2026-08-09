/*
 * Copyright (C) 2026 Navdeep Singh Sidhu
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
package in.co.s13.sips.lib.accelerator;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_device_id;
import org.jocl.cl_platform_id;

/**
 * OpenCL, covering CPUs, integrated GPUs and discrete GPUs from any vendor.
 *
 * <p>This is the primary accelerator path. One binding reaches Intel, AMD and
 * NVIDIA hardware, which is why it is implemented first: the alternative is a
 * separate binding per vendor.
 *
 * <p>Enumeration is guarded throughout. A node with no OpenCL runtime, or with a
 * broken one, must still boot and fall back to the CPU backend, so every failure
 * mode here resolves to "unavailable with a reason" rather than an exception.
 */
public final class OpenCLBackend implements AcceleratorBackend {

    private static final Logger LOG = Logger.getLogger(OpenCLBackend.class.getName());

    /** Not in JOCL's constant set in all versions; 1 means memory is shared with the host. */
    private static final int CL_DEVICE_HOST_UNIFIED_MEMORY = 0x1035;

    private List<Device> cached;
    private String reason;

    @Override
    public Backend backend() {
        return Backend.OPENCL;
    }

    @Override
    public boolean isAvailable() {
        return !devices().isEmpty();
    }

    @Override
    public String unavailableReason() {
        devices();
        return reason == null ? "" : reason;
    }

    @Override
    public List<Device> devices() {
        if (cached == null) {
            cached = List.copyOf(enumerate());
        }
        return cached;
    }

    private List<Device> enumerate() {
        List<Device> found = new ArrayList<>();
        try {
            CL.setExceptionsEnabled(false);

            int[] platformCount = new int[1];
            if (CL.clGetPlatformIDs(0, null, platformCount) != CL.CL_SUCCESS
                    || platformCount[0] == 0) {
                reason = "no OpenCL platform found; install a vendor OpenCL runtime";
                return found;
            }
            cl_platform_id[] platforms = new cl_platform_id[platformCount[0]];
            CL.clGetPlatformIDs(platforms.length, platforms, null);

            int ordinal = 0;
            for (cl_platform_id platform : platforms) {
                int[] deviceCount = new int[1];
                if (CL.clGetDeviceIDs(platform, CL.CL_DEVICE_TYPE_ALL, 0, null, deviceCount)
                        != CL.CL_SUCCESS || deviceCount[0] == 0) {
                    continue;
                }
                cl_device_id[] deviceIds = new cl_device_id[deviceCount[0]];
                CL.clGetDeviceIDs(platform, CL.CL_DEVICE_TYPE_ALL, deviceIds.length, deviceIds, null);

                for (cl_device_id deviceId : deviceIds) {
                    found.add(describe(deviceId, ordinal++));
                }
            }
            if (found.isEmpty()) {
                reason = "OpenCL platform present but it exposes no devices";
            }
        } catch (UnsatisfiedLinkError | NoClassDefFoundError error) {
            // The JOCL native library is missing or does not match this platform.
            reason = "OpenCL native library could not be loaded: " + error.getMessage();
            LOG.log(Level.FINE, "OpenCL unavailable", error);
        } catch (RuntimeException ex) {
            reason = "OpenCL enumeration failed: " + ex;
            LOG.log(Level.FINE, "OpenCL enumeration failed", ex);
        }
        return found;
    }

    private Device describe(cl_device_id deviceId, int ordinal) {
        String name = stringInfo(deviceId, CL.CL_DEVICE_NAME);
        String vendor = stringInfo(deviceId, CL.CL_DEVICE_VENDOR);
        long deviceType = longInfo(deviceId, CL.CL_DEVICE_TYPE, Sizeof.cl_long);
        int computeUnits = (int) longInfo(deviceId, CL.CL_DEVICE_MAX_COMPUTE_UNITS, Sizeof.cl_uint);
        long memory = longInfo(deviceId, CL.CL_DEVICE_GLOBAL_MEM_SIZE, Sizeof.cl_ulong);
        boolean unifiedMemory = longInfo(deviceId, CL_DEVICE_HOST_UNIFIED_MEMORY, Sizeof.cl_uint) == 1;

        return new Device(Backend.OPENCL, "opencl:" + ordinal, name, vendor,
                classify(deviceType, unifiedMemory, vendor),
                Math.max(1, computeUnits), Math.max(1, memory));
    }

    /**
     * OpenCL does not distinguish integrated from discrete GPUs directly. A
     * device whose memory is unified with the host is integrated; that
     * distinction matters because it changes transfer cost by an order of
     * magnitude and so changes which tiles are worth sending there.
     */
    private static AcceleratorType classify(long deviceType, boolean unifiedMemory, String vendor) {
        if ((deviceType & CL.CL_DEVICE_TYPE_CPU) != 0) {
            return AcceleratorType.CPU;
        }
        if ((deviceType & CL.CL_DEVICE_TYPE_GPU) != 0) {
            if (!unifiedMemory) {
                return AcceleratorType.DISCRETE_GPU;
            }
            // AMD markets its unified-memory parts as APUs.
            return vendor.toLowerCase().contains("amd") || vendor.toLowerCase().contains("advanced micro")
                    ? AcceleratorType.APU
                    : AcceleratorType.INTEGRATED_GPU;
        }
        if ((deviceType & CL.CL_DEVICE_TYPE_ACCELERATOR) != 0) {
            return AcceleratorType.FPGA;
        }
        return AcceleratorType.OTHER;
    }

    private static String stringInfo(cl_device_id deviceId, int parameter) {
        long[] size = new long[1];
        if (CL.clGetDeviceInfo(deviceId, parameter, 0, null, size) != CL.CL_SUCCESS || size[0] == 0) {
            return "";
        }
        byte[] buffer = new byte[(int) size[0]];
        CL.clGetDeviceInfo(deviceId, parameter, buffer.length, Pointer.to(buffer), null);
        // The value is NUL-terminated.
        return new String(buffer, 0, Math.max(0, buffer.length - 1)).strip();
    }

    private static long longInfo(cl_device_id deviceId, int parameter, int size) {
        long[] value = new long[1];
        if (CL.clGetDeviceInfo(deviceId, parameter, size, Pointer.to(value), null) != CL.CL_SUCCESS) {
            return 0L;
        }
        return value[0];
    }
}
