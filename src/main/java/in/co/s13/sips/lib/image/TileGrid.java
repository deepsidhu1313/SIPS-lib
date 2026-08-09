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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Splits an image into tiles for distributed processing.
 *
 * <p>This is the two-dimensional counterpart to the framework's existing 1-D
 * range chunking. Where a parallel {@code for} splits an integer interval, a
 * tile grid splits a raster, and adds the halo that neighbourhood operations
 * need.
 *
 * <p>Rows and columns absorb any remainder one pixel at a time, so the tiles
 * always cover the image exactly: no gaps, no overlap, and no empty tiles.
 */
public final class TileGrid implements Serializable {

    private final int imageWidth;
    private final int imageHeight;
    private final int columns;
    private final int rows;
    private final int halo;
    private final List<Tile> tiles;

    private TileGrid(int imageWidth, int imageHeight, int columns, int rows, int halo) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.columns = columns;
        this.rows = rows;
        this.halo = halo;
        this.tiles = Collections.unmodifiableList(build());
    }

    /**
     * Splits an image into an explicit {@code columns x rows} grid.
     *
     * @param halo pixels of context each tile may read beyond its own edges;
     *             0 for pointwise operations, 1 for a 3x3 kernel, and so on
     * @throws IllegalArgumentException if any argument is out of range
     */
    public static TileGrid of(int imageWidth, int imageHeight, int columns, int rows, int halo) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    "Image dimensions must be positive, got " + imageWidth + "x" + imageHeight);
        }
        if (columns <= 0 || rows <= 0) {
            throw new IllegalArgumentException(
                    "Grid must be at least 1x1, got " + columns + "x" + rows);
        }
        if (halo < 0) {
            throw new IllegalArgumentException("Halo cannot be negative, got " + halo);
        }
        // More tiles than pixels would produce empty tiles, which schedule work
        // with nothing to do.
        return new TileGrid(imageWidth, imageHeight,
                Math.min(columns, imageWidth), Math.min(rows, imageHeight), halo);
    }

    /**
     * Chooses a balanced grid yielding exactly {@code chunks} tiles.
     *
     * <p>Prefers a squarish split over a strip split, because a squarish tile
     * has a shorter perimeter and therefore exchanges less halo data.
     */
    public static TileGrid forChunks(int imageWidth, int imageHeight, int chunks, int halo) {
        if (chunks <= 0) {
            throw new IllegalArgumentException("Chunk count must be positive, got " + chunks);
        }
        int bestColumns = chunks;
        int bestRows = 1;
        long bestCost = Long.MAX_VALUE;

        for (int candidateColumns = 1; candidateColumns <= chunks; candidateColumns++) {
            if (chunks % candidateColumns != 0) {
                continue;
            }
            int candidateRows = chunks / candidateColumns;
            // Total halo traffic is proportional to the summed tile perimeter.
            long tileWidth = Math.max(1, imageWidth / candidateColumns);
            long tileHeight = Math.max(1, imageHeight / candidateRows);
            long cost = tileWidth + tileHeight;
            if (cost < bestCost) {
                bestCost = cost;
                bestColumns = candidateColumns;
                bestRows = candidateRows;
            }
        }
        return of(imageWidth, imageHeight, bestColumns, bestRows, halo);
    }

    private List<Tile> build() {
        List<Tile> result = new ArrayList<>(columns * rows);
        int index = 0;
        int y = 0;
        for (int row = 0; row < rows; row++) {
            int tileHeight = extent(imageHeight, rows, row);
            int x = 0;
            for (int column = 0; column < columns; column++) {
                int tileWidth = extent(imageWidth, columns, column);

                int readX = Math.max(0, x - halo);
                int readY = Math.max(0, y - halo);
                int readRight = Math.min(imageWidth, x + tileWidth + halo);
                int readBottom = Math.min(imageHeight, y + tileHeight + halo);

                result.add(new Tile(index++, row, column,
                        x, y, tileWidth, tileHeight,
                        readX, readY, readRight - readX, readBottom - readY));
                x += tileWidth;
            }
            y += tileHeight;
        }
        return result;
    }

    /**
     * Size of slice {@code position} when {@code total} is divided into
     * {@code parts}. The first {@code total % parts} slices take one extra
     * pixel, which is what makes the tiling exact.
     */
    private static int extent(int total, int parts, int position) {
        return total / parts + (position < total % parts ? 1 : 0);
    }

    public List<Tile> tiles() {
        return tiles;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public int halo() {
        return halo;
    }

    public int imageWidth() {
        return imageWidth;
    }

    public int imageHeight() {
        return imageHeight;
    }

    @Override
    public String toString() {
        return "TileGrid[" + imageWidth + "x" + imageHeight + " into "
                + columns + "x" + rows + " halo=" + halo + "]";
    }
}
