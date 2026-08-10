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

import java.util.HashMap;
import java.util.Map;
import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;

/**
 * Runs {@link MatrixKernel}s on an OpenCL device.
 *
 * <p>Context, queue and compiled programs are held for the executor's lifetime.
 * Compiling costs tens of milliseconds against a fraction of one to run, so an
 * executor created per multiply would spend nearly all its time in the compiler
 * and lose to the CPU — the same trap the image executor documents, and the
 * reason both are built to be reused.
 *
 * <p>Not thread-safe: an OpenCL command queue cannot be shared.
 */
public final class OpenCLMatrixExecutor implements MatrixKernelExecutor {

    private final Device device;
    private final cl_context context;
    private final cl_command_queue queue;
    private final Map<String, cl_kernel> compiled = new HashMap<>();
    private final Map<String, cl_program> programs = new HashMap<>();
    private boolean closed;

    /**
     * Opens an executor for an OpenCL device.
     *
     * @throws IllegalArgumentException if the device is not an OpenCL device
     * @throws IllegalStateException if the OpenCL runtime cannot be used
     */
    public OpenCLMatrixExecutor(Device device) {
        if (device.backend() != Backend.OPENCL) {
            throw new IllegalArgumentException(device + " is not an OpenCL device");
        }
        this.device = device;
        try {
            CL.setExceptionsEnabled(true);
            cl_device_id deviceId = resolve(device);
            cl_context_properties properties = new cl_context_properties();
            properties.addProperty(CL.CL_CONTEXT_PLATFORM, platformOf(deviceId));
            this.context = CL.clCreateContext(properties, 1, new cl_device_id[]{deviceId},
                    null, null, null);
            this.queue = CL.clCreateCommandQueue(context, deviceId, 0, null);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Could not open " + device + " for matrix work", ex);
        }
    }

    private static cl_device_id resolve(Device device) {
        int wanted = Integer.parseInt(device.id().substring(device.id().indexOf(':') + 1));
        int[] platformCount = new int[1];
        CL.clGetPlatformIDs(0, null, platformCount);
        cl_platform_id[] platforms = new cl_platform_id[platformCount[0]];
        CL.clGetPlatformIDs(platforms.length, platforms, null);

        int ordinal = 0;
        for (cl_platform_id platform : platforms) {
            int[] deviceCount = new int[1];
            CL.clGetDeviceIDs(platform, CL.CL_DEVICE_TYPE_ALL, 0, null, deviceCount);
            if (deviceCount[0] == 0) {
                continue;
            }
            cl_device_id[] ids = new cl_device_id[deviceCount[0]];
            CL.clGetDeviceIDs(platform, CL.CL_DEVICE_TYPE_ALL, ids.length, ids, null);
            for (cl_device_id id : ids) {
                if (ordinal++ == wanted) {
                    return id;
                }
            }
        }
        throw new IllegalStateException("OpenCL device disappeared between enumeration "
                + "and use: " + device);
    }

    private static cl_platform_id platformOf(cl_device_id deviceId) {
        cl_platform_id[] platform = new cl_platform_id[1];
        CL.clGetDeviceInfo(deviceId, CL.CL_DEVICE_PLATFORM, Sizeof.cl_platform_id,
                Pointer.to(platform), null);
        return platform[0];
    }

    @Override
    public Device device() {
        return device;
    }

    @Override
    public boolean supports(MatrixKernel kernel) {
        int tile = kernel.tileSize();
        if (tile <= 0) {
            return true;
        }
        try {
            cl_kernel clKernel = compiled.computeIfAbsent(kernel.name(), name -> build(kernel));
            // The kernel's own limit, not the device's: local memory and
            // register pressure can push a kernel's maximum group below what
            // the device advertises.
            long[] maxGroup = new long[1];
            CL.clGetKernelWorkGroupInfo(clKernel, resolve(device),
                    CL.CL_KERNEL_WORK_GROUP_SIZE, Sizeof.size_t, Pointer.to(maxGroup), null);
            if ((long) tile * tile > maxGroup[0]) {
                return false;
            }
            long[] itemSizes = new long[3];
            CL.clGetDeviceInfo(device2(), CL.CL_DEVICE_MAX_WORK_ITEM_SIZES,
                    (long) Sizeof.size_t * 3, Pointer.to(itemSizes), null);
            return tile <= itemSizes[0] && tile <= itemSizes[1];
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private cl_device_id device2() {
        return resolve(device);
    }

    @Override
    public float[] execute(MatrixKernel kernel, float[] a, float[] b, int m, int k, int n) {
        if (closed) {
            throw new IllegalStateException("Executor is closed");
        }
        if (m <= 0 || k <= 0 || n <= 0) {
            throw new IllegalArgumentException("dimensions must be positive: "
                    + m + "x" + k + " times " + k + "x" + n);
        }
        if (a.length != m * k && a.length != k * m) {
            throw new IllegalArgumentException("A is " + a.length + " values, expected "
                    + (m * k));
        }
        if (b.length != k * n) {
            throw new IllegalArgumentException("B is " + b.length + " values, expected "
                    + (k * n));
        }

        cl_kernel clKernel = compiled.computeIfAbsent(kernel.name(), name -> build(kernel));
        float[] out = new float[m * n];

        cl_mem bufferA = null;
        cl_mem bufferB = null;
        cl_mem bufferC = null;
        try {
            bufferA = CL.clCreateBuffer(context, CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR,
                    (long) Sizeof.cl_float * a.length, Pointer.to(a), null);
            bufferB = CL.clCreateBuffer(context, CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR,
                    (long) Sizeof.cl_float * b.length, Pointer.to(b), null);
            bufferC = CL.clCreateBuffer(context, CL.CL_MEM_WRITE_ONLY,
                    (long) Sizeof.cl_float * out.length, null, null);

            CL.clSetKernelArg(clKernel, 0, Sizeof.cl_mem, Pointer.to(bufferA));
            CL.clSetKernelArg(clKernel, 1, Sizeof.cl_mem, Pointer.to(bufferB));
            CL.clSetKernelArg(clKernel, 2, Sizeof.cl_mem, Pointer.to(bufferC));
            CL.clSetKernelArg(clKernel, 3, Sizeof.cl_int, Pointer.to(new int[]{m}));
            CL.clSetKernelArg(clKernel, 4, Sizeof.cl_int, Pointer.to(new int[]{k}));
            CL.clSetKernelArg(clKernel, 5, Sizeof.cl_int, Pointer.to(new int[]{n}));

            // One work item per output element. A tiled kernel needs its
            // work-group size fixed and the range rounded up to a multiple of
            // it; every kernel guards its own bounds, so the extra items are
            // harmless.
            int tile = kernel.tileSize();
            long[] global = tile > 0
                    ? new long[]{roundUp(m, tile), roundUp(n, tile)}
                    : new long[]{m, n};
            long[] local = tile > 0 ? new long[]{tile, tile} : null;
            CL.clEnqueueNDRangeKernel(queue, clKernel, 2, null, global, local, 0, null, null);
            CL.clEnqueueReadBuffer(queue, bufferC, CL.CL_TRUE, 0,
                    (long) Sizeof.cl_float * out.length, Pointer.to(out), 0, null, null);
        } finally {
            release(bufferA);
            release(bufferB);
            release(bufferC);
        }
        return out;
    }

    private static long roundUp(int value, int multiple) {
        return ((value + multiple - 1L) / multiple) * multiple;
    }

    private static void release(cl_mem buffer) {
        if (buffer != null) {
            CL.clReleaseMemObject(buffer);
        }
    }

    private cl_kernel build(MatrixKernel kernel) {
        cl_program program = CL.clCreateProgramWithSource(context, 1,
                new String[]{kernel.openClSource()}, null, null);
        try {
            CL.clBuildProgram(program, 0, null, null, null, null);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Matrix kernel '" + kernel.name()
                    + "' failed to build on " + device + ": " + buildLog(program), ex);
        }
        programs.put(kernel.name(), program);
        return CL.clCreateKernel(program, kernel.name(), null);
    }

    /** The compiler's diagnostics, which are otherwise lost inside the driver. */
    private String buildLog(cl_program program) {
        try {
            cl_device_id deviceId = resolve(device);
            long[] size = new long[1];
            CL.clGetProgramBuildInfo(program, deviceId, CL.CL_PROGRAM_BUILD_LOG, 0, null, size);
            byte[] log = new byte[(int) size[0]];
            CL.clGetProgramBuildInfo(program, deviceId, CL.CL_PROGRAM_BUILD_LOG,
                    log.length, Pointer.to(log), null);
            return new String(log, 0, Math.max(0, log.length - 1)).strip();
        } catch (RuntimeException ex) {
            return "(build log unavailable)";
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        compiled.values().forEach(CL::clReleaseKernel);
        programs.values().forEach(CL::clReleaseProgram);
        CL.clReleaseCommandQueue(queue);
        CL.clReleaseContext(context);
    }
}
