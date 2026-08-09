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
 * Runs image kernels on an OpenCL device.
 *
 * <p>The context, queue and compiled programs are held for the executor's
 * lifetime and reused. Compiling a kernel costs far more than running it — often
 * tens of milliseconds against a fraction of one — so an executor created per
 * tile would spend nearly all its time in the compiler and be slower than the
 * CPU. Create one per device per worker thread and reuse it.
 *
 * <p>Not thread-safe: an OpenCL command queue is not safe to share.
 */
public final class OpenCLKernelExecutor implements KernelExecutor {

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
    public OpenCLKernelExecutor(Device device) {
        if (device.backend() != Backend.OPENCL) {
            throw new IllegalArgumentException("Not an OpenCL device: " + device);
        }
        this.device = device;
        try {
            CL.setExceptionsEnabled(true);
            cl_device_id deviceId = resolve(device);
            cl_platform_id platform = platformOf(deviceId);

            cl_context_properties properties = new cl_context_properties();
            properties.addProperty(CL.CL_CONTEXT_PLATFORM, platform);
            this.context = CL.clCreateContext(properties, 1, new cl_device_id[]{deviceId},
                    null, null, null);
            this.queue = CL.clCreateCommandQueue(context, deviceId, 0, null);
        } catch (RuntimeException | UnsatisfiedLinkError ex) {
            throw new IllegalStateException("Could not open OpenCL device " + device, ex);
        }
    }

    /**
     * Finds the OpenCL device id behind a {@link Device}.
     *
     * <p>Devices are identified by their enumeration ordinal, which is stable
     * for a given machine and driver set — the same walk order that produced the
     * id is repeated here.
     */
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
    public int[] execute(ImageKernel kernel, int[] pixels, int width, int height) {
        if (closed) {
            throw new IllegalStateException("Executor is closed");
        }
        if (pixels.length != width * height) {
            throw new IllegalArgumentException("Buffer is " + pixels.length
                    + " but " + width + "x" + height + " needs " + width * height);
        }
        if (pixels.length == 0) {
            return new int[0];
        }

        cl_kernel clKernel = compiled.computeIfAbsent(kernel.name(), name -> build(kernel));
        int[] out = new int[pixels.length];
        long bytes = (long) Sizeof.cl_int * pixels.length;

        cl_mem input = null;
        cl_mem output = null;
        try {
            input = CL.clCreateBuffer(context, CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR,
                    bytes, Pointer.to(pixels), null);
            output = CL.clCreateBuffer(context, CL.CL_MEM_WRITE_ONLY, bytes, null, null);

            CL.clSetKernelArg(clKernel, 0, Sizeof.cl_mem, Pointer.to(input));
            CL.clSetKernelArg(clKernel, 1, Sizeof.cl_mem, Pointer.to(output));
            CL.clSetKernelArg(clKernel, 2, Sizeof.cl_int, Pointer.to(new int[]{width}));
            CL.clSetKernelArg(clKernel, 3, Sizeof.cl_int, Pointer.to(new int[]{height}));

            // 2-D range over the image; kernels guard against the rounding-up
            // that a device's preferred work-group size may introduce.
            CL.clEnqueueNDRangeKernel(queue, clKernel, 2, null,
                    new long[]{width, height}, null, 0, null, null);
            CL.clEnqueueReadBuffer(queue, output, CL.CL_TRUE, 0, bytes,
                    Pointer.to(out), 0, null, null);
        } finally {
            if (input != null) {
                CL.clReleaseMemObject(input);
            }
            if (output != null) {
                CL.clReleaseMemObject(output);
            }
        }
        return out;
    }

    private cl_kernel build(ImageKernel kernel) {
        cl_program program = CL.clCreateProgramWithSource(context, 1,
                new String[]{kernel.openClSource()}, null, null);
        try {
            CL.clBuildProgram(program, 0, null, null, null, null);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Kernel '" + kernel.name()
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
        compiled.clear();
        programs.clear();
        CL.clReleaseCommandQueue(queue);
        CL.clReleaseContext(context);
    }
}
