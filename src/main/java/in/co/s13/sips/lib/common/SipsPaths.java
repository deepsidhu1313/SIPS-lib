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
package in.co.s13.sips.lib.common;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds file paths that are correct on every platform SIPS runs on.
 *
 * <p>Paths used to be assembled by concatenating {@code "/"}, which on Windows
 * produces {@code C:\Users\x/.simulated/Foo}. Java's file APIs mostly tolerate
 * that, which is why it never failed outright, but the string is wrong anywhere
 * a path is compared, split, persisted or shown — and those are exactly the
 * places the bug surfaces, far from where it was introduced.
 *
 * <p>Deliberately more forgiving than {@link Path#resolve}: a segment that
 * begins with a separator is treated as relative, not as a new root. The
 * distributor sends names like {@code /src/Main.java} meaning "relative to the
 * chunk directory", and resolve would discard the base and write to the
 * filesystem root.
 */
public final class SipsPaths {

    private SipsPaths() {
    }

    /**
     * Joins segments with the platform separator.
     *
     * <p>The <em>first</em> segment sets the root: an absolute path stays
     * absolute, a Windows drive letter is kept, and a relative base stays
     * relative. Every <em>later</em> segment is treated as relative even if it
     * begins with a separator — the distributor sends names like
     * {@code /src/Main.java} meaning "inside the chunk directory", and treating
     * that as a new root would write to the filesystem root instead.
     *
     * <p>Later segments may use either separator, so a path recorded on one
     * platform can be read on another. Empty and null segments are skipped
     * rather than producing a doubled separator.
     *
     * @throws IllegalArgumentException if no usable segment is given
     */
    public static String join(String... segments) {
        Path joined = null;
        if (segments != null) {
            for (String segment : segments) {
                if (segment == null || segment.isBlank()) {
                    continue;
                }
                if (joined == null) {
                    // Handed to Path.of whole, so its root survives. Splitting
                    // it first would turn "/home/nika" into "home/nika" and
                    // quietly reinterpret an absolute path as relative to
                    // whatever directory the process happens to be in.
                    //
                    // Backslashes become forward slashes first: Windows Path
                    // accepts either, and it lets a path recorded on Windows be
                    // read here. A genuine backslash in a Unix filename is the
                    // price, and it is not one anybody pays in practice.
                    joined = Path.of(segment.replace('\\', '/'));
                    continue;
                }
                for (String piece : segment.split("[/\\\\]+")) {
                    if (!piece.isBlank()) {
                        joined = joined.resolve(piece);
                    }
                }
            }
        }
        if (joined == null) {
            throw new IllegalArgumentException("At least one path segment is required");
        }
        return joined.normalize().toString();
    }

    /** The same, as a {@link Path}. */
    public static Path of(String... segments) {
        return Path.of(join(segments));
    }

    /**
     * Converts a fully-qualified class name to a relative path.
     *
     * <p>Replaces {@code SIPS.java}'s {@code replaceAll("\\.", "/")}, which had
     * the same problem for packaged classes.
     */
    public static String classNameToPath(String className) {
        if (className == null || className.isBlank()) {
            return "";
        }
        String[] parts = className.trim().split("\\.");
        return parts.length == 1 ? parts[0] : join(parts);
    }
}
