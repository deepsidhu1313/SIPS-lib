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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The built-in image kernels.
 *
 * <p>Every kernel is integer-only. Floating point would be the obvious way to
 * write luma weights or an edge magnitude, but OpenCL leaves fused
 * multiply-add, denormal handling and {@code sqrt} precision
 * implementation-defined, so a float kernel gives subtly different answers on
 * different devices. With one tile on a GPU and its neighbour on a CPU, those
 * differences appear as a seam. Integer arithmetic is exact everywhere.
 *
 * <p>Two consequences of that choice, both deliberate:
 * <ul>
 *   <li>Luma uses the fixed-point weights 77/150/29 over 256, the standard
 *       integer approximation of 0.299/0.587/0.114.</li>
 *   <li>Sobel uses {@code |gx| + |gy|} rather than {@code sqrt(gx²+gy²)}. This
 *       is the usual approximation and avoids {@code sqrt} entirely.</li>
 * </ul>
 */
public final class Kernels {

    private Kernels() {
    }

    /** Shared prologue: bounds-clamped neighbour fetch, used by the 3x3 kernels. */
    private static final String HELPERS = """
            int clampi(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
            int at(__global const int* in, int x, int y, int width, int height) {
                return in[clampi(y,0,height-1) * width + clampi(x,0,width-1)];
            }
            int luma(int p) {
                return (77 * ((p >> 16) & 0xFF) + 150 * ((p >> 8) & 0xFF) + 29 * (p & 0xFF)) >> 8;
            }
            """;

    public static final ImageKernel INVERT = new ImageKernel() {
        @Override
        public String name() {
            return "invert";
        }

        @Override
        public String openClSource() {
            return """
                   __kernel void invert(__global const int* in, __global int* out,
                                        int width, int height) {
                       int x = get_global_id(0); int y = get_global_id(1);
                       if (x >= width || y >= height) return;
                       int i = y * width + x;
                       out[i] = (~in[i]) & 0x00FFFFFF;
                   }
                   """;
        }

        @Override
        public int[] applyOnCpu(int[] pixels, int width, int height) {
            int[] out = new int[pixels.length];
            for (int i = 0; i < pixels.length; i++) {
                out[i] = (~pixels[i]) & 0x00FFFFFF;
            }
            return out;
        }
    };

    public static final ImageKernel GRAYSCALE = new ImageKernel() {
        @Override
        public String name() {
            return "grayscale";
        }

        @Override
        public String openClSource() {
            return HELPERS + """
                   __kernel void grayscale(__global const int* in, __global int* out,
                                           int width, int height) {
                       int x = get_global_id(0); int y = get_global_id(1);
                       if (x >= width || y >= height) return;
                       int i = y * width + x;
                       int g = luma(in[i]);
                       out[i] = (g << 16) | (g << 8) | g;
                   }
                   """;
        }

        @Override
        public int[] applyOnCpu(int[] pixels, int width, int height) {
            int[] out = new int[pixels.length];
            for (int i = 0; i < pixels.length; i++) {
                int g = luma(pixels[i]);
                out[i] = (g << 16) | (g << 8) | g;
            }
            return out;
        }
    };

    public static final ImageKernel BLUR3 = new ImageKernel() {
        @Override
        public String name() {
            return "blur3";
        }

        @Override
        public String openClSource() {
            return HELPERS + """
                   __kernel void blur3(__global const int* in, __global int* out,
                                       int width, int height) {
                       int x = get_global_id(0); int y = get_global_id(1);
                       if (x >= width || y >= height) return;
                       int r = 0, g = 0, b = 0;
                       for (int dy = -1; dy <= 1; dy++) {
                           for (int dx = -1; dx <= 1; dx++) {
                               int p = at(in, x + dx, y + dy, width, height);
                               r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF;
                           }
                       }
                       out[y * width + x] = ((r / 9) << 16) | ((g / 9) << 8) | (b / 9);
                   }
                   """;
        }

        @Override
        public int[] applyOnCpu(int[] pixels, int width, int height) {
            int[] out = new int[pixels.length];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int r = 0, g = 0, b = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int p = at(pixels, x + dx, y + dy, width, height);
                            r += (p >> 16) & 0xFF;
                            g += (p >> 8) & 0xFF;
                            b += p & 0xFF;
                        }
                    }
                    out[y * width + x] = ((r / 9) << 16) | ((g / 9) << 8) | (b / 9);
                }
            }
            return out;
        }
    };

    public static final ImageKernel SHARPEN = new ImageKernel() {
        @Override
        public String name() {
            return "sharpen";
        }

        @Override
        public String openClSource() {
            return HELPERS + """
                   __kernel void sharpen(__global const int* in, __global int* out,
                                         int width, int height) {
                       int x = get_global_id(0); int y = get_global_id(1);
                       if (x >= width || y >= height) return;
                       int c = at(in, x, y, width, height);
                       int n = at(in, x, y - 1, width, height);
                       int s = at(in, x, y + 1, width, height);
                       int w = at(in, x - 1, y, width, height);
                       int e = at(in, x + 1, y, width, height);
                       int r = 5 * ((c >> 16) & 0xFF) - ((n >> 16) & 0xFF) - ((s >> 16) & 0xFF)
                                 - ((w >> 16) & 0xFF) - ((e >> 16) & 0xFF);
                       int g = 5 * ((c >> 8) & 0xFF) - ((n >> 8) & 0xFF) - ((s >> 8) & 0xFF)
                                 - ((w >> 8) & 0xFF) - ((e >> 8) & 0xFF);
                       int b = 5 * (c & 0xFF) - (n & 0xFF) - (s & 0xFF) - (w & 0xFF) - (e & 0xFF);
                       out[y * width + x] = (clampi(r,0,255) << 16) | (clampi(g,0,255) << 8)
                                            | clampi(b,0,255);
                   }
                   """;
        }

        @Override
        public int[] applyOnCpu(int[] pixels, int width, int height) {
            int[] out = new int[pixels.length];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int c = at(pixels, x, y, width, height);
                    int n = at(pixels, x, y - 1, width, height);
                    int s = at(pixels, x, y + 1, width, height);
                    int w = at(pixels, x - 1, y, width, height);
                    int e = at(pixels, x + 1, y, width, height);
                    int r = 5 * ((c >> 16) & 0xFF) - ((n >> 16) & 0xFF) - ((s >> 16) & 0xFF)
                            - ((w >> 16) & 0xFF) - ((e >> 16) & 0xFF);
                    int g = 5 * ((c >> 8) & 0xFF) - ((n >> 8) & 0xFF) - ((s >> 8) & 0xFF)
                            - ((w >> 8) & 0xFF) - ((e >> 8) & 0xFF);
                    int b = 5 * (c & 0xFF) - (n & 0xFF) - (s & 0xFF) - (w & 0xFF) - (e & 0xFF);
                    out[y * width + x] = (clamp(r, 0, 255) << 16) | (clamp(g, 0, 255) << 8)
                            | clamp(b, 0, 255);
                }
            }
            return out;
        }
    };

    public static final ImageKernel SOBEL = new ImageKernel() {
        @Override
        public String name() {
            return "sobel";
        }

        @Override
        public String openClSource() {
            return HELPERS + """
                   __kernel void sobel(__global const int* in, __global int* out,
                                       int width, int height) {
                       int x = get_global_id(0); int y = get_global_id(1);
                       if (x >= width || y >= height) return;
                       int gx = 0, gy = 0;
                       int wx[9] = {-1, 0, 1, -2, 0, 2, -1, 0, 1};
                       int wy[9] = {-1, -2, -1, 0, 0, 0, 1, 2, 1};
                       for (int dy = -1; dy <= 1; dy++) {
                           for (int dx = -1; dx <= 1; dx++) {
                               int l = luma(at(in, x + dx, y + dy, width, height));
                               int k = (dy + 1) * 3 + (dx + 1);
                               gx += l * wx[k]; gy += l * wy[k];
                           }
                       }
                       int m = abs(gx) + abs(gy);
                       m = clampi(m, 0, 255);
                       out[y * width + x] = (m << 16) | (m << 8) | m;
                   }
                   """;
        }

        @Override
        public int[] applyOnCpu(int[] pixels, int width, int height) {
            int[] wx = {-1, 0, 1, -2, 0, 2, -1, 0, 1};
            int[] wy = {-1, -2, -1, 0, 0, 0, 1, 2, 1};
            int[] out = new int[pixels.length];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int gx = 0, gy = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int l = luma(at(pixels, x + dx, y + dy, width, height));
                            int k = (dy + 1) * 3 + (dx + 1);
                            gx += l * wx[k];
                            gy += l * wy[k];
                        }
                    }
                    int m = clamp(Math.abs(gx) + Math.abs(gy), 0, 255);
                    out[y * width + x] = (m << 16) | (m << 8) | m;
                }
            }
            return out;
        }
    };

    private static final Map<String, ImageKernel> BY_NAME = new LinkedHashMap<>();

    static {
        for (ImageKernel kernel : new ImageKernel[]{INVERT, GRAYSCALE, BLUR3, SHARPEN, SOBEL}) {
            BY_NAME.put(kernel.name(), kernel);
        }
    }

    /** All built-in kernels, in a stable order. */
    public static Map<String, ImageKernel> all() {
        return Map.copyOf(BY_NAME);
    }

    /**
     * @throws IllegalArgumentException naming the available kernels, so a typo
     *         is self-correcting rather than a null downstream
     */
    public static ImageKernel byName(String name) {
        ImageKernel kernel = BY_NAME.get(name == null ? "" : name.toLowerCase());
        if (kernel == null) {
            throw new IllegalArgumentException("Unknown kernel: " + name
                    + ". Available: " + BY_NAME.keySet());
        }
        return kernel;
    }

    /** Fixed-point luma; matches the OpenCL helper exactly. */
    static int luma(int p) {
        return (77 * ((p >> 16) & 0xFF) + 150 * ((p >> 8) & 0xFF) + 29 * (p & 0xFF)) >> 8;
    }

    static int at(int[] pixels, int x, int y, int width, int height) {
        return pixels[clamp(y, 0, height - 1) * width + clamp(x, 0, width - 1)];
    }

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
