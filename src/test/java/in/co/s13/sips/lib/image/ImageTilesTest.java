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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extracting a tile's pixels and putting processed results back.
 *
 * <p>The round trip that matters: cutting an image into tiles, processing each
 * independently as a distributed job would, and reassembling must reproduce the
 * original exactly when the operation is the identity. Anything less means a
 * seam or an offset error, which is very hard to spot by eye on a real filter.
 */
class ImageTilesTest {

    private static BufferedImage noise(int width, int height, long seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        return image;
    }

    private static void assertImagesEqual(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth(), "width");
        assertEquals(expected.getHeight(), actual.getHeight(), "height");
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y) & 0xFFFFFF, actual.getRGB(x, y) & 0xFFFFFF,
                        "pixel mismatch at " + x + "," + y);
            }
        }
    }

    @Test
    void identityRoundTripReproducesTheImageExactly() {
        BufferedImage source = noise(97, 61, 7);
        TileGrid grid = TileGrid.forChunks(97, 61, 6, 0);
        BufferedImage rebuilt = new BufferedImage(97, 61, BufferedImage.TYPE_INT_RGB);

        for (Tile tile : grid.tiles()) {
            BufferedImage region = ImageTiles.readRegion(source, tile);
            ImageTiles.writeRegion(rebuilt, tile, region);
        }
        assertImagesEqual(source, rebuilt);
    }

    @Test
    void identityRoundTripIsExactWithAHaloToo() {
        // With a halo the read region is larger than the write region, so the
        // write must be offset back by the halo. Getting that offset wrong
        // shifts every tile and is the classic tiling bug.
        BufferedImage source = noise(64, 48, 11);
        TileGrid grid = TileGrid.of(64, 48, 3, 3, 2);
        BufferedImage rebuilt = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);

        for (Tile tile : grid.tiles()) {
            BufferedImage region = ImageTiles.readRegion(source, tile);
            ImageTiles.writeRegion(rebuilt, tile, region);
        }
        assertImagesEqual(source, rebuilt);
    }

    @Test
    void readRegionHasTheHaloExpandedDimensions() {
        BufferedImage source = noise(100, 100, 3);
        TileGrid grid = TileGrid.of(100, 100, 2, 2, 5);
        Tile interior = grid.tiles().stream().filter(t -> t.x() > 0 && t.y() > 0).findFirst().orElseThrow();

        BufferedImage region = ImageTiles.readRegion(source, interior);

        assertEquals(interior.readWidth(), region.getWidth());
        assertEquals(interior.readHeight(), region.getHeight());
    }

    @Test
    void readRegionPixelsMatchTheSourceAtTheRightOffset() {
        BufferedImage source = noise(40, 40, 5);
        TileGrid grid = TileGrid.of(40, 40, 2, 2, 3);
        Tile tile = grid.tiles().get(3);

        BufferedImage region = ImageTiles.readRegion(source, tile);

        for (int y = 0; y < region.getHeight(); y++) {
            for (int x = 0; x < region.getWidth(); x++) {
                assertEquals(source.getRGB(tile.readX() + x, tile.readY() + y) & 0xFFFFFF,
                        region.getRGB(x, y) & 0xFFFFFF, "at " + x + "," + y);
            }
        }
    }

    @Test
    void writeRegionOnlyTouchesTheWriteRegion() {
        // A tile must never write into its halo: that area belongs to a
        // neighbour and writing it would double-cover those pixels.
        BufferedImage destination = new BufferedImage(60, 60, BufferedImage.TYPE_INT_RGB);
        TileGrid grid = TileGrid.of(60, 60, 2, 2, 4);
        Tile tile = grid.tiles().get(3);

        BufferedImage solid = new BufferedImage(tile.readWidth(), tile.readHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < solid.getHeight(); y++) {
            for (int x = 0; x < solid.getWidth(); x++) {
                solid.setRGB(x, y, 0xFFFFFF);
            }
        }
        ImageTiles.writeRegion(destination, tile, solid);

        int written = 0;
        for (int y = 0; y < 60; y++) {
            for (int x = 0; x < 60; x++) {
                if ((destination.getRGB(x, y) & 0xFFFFFF) != 0) {
                    written++;
                }
            }
        }
        assertEquals(tile.area(), written, "exactly the write region should be painted");
    }

    @Test
    void tileIndexAddressesTheGridAsALinearRange() {
        // This is what lets an image job ride the existing 1-D parallelFor:
        // chunk i of the loop is tile i of the grid.
        TileGrid grid = TileGrid.forChunks(320, 240, 6, 1);
        for (int i = 0; i < grid.tiles().size(); i++) {
            assertEquals(i, ImageTiles.tileFor(grid, i).index());
        }
    }

    @Test
    void tileForRejectsAnOutOfRangeIndex() {
        TileGrid grid = TileGrid.forChunks(64, 64, 4, 0);
        assertThrows(IndexOutOfBoundsException.class, () -> ImageTiles.tileFor(grid, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> ImageTiles.tileFor(grid, -1));
    }

    @Test
    void writesAndReadsTilesAsPngOnDisk(@TempDir Path dir) throws IOException {
        // How a chunk actually hands its result back: a PNG per tile.
        BufferedImage source = noise(50, 50, 13);
        TileGrid grid = TileGrid.forChunks(50, 50, 4, 0);

        for (Tile tile : grid.tiles()) {
            ImageTiles.writePng(ImageTiles.readRegion(source, tile),
                    dir.resolve("tile-" + tile.index() + ".png"));
        }

        BufferedImage rebuilt = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        for (Tile tile : grid.tiles()) {
            ImageTiles.writeRegion(rebuilt, tile,
                    ImageTiles.readPng(dir.resolve("tile-" + tile.index() + ".png")));
        }
        assertImagesEqual(source, rebuilt);
    }

    @Test
    void mergeReassemblesEveryTileFromADirectory(@TempDir Path dir) throws IOException {
        BufferedImage source = noise(70, 55, 17);
        TileGrid grid = TileGrid.forChunks(70, 55, 6, 0);
        for (Tile tile : grid.tiles()) {
            ImageTiles.writePng(ImageTiles.readRegion(source, tile),
                    dir.resolve("tile-" + tile.index() + ".png"));
        }

        BufferedImage merged = ImageTiles.merge(grid, dir, "tile-%d.png");

        assertImagesEqual(source, merged);
    }

    @Test
    void mergeFailsLoudlyWhenATileIsMissing(@TempDir Path dir) throws IOException {
        BufferedImage source = noise(40, 40, 19);
        TileGrid grid = TileGrid.forChunks(40, 40, 4, 0);
        for (Tile tile : grid.tiles()) {
            if (tile.index() != 2) {
                ImageTiles.writePng(ImageTiles.readRegion(source, tile),
                        dir.resolve("tile-" + tile.index() + ".png"));
            }
        }
        // A silently half-merged image looks plausible and is worse than an error.
        IOException thrown = assertThrows(IOException.class,
                () -> ImageTiles.merge(grid, dir, "tile-%d.png"));
        assertTrue(thrown.getMessage().contains("2"), "should name the missing tile");
    }

    @Test
    void pngRoundTripPreservesPixelsExactly(@TempDir Path dir) throws IOException {
        BufferedImage source = noise(33, 21, 23);
        Path file = dir.resolve("x.png");

        ImageTiles.writePng(source, file);

        assertTrue(Files.size(file) > 0);
        assertImagesEqual(source, ImageTiles.readPng(file));
    }
}
