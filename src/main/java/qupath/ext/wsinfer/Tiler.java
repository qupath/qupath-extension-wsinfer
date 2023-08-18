package qupath.ext.wsinfer;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Tiler {
    private Geometry parent;
    private int tileWidth;
    private int tileHeight;
    private boolean trimToParent = true;
    private boolean symmetric = true;
    private boolean filterByCentroid = true;

    public Tiler(Geometry parent, int tileWidth, int tileHeight) {
        this.parent = parent;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
    }

    public Geometry getParent() {
        return parent;
    }

    public void setParent(Geometry parent) {
        this.parent = parent;
    }

    public void setTileWidth(int tileWidth) {
        this.tileWidth = tileWidth;
    }

    public int getTileWidth() {
        return tileWidth;
    }

    public int getTileHeight() {
        return tileHeight;
    }

    public void setTileHeight(int tileHeight) {
        this.tileHeight = tileHeight;
    }

    public boolean isTrimToParent() {
        return trimToParent;
    }

    public void setTrimToParent(boolean trimToParent) {
        this.trimToParent = trimToParent;
    }

    public boolean isSymmetric() {
        return symmetric;
    }

    public void setSymmetric(boolean symmetric) {
        this.symmetric = symmetric;
    }

    public void setFilterByCentroid(boolean filterByCentroid) {
        this.filterByCentroid = filterByCentroid;
    }

    public boolean isFilterByCentroid() {
        return filterByCentroid;
    }

    public List<Geometry> tile() {
        return Tiler.tile(parent, tileWidth, tileHeight, trimToParent, filterByCentroid, symmetric);
    }

    public static List<Geometry> tile(final Geometry parent,
                                      final int tileWidth,
                                      final int tileHeight,
                                      final boolean trimToParent,
                                      final boolean trimByCentroids,
                                      final boolean symmetric) {
        Geometry boundingBox = parent.isRectangle() ? parent : parent.getEnvelope();
        Coordinate[] coordinates = boundingBox.getCoordinates(); // (minx miny, minx maxy, maxx maxy, maxx miny, minx miny).
        double xStart = coordinates[0].x;
        double yStart = coordinates[0].y;
        double xEnd = coordinates[2].x;
        double yEnd = coordinates[2].y;

        double bBoxWidth = xEnd - xStart;
        double bBoxHeight = yEnd - yStart;

        if (symmetric) {
            xStart += calculateOffset(tileWidth, bBoxWidth);
            yStart += calculateOffset(tileHeight, bBoxHeight);
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
                } else if (!trimByCentroids | parent.contains(tile.getCentroid())) {
                    // If we aren't trimming based on centroids,
                    // or it'd be included anyway
                    tiles.add(tile);
                }
            }
        }
        return tiles;
    }

    private static double calculateOffset(final int tileDim, final double parentDim) {
        double mod = parentDim % tileDim;
        if (mod == 0) {
            return 0;
        }
        return mod / 2;
    }

    public static boolean createObjectsFromGeometries(
            ImageData<BufferedImage> imageData,
            List<Geometry> geometries,
            PathObject parent,
            boolean clearChildren,
            boolean setLocked,
            Function<ROI, ? extends PathObject> creator) {
        if (geometries.isEmpty()) {
            return false;
        }

        if (clearChildren) {
            parent.clearChildObjects();
        }
        for (int i = 0; i < geometries.size(); i++) {
            var geometry = geometries.get(i);
            var roi = GeometryTools.geometryToROI(geometry, parent.getROI().getImagePlane());
            var po = creator.apply(roi);
            po.setName("Tile " + i);
            parent.addChildObject(po);
        }
        parent.setLocked(setLocked);
        imageData.getHierarchy().fireHierarchyChangedEvent(parent);
        return true;
    }

    public static boolean createTilesFromGeometries(
            ImageData<BufferedImage> imageData,
            List<Geometry> geometries,
            PathObject parent,
            boolean clearChildren,
            boolean setLocked) {
        return createObjectsFromGeometries(
                imageData,
                geometries,
                parent,
                clearChildren,
                setLocked,
                PathObjects::createTileObject);
    }

    public static boolean createAnnotationTilesFromGeometries(
            ImageData<BufferedImage> imageData,
            List<Geometry> geometries,
            PathObject parent,
            boolean clearChildren,
            boolean setLocked) {
        return createObjectsFromGeometries(
                imageData,
                geometries,
                parent,
                clearChildren,
                setLocked,
                PathObjects::createAnnotationObject);
    }
}
