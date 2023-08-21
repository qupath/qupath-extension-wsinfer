/**
 * Copyright 2023 University of Edinburgh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package qupath.ext.wsinfer;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathTileObject;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A class used to split {@link ROI} or {@link Geometry} objects into
 * rectangular tiles. Useful for parallel processing.
 */
public class Tiler {
    private int tileWidth;
    private int tileHeight;
    private boolean trimToParent = true;
    private boolean symmetric = true;
    private boolean filterByCentroid = true;

    /**
     * Create a Tiler object.
     * @param tileWidth the width in pixels.
     * @param tileHeight the height in pixels.
     */
    public Tiler(int tileWidth, int tileHeight) {
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
    }

    /**
     *
     * @param tileWidth tile width in pixels.
     * @param tileHeight tile height in pixels.
     * @param trimToParent controls whether tiles should be trimmed to fit
     *                     within the parent object.
     * @param symmetric controls whether the Tiler should aim to split the
     *                  parent object symmetrically. If false, it will
     *                  begin at the top left of the parent.
     * @param filterByCentroid controls whether tiles whose centroid is outwith
     *                         the parent object will be removed from the
     *                         output.
     */
    public Tiler(int tileWidth, int tileHeight,
                 boolean trimToParent, boolean symmetric,
                 boolean filterByCentroid) {
        this(tileWidth, tileHeight);
        this.trimToParent = trimToParent;
        this.symmetric = symmetric;
        this.filterByCentroid = filterByCentroid;
    }

    /**
     * Get the width of output tiles
     * @return the width in pixels
     */
    public int getTileWidth() {
        return tileWidth;
    }

    /**
     * Change the width of output tiles
     * @param tileWidth the new width in pixels
     */
    public void setTileWidth(int tileWidth) {
        this.tileWidth = tileWidth;
    }

    /**
     * Change the height of output tiles
     * @return the height in pixels
     */
    public int getTileHeight() {
        return tileHeight;
    }

    /**
     * Change the height of output tiles
     * @param tileHeight the new height in pixels
     */
    public void setTileHeight(int tileHeight) {
        this.tileHeight = tileHeight;
    }

    /**
     * Check if the tiler is set to trim output to the input parent.
     * @return whether the tiler is set to trim output to the parent object
     */
    public boolean isTrimToParent() {
        return trimToParent;
    }

    /**
     * Set whether the tiler is set to trim output to the input parent.
     * @param trimToParent the new setting
     */
    public void setTrimToParent(boolean trimToParent) {
        this.trimToParent = trimToParent;
    }

    /**
     * Check if the tiler will try to tile symmetrically, or will start
     * directly from the top-left of the parent.
     * @return The current setting
     */
    public boolean isSymmetric() {
        return symmetric;
    }

    /**
     * Set if the tiler will try to tile symmetrically, or will start
     * directly from the top-left of the parent.
     * @param symmetric The new setting
     */
    public void setSymmetric(boolean symmetric) {
        this.symmetric = symmetric;
    }

    /**
     * Check if the tiler will filter the output based on whether the centroid
     * of tiles lies within the parent
     * @return The current setting
     */
    public boolean isFilterByCentroid() {
        return filterByCentroid;
    }

    /**
     * Set if the tiler will filter the output based on whether the centroid
     * of tiles lies within the parent
     * @param filterByCentroid the new setting
     */
    public void setFilterByCentroid(boolean filterByCentroid) {
        this.filterByCentroid = filterByCentroid;
    }

    /**
     * Create a list of {@link Geometry} tiles from the input. These may
     * not all be rectangular based on the settings used.
     * @param parent the object that will be split into tiles.
     * @return a list of tiles
     */
    public List<Geometry> createGeometries(Geometry parent) {
        if (parent == null) {
            return new ArrayList<>();
        }
        Envelope boundingBox = parent.getEnvelopeInternal();
        double xStart = boundingBox.getMinX();
        double yStart = boundingBox.getMinY();
        double xEnd = boundingBox.getMaxX();
        double yEnd = boundingBox.getMaxY();

        double bBoxWidth = xEnd - xStart;
        double bBoxHeight = yEnd - yStart;

        if (symmetric) {
            if (filterByCentroid) {
                // Shift 'inside' the parent
                xStart += calculateInteriorOffset(tileWidth, bBoxWidth);
                yStart += calculateInteriorOffset(tileHeight, bBoxHeight);
            } else {
                // Shift 'outside' the parent
                xStart += calculateExteriorOffset(tileWidth, bBoxWidth);
                yStart += calculateExteriorOffset(tileHeight, bBoxHeight);
            }
        }
        List<Geometry> tiles = new ArrayList<>();
        for (int x = (int) xStart; x < xEnd; x += tileWidth) {
            for (int y = (int) yStart; y < yEnd; y += tileHeight) {
                Geometry tile = GeometryTools.createRectangle(x, y, tileWidth, tileHeight);
                // straightforward case 1:
                // if there's no intersection, we're in the bounding box but not
                // the parent
                if (!parent.intersects(tile)) {
                    continue;
                }
                // straightforward case 2:
                // tile is cleanly within roi
                if (parent.contains(tile)) {
                    tiles.add(tile);
                    continue;
                }

                // trimming:
                if (trimToParent) {
                    // trim the tile to fit the parent
                    tile = tile.intersection(parent);
                    tiles.add(tile);
                } else if (!filterByCentroid || parent.contains(tile.getCentroid())) {
                    // If we aren't trimming based on centroids,
                    // or it'd be included anyway
                    tiles.add(tile);
                }
            }
        }
        return tiles;
    }

    /**
     * Create a list of {@link ROI} tiles from the input. These may
     * not all be rectangular based on the settings used.
     * @param parent the object that will be split into tiles.
     * @return a list of tiles
     */
    public List<ROI> createROIs(ROI parent) {
        return createGeometries(parent.getGeometry()).stream()
                .map(g -> GeometryTools.geometryToROI(g, parent.getImagePlane()))
                .collect(Collectors.toList());
    }

    /**
     * Create a list of {@link PathObject} tiles from the input. These may
     * not all be rectangular based on the settings used.
     * @param parent the object that will be split into tiles.
     * @param creator a function used to create the desired type
     *                of {@link PathObject}
     * @return a list of tiles
     */
    public List<PathObject> createObjects(ROI parent, Function<ROI, PathObject> creator) {
        return createROIs(parent).stream().map(creator).collect(Collectors.toList());
    }

    /**
     * Create a list of {@link PathTileObject} tiles from the input. These may
     * not all be rectangular based on the settings used.
     * @param parent the object that will be split into tiles.
     * @return a list of tiles
     */
    public List<PathObject> createTiles(ROI parent) {
        return createObjects(parent, PathObjects::createTileObject);
    }

    /**
     * Create a list of {@link PathAnnotationObject} tiles from the input. These may
     * not all be rectangular based on the settings used.
     * @param parent the object that will be split into tiles.
     * @return a list of tiles
     */
    public List<PathObject> createAnnotations(ROI parent) {
        return createObjects(parent, PathObjects::createAnnotationObject);
    }

    /**
     * Calculate offset for symmetric tiling where the tiles cannot extend beyond the parent bounds
     * @param tileDim
     * @param parentDim
     * @return
     */
    private static double calculateInteriorOffset(final int tileDim, final double parentDim) {
        double mod = parentDim % tileDim;
        if (mod == 0) {
            return 0;
        }
        return mod / 2;
    }

    /**
     * Calculate offset for symmetric tiling where the tiles can extend beyond the parent bounds
     * @param tileDim
     * @param parentDim
     * @return
     */
    private static double calculateExteriorOffset(final int tileDim, final double parentDim) {
        double mod = parentDim % tileDim;
        if (mod == 0) {
            return 0;
        }
        return -(tileDim - mod) / 2;
    }

}