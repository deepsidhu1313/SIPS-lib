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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two-dimensional decomposition of an image into tiles.
 *
 * <p>SIPS could previously only split a single numeric range, which is enough
 * for a 1-D loop but not for image work: a neighbourhood operation such as a
 * convolution needs each tile to carry a halo of surrounding pixels, and the
 * results must reassemble into exactly the original raster with no gaps and no
 * double-counting.
 */
class TileGridTest {

    @Test
    void coversTheWholeImageExactlyOnce() {
        TileGrid grid = TileGrid.of(1000, 800, 4, 4, 0);
        List<Tile> tiles = grid.tiles();

        long covered = tiles.stream()
                .mapToLong(t -> (long) t.width() * t.height())
                .sum();
        assertEquals(1000L * 800L, covered, "tiles must tile the image exactly");
    }

    @Test
    void handlesDimensionsThatDoNotDivideEvenly() {
        // 1001 x 799 over a 4x4 grid: remainders must land somewhere, not vanish.
        TileGrid grid = TileGrid.of(1001, 799, 4, 4, 0);

        long covered = grid.tiles().stream()
                .mapToLong(t -> (long) t.width() * t.height())
                .sum();
        assertEquals(1001L * 799L, covered);
        assertEquals(16, grid.tiles().size());
    }

    @Test
    void tilesDoNotOverlapInTheirCoreRegions() {
        TileGrid grid = TileGrid.of(100, 100, 3, 3, 2);
        boolean[][] seen = new boolean[100][100];

        for (Tile tile : grid.tiles()) {
            for (int y = tile.y(); y < tile.y() + tile.height(); y++) {
                for (int x = tile.x(); x < tile.x() + tile.width(); x++) {
                    assertFalse(seen[y][x], "pixel " + x + "," + y + " covered twice");
                    seen[y][x] = true;
                }
            }
        }
        for (int y = 0; y < 100; y++) {
            for (int x = 0; x < 100; x++) {
                assertTrue(seen[y][x], "pixel " + x + "," + y + " never covered");
            }
        }
    }

    @Test
    void haloExpandsTheReadRegionButNotTheWriteRegion() {
        TileGrid grid = TileGrid.of(100, 100, 2, 2, 3);
        Tile bottomRight = grid.tiles().stream()
                .filter(t -> t.x() > 0 && t.y() > 0)
                .findFirst()
                .orElseThrow();

        // Write region is the tile itself.
        assertEquals(50, bottomRight.width());
        assertEquals(50, bottomRight.height());
        // Read region is grown by the halo on the interior sides.
        assertEquals(47, bottomRight.readX());
        assertEquals(47, bottomRight.readY());
        assertEquals(53, bottomRight.readWidth());
        assertEquals(53, bottomRight.readHeight());
    }

    @Test
    void haloIsClampedAtImageEdges() {
        TileGrid grid = TileGrid.of(100, 100, 2, 2, 5);
        Tile topLeft = grid.tiles().get(0);

        // Nothing exists above or left of the image, so the read region starts at 0.
        assertEquals(0, topLeft.readX());
        assertEquals(0, topLeft.readY());
        assertEquals(55, topLeft.readWidth());
        assertEquals(55, topLeft.readHeight());
    }

    @Test
    void zeroHaloMeansReadRegionEqualsWriteRegion() {
        for (Tile tile : TileGrid.of(64, 64, 4, 4, 0).tiles()) {
            assertEquals(tile.x(), tile.readX());
            assertEquals(tile.y(), tile.readY());
            assertEquals(tile.width(), tile.readWidth());
            assertEquals(tile.height(), tile.readHeight());
        }
    }

    @Test
    void tilesAreIndexedSoResultsCanBeReassembled() {
        TileGrid grid = TileGrid.of(80, 60, 4, 3, 0);
        List<Tile> tiles = grid.tiles();

        assertEquals(12, tiles.size());
        for (int i = 0; i < tiles.size(); i++) {
            assertEquals(i, tiles.get(i).index(), "index must match position in the list");
        }
    }

    @Test
    void splittingForAGivenChunkCountPicksABalancedGrid() {
        // 8 chunks over a landscape image should not degenerate to 8x1.
        TileGrid grid = TileGrid.forChunks(1920, 1080, 8, 1);
        assertEquals(8, grid.tiles().size());
        assertTrue(grid.columns() > 1 && grid.rows() > 1,
                "expected a 2-D split, got " + grid.columns() + "x" + grid.rows());
    }

    @Test
    void forChunksStillCoversTheImageExactly() {
        TileGrid grid = TileGrid.forChunks(1920, 1080, 7, 2);
        long covered = grid.tiles().stream()
                .mapToLong(t -> (long) t.width() * t.height())
                .sum();
        assertEquals(1920L * 1080L, covered);
    }

    @Test
    void aSingleChunkIsTheWholeImage() {
        TileGrid grid = TileGrid.forChunks(640, 480, 1, 4);
        assertEquals(1, grid.tiles().size());
        Tile only = grid.tiles().get(0);
        assertEquals(0, only.x());
        assertEquals(0, only.y());
        assertEquals(640, only.width());
        assertEquals(480, only.height());
    }

    @Test
    void moreChunksThanPixelsDoesNotProduceEmptyTiles() {
        TileGrid grid = TileGrid.of(2, 2, 4, 4, 0);
        for (Tile tile : grid.tiles()) {
            assertTrue(tile.width() > 0 && tile.height() > 0,
                    "empty tile would schedule work with nothing to do: " + tile);
        }
    }

    @Test
    void rejectsNonsenseDimensions() {
        assertThrows(IllegalArgumentException.class, () -> TileGrid.of(0, 100, 2, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> TileGrid.of(100, -1, 2, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> TileGrid.of(100, 100, 0, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> TileGrid.of(100, 100, 2, 2, -1));
        assertThrows(IllegalArgumentException.class, () -> TileGrid.forChunks(100, 100, 0, 0));
    }
}
