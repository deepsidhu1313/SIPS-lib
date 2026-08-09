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
     * <p>Segments may themselves contain either separator; both are understood,
     * so paths written on one platform can be read on another. Empty and null
     * segments are skipped rather than producing a doubled separator.
     *
     * @throws IllegalArgumentException if no usable segment is given
     */
    public static String join(String... segments) {
        List<String> parts = new ArrayList<>();
        if (segments != null) {
            for (String segment : segments) {
                if (segment == null || segment.isBlank()) {
                    continue;
                }
                for (String piece : segment.split("[/\\\\]+")) {
                    if (!piece.isBlank()) {
                        parts.add(piece);
                    }
                }
            }
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("At least one path segment is required");
        }
        String first = parts.get(0);
        String[] rest = parts.subList(1, parts.size()).toArray(new String[0]);
        return Path.of(first, rest).normalize().toString();
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
