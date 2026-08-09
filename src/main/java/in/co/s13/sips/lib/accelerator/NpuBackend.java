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

/**
 * Neural processing units.
 *
 * <p>Not yet implemented. NPUs have no common API: Apple exposes the Neural
 * Engine through CoreML, Intel through OpenVINO, AMD through XDNA/Ryzen AI and
 * Qualcomm through the QNN SDK. The practical route is to target ONNX Runtime
 * and let it dispatch to whichever execution provider is installed, rather than
 * binding four vendor SDKs.
 *
 * <p>This matters only for the inference workload; classical filters and batch
 * processing are served by OpenCL.
 */
public final class NpuBackend extends UnavailableBackend {

    @Override
    public Backend backend() {
        return Backend.NPU_RUNTIME;
    }

    @Override
    protected String probe() {
        if (osContains("mac") && "x86_64".equals(System.getProperty("os.arch"))) {
            return "Intel Macs have no Neural Engine; NPU inference is unavailable on this host";
        }
        return "no NPU runtime detected; install ONNX Runtime with a vendor execution provider";
    }
}
