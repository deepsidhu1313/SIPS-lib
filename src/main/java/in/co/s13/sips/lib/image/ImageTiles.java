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
import javax.imageio.ImageIO;

/**
 * Moves pixels between a whole image and the tiles a distributed job works on.
 *
 * <p>Tiles are addressed by a single index, so an image job rides the existing
 * one-dimensional {@code parallelFor}: chunk <i>i</i> of the loop is tile
 * <i>i</i> of the grid. No new loop syntax is required, and every existing
 * scheduler works unchanged.
 */
public final class ImageTiles {

    private ImageTiles() {
    }

    /**
     * The tile a given chunk index is responsible for.
     *
     * @throws IndexOutOfBoundsException if the index is not a tile in this grid
     */
    public static Tile tileFor(TileGrid grid, int index) {
        if (index < 0 || index >= grid.tiles().size()) {
            throw new IndexOutOfBoundsException(
                    "No tile " + index + " in a grid of " + grid.tiles().size());
        }
        return grid.tiles().get(index);
    }

    /**
     * Copies a tile's read region — its own pixels plus the halo — out of the
     * source image.
     *
     * <p>The returned image is the read region's size, so a filter treats it as
     * a standalone image. Coordinates inside it are offset from the source by
     * {@link Tile#readX()}/{@link Tile#readY()}.
     */
    public static BufferedImage readRegion(BufferedImage source, Tile tile) {
        BufferedImage region = new BufferedImage(tile.readWidth(), tile.readHeight(),
                BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < tile.readHeight(); y++) {
            for (int x = 0; x < tile.readWidth(); x++) {
                region.setRGB(x, y, source.getRGB(tile.readX() + x, tile.readY() + y));
            }
        }
        return region;
    }

    /**
     * Copies a processed tile's write region into the destination image.
     *
     * <p>Only the write region is copied. The halo belongs to neighbouring
     * tiles, and writing it would cover those pixels twice with values computed
     * from incomplete context.
     *
     * @param processed the tile result, sized as the tile's <em>read</em> region
     */
    public static void writeRegion(BufferedImage destination, Tile tile, BufferedImage processed) {
        // Skip the halo that sits above and to the left of the write region.
        int offsetX = tile.haloLeft();
        int offsetY = tile.haloTop();
        for (int y = 0; y < tile.height(); y++) {
            for (int x = 0; x < tile.width(); x++) {
                destination.setRGB(tile.x() + x, tile.y() + y,
                        processed.getRGB(offsetX + x, offsetY + y));
            }
        }
    }

    /**
     * Reassembles a whole image from per-tile results on disk.
     *
     * @param nameTemplate a {@link String#format} template taking the tile index,
     *                     for example {@code "tile-%d.png"}
     * @throws IOException if any tile is missing or unreadable. Failing loudly
     *                     matters: a silently half-merged image looks plausible.
     */
    public static BufferedImage merge(TileGrid grid, Path directory, String nameTemplate)
            throws IOException {
        BufferedImage merged = new BufferedImage(grid.imageWidth(), grid.imageHeight(),
                BufferedImage.TYPE_INT_RGB);
        for (Tile tile : grid.tiles()) {
            Path file = directory.resolve(String.format(nameTemplate, tile.index()));
            if (!Files.exists(file)) {
                throw new IOException("Missing result for tile " + tile.index() + ": " + file);
            }
            writeRegion(merged, tile, readPng(file));
        }
        return merged;
    }

    public static void writePng(BufferedImage image, Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        if (!ImageIO.write(image, "png", file.toFile())) {
            throw new IOException("No PNG writer available for " + file);
        }
    }

    public static BufferedImage readPng(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            throw new IOException("Not a readable image: " + file);
        }
        return image;
    }
}
