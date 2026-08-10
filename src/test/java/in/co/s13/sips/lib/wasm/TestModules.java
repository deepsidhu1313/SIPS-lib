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
package in.co.s13.sips.lib.wasm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assembles WebAssembly modules byte by byte for the tests.
 *
 * <p>Deliberately not a toolchain: the suite must run on any machine with a JVM,
 * which is the same property that made Chicory the right runtime in the first
 * place. Requiring clang or rustc to test the WASM path would undo it.
 *
 * <p>Modules built here import all six host functions in a fixed order, so their
 * function indices are the constants below and the module's own {@code run} is
 * index {@link #RUN}.
 */
public final class TestModules {

    public static final int INPUT_SIZE = 0;
    public static final int INPUT_READ = 1;
    public static final int OUTPUT_WRITE = 2;
    public static final int LOG = 3;
    public static final int BREAK_ALL = 4;
    public static final int BREAK_AFTER = 5;
    public static final int RUN = 6;

    /** Locals 0 and 1 are the range parameters, so a declared local starts here. */
    public static final int FIRST_LOCAL = 2;

    // Instruction opcodes used by the test bodies.
    public static final byte UNREACHABLE = 0x00;
    public static final byte DROP = 0x1a;
    public static final byte CALL = 0x10;
    public static final byte LOCAL_GET = 0x20;
    public static final byte LOCAL_SET = 0x21;
    public static final byte I32_CONST = 0x41;
    public static final byte I64_CONST = 0x42;
    public static final byte I64_STORE = 0x37;
    public static final byte END = 0x0b;

    private TestModules() {
    }

    /**
     * A module with no imports at all, proving the host interface is optional.
     */
    public static Path bare(Path dir, String name, byte[] body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header());
        out.write(section(1, vector(runType())));
        out.write(section(3, vector(new byte[]{0})));
        out.write(section(7, vector(exportEntry("run", 0x00, 0))));
        out.write(section(10, vector(code(0, body))));
        return write(dir, name, out.toByteArray());
    }

    /**
     * A module importing the full SIPS host interface and exporting one page of
     * memory as {@code memory}.
     *
     * @param locals how many {@code i32} locals the body declares
     */
    public static Path hosted(Path dir, String name, int locals, byte[] body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header());
        out.write(section(1, vector(
                type(new byte[]{}, new byte[]{0x7f}),                             // 0 ()->i32
                type(new byte[]{0x7f, 0x7f, 0x7f}, new byte[]{0x7f}),             // 1
                type(new byte[]{0x7f, 0x7f}, new byte[]{}),                       // 2
                type(new byte[]{0x7e, 0x7f, 0x7f}, new byte[]{}),                 // 3
                type(new byte[]{0x7e}, new byte[]{}),                             // 4
                runType())));                                                     // 5
        out.write(section(2, vector(
                importEntry("input_size", 0),
                importEntry("input_read", 1),
                importEntry("output_write", 2),
                importEntry("log", 2),
                importEntry("break_all", 3),
                importEntry("break_after", 4))));
        out.write(section(3, vector(new byte[]{5})));
        out.write(section(5, vector(new byte[]{0x00, 1})));                       // one page, no max
        out.write(section(7, vector(
                exportEntry("run", 0x00, RUN),
                exportEntry("memory", 0x02, 0))));
        out.write(section(10, vector(code(locals, body))));
        return write(dir, name, out.toByteArray());
    }

    /** {@code call $index} */
    public static byte[] call(int index) {
        return new byte[]{CALL, (byte) index};
    }

    /** {@code i32.const value} */
    public static byte[] i32(int value) {
        return concat(new byte[]{I32_CONST}, signedLeb(value));
    }

    /** {@code i64.const value} */
    public static byte[] i64(long value) {
        return concat(new byte[]{I64_CONST}, signedLeb(value));
    }

    public static byte[] localGet(int index) {
        return new byte[]{LOCAL_GET, (byte) index};
    }

    public static byte[] localSet(int index) {
        return new byte[]{LOCAL_SET, (byte) index};
    }

    /** {@code i64.store offset=n} with natural alignment. */
    public static byte[] i64Store(int offset) {
        return new byte[]{I64_STORE, 3, (byte) offset};
    }

    public static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    /** Reads the whole input into memory at 0 and leaves its length in {@code local}. */
    public static byte[] readAllInput(int local) {
        return concat(
                call(INPUT_SIZE), localSet(local),
                i32(0), i32(0), localGet(local), call(INPUT_READ), new byte[]{DROP});
    }

    /** Interprets the first eight bytes of a result as a little-endian long. */
    public static long readLong(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    // ---- binary format ----

    private static byte[] header() {
        return concat(new byte[]{0x00, 'a', 's', 'm'},
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1).array());
    }

    private static byte[] runType() {
        return type(new byte[]{0x7e, 0x7e}, new byte[]{0x7e});
    }

    private static byte[] type(byte[] params, byte[] results) {
        return concat(new byte[]{0x60}, unsignedLeb(params.length), params,
                unsignedLeb(results.length), results);
    }

    private static byte[] importEntry(String field, int typeIndex) {
        return concat(name(WasmHost.NAMESPACE), name(field),
                new byte[]{0x00}, unsignedLeb(typeIndex));
    }

    private static byte[] exportEntry(String field, int kind, int index) {
        return concat(name(field), new byte[]{(byte) kind}, unsignedLeb(index));
    }

    private static byte[] name(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return concat(unsignedLeb(bytes.length), bytes);
    }

    private static byte[] code(int i32Locals, byte[] body) {
        byte[] locals = i32Locals == 0
                ? new byte[]{0}
                : concat(new byte[]{1}, unsignedLeb(i32Locals), new byte[]{0x7f});
        byte[] function = concat(locals, body, new byte[]{END});
        return concat(unsignedLeb(function.length), function);
    }

    private static byte[] vector(byte[]... entries) {
        return concat(unsignedLeb(entries.length), concat(entries));
    }

    private static byte[] section(int id, byte[] body) {
        return concat(new byte[]{(byte) id}, unsignedLeb(body.length), body);
    }

    private static byte[] unsignedLeb(int value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int remaining = value;
        do {
            int b = remaining & 0x7f;
            remaining >>>= 7;
            out.write(remaining != 0 ? b | 0x80 : b);
        } while (remaining != 0);
        return out.toByteArray();
    }

    private static byte[] signedLeb(long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long remaining = value;
        boolean more = true;
        while (more) {
            int b = (int) (remaining & 0x7f);
            remaining >>= 7;
            more = !((remaining == 0 && (b & 0x40) == 0) || (remaining == -1 && (b & 0x40) != 0));
            out.write(more ? b | 0x80 : b);
        }
        return out.toByteArray();
    }

    private static Path write(Path dir, String name, byte[] module) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, module);
        return file;
    }
}
