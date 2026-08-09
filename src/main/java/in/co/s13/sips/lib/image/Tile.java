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
package in.co.s13.sips.lib.image;

import java.io.Serializable;

/**
 * One unit of two-dimensional work: a rectangle of an image assigned to a node.
 *
 * <p>A tile distinguishes two rectangles:
 * <ul>
 *   <li>the <b>write region</b> ({@link #x}, {@link #y}, {@link #width},
 *       {@link #height}) — the pixels this tile is responsible for producing.
 *       Write regions never overlap, so results reassemble cleanly.</li>
 *   <li>the <b>read region</b> ({@link #readX}, {@link #readY},
 *       {@link #readWidth}, {@link #readHeight}) — the write region grown by the
 *       halo and clamped to the image. Neighbourhood operations such as a 3x3
 *       convolution need these surrounding pixels to compute correct values at
 *       the tile's own borders.</li>
 * </ul>
 *
 * @param index  position in the grid's tile list, used to reassemble results
 * @param row    grid row
 * @param column grid column
 */
public record Tile(int index, int row, int column,
        int x, int y, int width, int height,
        int readX, int readY, int readWidth, int readHeight) implements Serializable {

    /** Number of pixels this tile is responsible for producing. */
    public long area() {
        return (long) width * height;
    }

    /** Offset of the write region inside the read region, in pixels. */
    public int haloLeft() {
        return x - readX;
    }

    public int haloTop() {
        return y - readY;
    }
}
