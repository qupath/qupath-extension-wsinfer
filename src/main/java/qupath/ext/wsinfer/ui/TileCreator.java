package qupath.ext.wsinfer.ui;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class TileCreator {

    private final double tileSizePx;
    private final double tileSizeMicrons;
    private final boolean trimToROI;
    private final boolean makeAnnotations;
    private final boolean removeParentAnnotation;
    private final PathObject parentObject;
    private final ImageData<?> imageData;
    private final boolean symmetric;
    private List<PathObject> tiles;
    private static final Logger logger = LoggerFactory.getLogger(TileCreator.class);

    public TileCreator(final ImageData<?> imageData, final PathObject parentObject,
                       double tileSizeMicrons, double tileSizePx, boolean trimToROI,
                       boolean makeAnnotations, boolean removeParentAnnotation,
                       boolean symmetric) {
        this.tileSizeMicrons = tileSizeMicrons;
        this.tileSizePx = tileSizePx;
        this.trimToROI = trimToROI;
        this.makeAnnotations = makeAnnotations;
        this.removeParentAnnotation = removeParentAnnotation;
        this.parentObject = parentObject;
        this.imageData = imageData;
        this.symmetric = symmetric;
    }

    public void run() {
        ROI roi = parentObject.getROI();
        ImageServer<?> server = imageData.getServer();
        ImmutableDimension tileSize = getPreferredTileSizePixels(tileSizeMicrons, tileSizePx, server);
        int tileWidth = tileSize.width;
        int tileHeight = tileSize.height;

        List<ROI> pathROIs = makeTiles(roi, tileWidth, tileHeight, trimToROI, symmetric);

        tiles = new ArrayList<>(pathROIs.size());
        Iterator<ROI> iter = pathROIs.iterator();
        int idx = 0;
        while (iter.hasNext()) {
            try {
                ROI nextROI = iter.next();
                PathObject tile = makeAnnotations ? PathObjects.createAnnotationObject(nextROI) : PathObjects.createTileObject(nextROI);
                idx++;
                tile.setName("Tile " + idx);
                tiles.add(tile);
            } catch (Exception e) {
                iter.remove();
            }
        }
        parentObject.clearChildObjects();
        parentObject.addChildObjects(tiles);
        if (parentObject.isAnnotation()) {
            parentObject.setLocked(true);
        }
        imageData.getHierarchy().fireHierarchyChangedEvent(this, parentObject);
        if (removeParentAnnotation && makeAnnotations && parentObject.isAnnotation()) {
            imageData.getHierarchy().removeObject(parentObject, true);
        }

    }

    public static List<ROI> makeTiles(final ROI roi,
                                      final int tileWidth, final int tileHeight,
                                      final boolean trimToROI,
                                      final boolean symmetric) {
        Geometry roiGeometry = roi.getGeometry();
        Geometry boundingBox = roiGeometry.isRectangle() ? roiGeometry : roiGeometry.getEnvelope();
        Coordinate[] coordinates = boundingBox.getCoordinates(); // (minx miny, minx maxy, maxx maxy, maxx miny, minx miny).
        double xStart = coordinates[0].x;
        double yStart = coordinates[0].y;
        double xEnd = coordinates[2].x;
        double yEnd = coordinates[2].y;

        double bBoxWidth = xEnd - xStart;
        double bBoxHeight = yEnd - yStart;

        if (symmetric) {
            xStart += nudge(tileWidth, bBoxWidth);
            yStart += nudge(tileHeight, bBoxHeight);
        }
        List<ROI> tiles = new ArrayList<>();
        for (int x = (int)xStart; x < xEnd; x += tileWidth) {
            for (int y = (int)yStart; y < yEnd; y += tileHeight) {
                ROI tile = ROIs.createRectangleROI(x, y, tileWidth, tileHeight, roi.getImagePlane());
                Geometry tileGeometry = tile.getGeometry();
                // straightforward cases
                // Check if we are actually within the object
                if (!roiGeometry.intersects(tileGeometry)) {
                    continue;
                }
                // tile is cleanly within roi
                if (roiGeometry.contains(tileGeometry)) {
                    tiles.add(tile);
                    continue;
                }

                if (trimToROI) {
                    // Shrink the tile if that is sensible
                    tileGeometry = tileGeometry.intersection(roiGeometry);
                    tile = GeometryTools.geometryToROI(tileGeometry, roi.getImagePlane());
                    tiles.add(tile);
                } else if (roiGeometry.contains(tileGeometry.getCentroid())) {
                    // If we aren't trimming, add if the centroid is contained
                    tiles.add(tile);
                }
            }
        }
        logger.info("Created {} tiles", tiles.size());
        return tiles;
    }

    private static double nudge(final int tileDim, final double parentDim) {
        double mod = parentDim % tileDim;
        if (mod == 0) {
            return 0;
        }
        return mod / 2;
    }

    public static <T> ImmutableDimension getPreferredTileSizePixels(final double tileSizeMicrons, final double tileSizePx, final ImageServer<T> server) {
        // Determine tile size
        int tileWidth, tileHeight;
        PixelCalibration cal = server.getPixelCalibration();
        if (cal.hasPixelSizeMicrons()) {
            tileWidth = (int)(tileSizeMicrons / cal.getPixelWidthMicrons() + .5);
            tileHeight = (int)(tileSizeMicrons / cal.getPixelHeightMicrons() + .5);
        } else {
            tileWidth = (int)(tileSizePx + .5);
            tileHeight = tileWidth;
        }
        return ImmutableDimension.getInstance(tileWidth, tileHeight);
    }

}
