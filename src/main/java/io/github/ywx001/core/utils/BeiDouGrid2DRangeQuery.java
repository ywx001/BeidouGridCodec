package io.github.ywx001.core.utils;

import io.github.ywx001.core.constants.BeiDouGridConstants;
import io.github.ywx001.core.decoder.BeiDouGridDecoder;
import io.github.ywx001.core.model.BeiDouGeoPoint;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * 北斗二维网格范围查询工具类
 * 根据几何图形（多边形或线）生成包含的北斗网格码集合
 */
@Slf4j
public class BeiDouGrid2DRangeQuery {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    /**
     * 主方法：根据几何图形查找相交的二维网格码
     *
     * @param geom        几何图形对象，支持多边形、线、点等JTS几何类型
     * @param targetLevel 目标网格级别，范围1-10
     * @return 与几何图形相交的所有指定级别网格码集合
     * @throws IllegalArgumentException 如果几何图形为空或目标级别不在 1-10 范围内
     */
    public static Set<String> find2DGridCodesInRange(Geometry geom, int targetLevel) {
        long startTime = System.currentTimeMillis();
        validateParameters(geom, targetLevel);

        Set<String> result = new HashSet<>();

        Envelope envelope = geom.getEnvelopeInternal();
        double minLng = envelope.getMinX();
        double maxLng = envelope.getMaxX();
        double minLat = envelope.getMinY();
        double maxLat = envelope.getMaxY();

        // 获取目标层级的网格尺寸
        BigDecimal[] gridSize = BeiDouGridConstants.GRID_SIZES_DEGREES[targetLevel];
        double lngSize = gridSize[0].doubleValue();
        double latSize = gridSize[1].doubleValue();

        // 生成候选网格
        for (double lng = minLng; lng <= maxLng + lngSize; lng += lngSize) {
            for (double lat = minLat; lat <= maxLat + latSize; lat += latSize) {

                // 生成网格编码
                String gridCode = BeiDouGridUtils.encode2D(
                        new BeiDouGeoPoint(lng, lat, 0), targetLevel
                );

                // 检查网格是否与几何图形相交
                if (isGridIntersectsMath(gridCode, geom, envelope)) {
                    result.add(gridCode);
                }
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        log.debug("总计算完成：找到 {} 个{}级网格，总耗时 {}ms", result.size(), targetLevel, totalTime);


        return result;
    }

    /**
     * 主方法：根据几何图形查找相交的二维网格码(已过时，请参考find2DGridCodesInRange)
     *
     * @param geom        几何图形对象，支持多边形、线、点等JTS几何类型
     * @param targetLevel 目标网格级别，范围1-10
     * @return 与几何图形相交的所有指定级别网格码集合
     * @throws IllegalArgumentException 如果几何图形为空或目标级别不在 1-10 范围内
     */
    @Deprecated
    public static Set<String> findGridCodesInRange(Geometry geom, int targetLevel) {
        long startTime = System.currentTimeMillis();
        validateParameters(geom, targetLevel);

        Set<String> result = new HashSet<>();

        // 1. 快速筛选一级网格
        Set<String> level1Grids = findIntersectingLevel1Grids(geom);

        log.info("一级网格筛选完成，找到 {} 个网格，耗时 {}ms",
                level1Grids.size(), System.currentTimeMillis() - startTime);


        // 2. 对每个相交的一级网格进行递归细化（使用并行流）
        level1Grids.parallelStream().forEach(level1Grid -> refineGrid(level1Grid, geom, targetLevel, 1, result));

        long totalTime = System.currentTimeMillis() - startTime;
        log.debug("总计算完成：找到 " + result.size() + " 个" + targetLevel + "级网格，总耗时 " + totalTime + "ms");

        return result;
    }

    /**
     * 一级网格快速筛选
     */
    private static Set<String> findIntersectingLevel1Grids(Geometry geom) {
        // 1. 计算几何图形的边界范围
        Envelope envelope = geom.getEnvelopeInternal();
        double minLng = envelope.getMinX();
        double maxLng = envelope.getMaxX();
        double minLat = envelope.getMinY();
        double maxLat = envelope.getMaxY();

        // 2. 计算一级网格索引范围（一级网格尺寸：6°×4°）
        int minLngIdx = (int) Math.floor(minLng / 6.0);
        int maxLngIdx = (int) Math.floor(maxLng / 6.0);
        int minLatIdx = (int) Math.floor(minLat / 4.0);
        int maxLatIdx = (int) Math.floor(maxLat / 4.0);

        // 3. 生成候选一级网格码
        Set<String> candidateGrids = new HashSet<>();
        for (int lngIdx = minLngIdx; lngIdx <= maxLngIdx; lngIdx++) {
            for (int latIdx = minLatIdx; latIdx <= maxLatIdx; latIdx++) {
                String gridCode = generateLevel1GridCode(lngIdx, latIdx);
                candidateGrids.add(gridCode);
            }
        }

        // 4. 精确筛选：判断几何图形是否与网格相交
        Set<String> intersectingGrids = new HashSet<>();
        for (String gridCode : candidateGrids) {
            Geometry gridPolygon = createGridPolygon(gridCode);
            if (geom.intersects(gridPolygon)) {
                intersectingGrids.add(gridCode);
            }
        }

        return intersectingGrids;
    }

    /**
     * 递归细化网格（使用数学计算优化）
     */
    private static void refineGrid(String parentGrid, Geometry geom, int targetLevel,
                                   int currentLevel, Set<String> result) {
        if (currentLevel == targetLevel) {
            result.add(parentGrid);
            return;
        }
        long startTime = System.currentTimeMillis();

        // 提前计算几何图形的边界框，避免重复计算
        Envelope geomEnvelope = geom.getEnvelopeInternal();

        // 生成当前层级网格的所有子网格
        Set<String> childGrids = generateChildGrids2D(parentGrid);

        long generateTime = System.currentTimeMillis() - startTime;

        int intersectCount = 0;

        for (String childGrid : childGrids) {
            // 使用纯数学方法判断相交
            if (isGridIntersectsMath(childGrid, geom, geomEnvelope)) {
                intersectCount++;
                refineGrid(childGrid, geom, targetLevel, currentLevel + 1, result);
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        if (currentLevel <= 3) { // 只记录低层级的详细日志
            log.debug("层级 {} 网格 {}: 生成 {} 子网格，{} 个相交，耗时 {}ms (生成: {}ms)",
                    currentLevel, parentGrid, childGrids.size(), intersectCount,
                    totalTime, generateTime);
        }
    }

    /**
     * 生成一级网格码
     */
    private static String generateLevel1GridCode(int lngIdx, int latIdx) {
        // 一级网格码格式：N + 经度索引(2位) + 纬度字母(A-V)
        String lngPart = String.format("%02d", lngIdx + 31); // 经度索引从31开始
        char latChar = (char) ('A' + latIdx); // 纬度字母从A开始

        return "N" + lngPart + latChar;
    }

    /**
     * 根据网格码创建对应的多边形几何
     *
     * @param gridCode 北斗网格码
     * @return 对应网格的多边形几何对象
     * @throws IllegalArgumentException 如果网格码格式无效
     */
    public static Geometry createGridPolygon(String gridCode) {
        // 1. 解码获取西南角坐标（注意：解码器返回的是网格的西南角点，不是中心点）
        BeiDouGeoPoint swCorner = BeiDouGridUtils.decode2D(gridCode);

        // 2. 获取网格级别
        int level = getGridLevel(gridCode);

        // 3. 计算网格尺寸（根据级别）
        BigDecimal[] gridSize = BeiDouGridConstants.GRID_SIZES_DEGREES[level];
        double lngSize = gridSize[0].doubleValue();
        double latSize = gridSize[1].doubleValue();

        // 4. 计算四个角点坐标（从西南角开始，顺时针方向）
        Coordinate sw = new Coordinate(swCorner.getLongitude(), swCorner.getLatitude());
        Coordinate se = new Coordinate(swCorner.getLongitude() + lngSize, swCorner.getLatitude());
        Coordinate ne = new Coordinate(swCorner.getLongitude() + lngSize, swCorner.getLatitude() + latSize);
        Coordinate nw = new Coordinate(swCorner.getLongitude(), swCorner.getLatitude() + latSize);

        // 5. 创建多边形（闭合环）
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(new Coordinate[]{sw, se, ne, nw, sw});
        log.debug("根据{}网格码创建对应的多边形几何{}", gridCode, new GeoJsonWriter().write(polygon));
        return polygon;
    }

    /**
     * 生成指定2维父网格的所有2维子网格集合
     *
     * @param parentGrid 父网格编码，自动识别其级别（格式示例：N50J475）
     *                   - 必须为有效的北斗二维网格码
     *                   - 编码级别需小于10（最高级网格无子网格）
     *                   - 1级网格的子网格为2级，依此类推
     * @return 子网格集合（可能为空集合）
     * - 每个子网格的级别为 parentGrid的级别 + 1
     * - 集合无序但保证唯一性
     * @throws IllegalArgumentException 如果参数不合法：
     *                                  - parentGrid格式无效
     *                                  - parentGrid层级超出1-9范围
     * @see BeiDouGridUtils#decode2D 网格解码实现
     * @see BeiDouGridConstants#GRID_DIVISIONS 各级网格划分规则
     * @see BeiDouGridConstants#GRID_SIZES_DEGREES 各级网格尺寸定义
     */
    public static Set<String> generateChildGrids2D(String parentGrid) {
        long startTime = System.currentTimeMillis();
        // 当前二维网格码层级
        int currentLevel = BeiDouGridDecoder.getCodeLevel2D(parentGrid);

        if (currentLevel < 1 || currentLevel >= 10) {
            throw new IllegalArgumentException("只能生成1-9级网格的子网格");
        }

        Set<String> childGrids = new HashSet<>();

        // 解码父网格获取西南角点（注意：解码器返回的是网格的西南角点，不是中心点）
        BeiDouGeoPoint parentSWCorner = BeiDouGridUtils.decode2D(parentGrid);

        // 获取子网格的划分数量
        int[] divisions = BeiDouGridConstants.GRID_DIVISIONS[currentLevel + 1];
        int lngDivisions = divisions[0];
        int latDivisions = divisions[1];

        // 获取子网格尺寸
        BigDecimal[] childSize = BeiDouGridConstants.GRID_SIZES_DEGREES[currentLevel + 1];
        double lngSize = childSize[0].doubleValue();
        double latSize = childSize[1].doubleValue();

        // 生成所有子网格的西南角点并计算中心点进行编码
        for (int i = 0; i < lngDivisions; i++) {
            for (int j = 0; j < latDivisions; j++) {
                // 计算子网格的西南角点
                double childSWLng = parentSWCorner.getLongitude() + i * lngSize;
                double childSWLat = parentSWCorner.getLatitude() + j * latSize;

                // 计算子网格的中心点（用于编码）
                double childLng = childSWLng + lngSize / 2;
                double childLat = childSWLat + latSize / 2;

                String childGrid = BeiDouGridUtils.encode2D(
                        new BeiDouGeoPoint(childLng, childLat, 0), currentLevel + 1);
                childGrids.add(childGrid);
            }
        }
        long time = System.currentTimeMillis() - startTime;
        if (time > 10) { // 只记录耗时较长的操作
            log.debug("生成层级 {} 网格 {} 的 {} 个子网格，耗时 {}ms",
                    currentLevel, parentGrid, childGrids.size(), time);
        }
        return childGrids;
    }

    /**
     * 获取指定层级的网格宽度（经度方向尺寸）
     */
    private static double getGridWidth(int level) {
        if (level < 1 || level > 10) {
            throw new IllegalArgumentException("层级必须在1-10之间");
        }
        return BeiDouGridConstants.GRID_SIZES_DEGREES[level][0].doubleValue();
    }

    /**
     * 获取指定层级的网格高度（纬度方向尺寸）
     */
    private static double getGridHeight(int level) {
        if (level < 1 || level > 10) {
            throw new IllegalArgumentException("层级必须在1-10之间");
        }
        return BeiDouGridConstants.GRID_SIZES_DEGREES[level][1].doubleValue();
    }

    /**
     * 根据网格码获取层级
     */
    private static int getGridLevel(String gridCode) {
        // 根据网格码长度判断层级
        int length = gridCode.length();
        for (int i = 1; i <= 10; i++) {
            if (length == BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i]) {
                return i;
            }
        }
        throw new IllegalArgumentException("无效的网格码格式: " + gridCode);
    }

    /**
     * 参数验证
     */
    private static void validateParameters(Geometry geom, int targetLevel) {
        if (geom == null) {
            throw new IllegalArgumentException("几何图形不能为空");
        }
        if (targetLevel < 1 || targetLevel > 10) {
            throw new IllegalArgumentException("目标层级必须在1-10之间");
        }
    }

    /**
     * 空间关系枚举
     */
    public enum SpatialRelation {
        /** 包含 */
        CONTAINS,
        /** 相交 */
        INTERSECTS,
        /** 被包含 */
        WITHIN,
        /** 不相交 */
        DISJOINT
    }

    /**
     * 纯数学方法判断网格与几何图形是否相交（避免JTS几何计算）
     *
     * @param gridCode     北斗网格编码
     * @param geom         待检测的几何图形（点/线/面）
     * @param geomEnvelope 几何图形的外包矩形（用于快速预判）
     * @return 是否相交
     */
    public static boolean isGridIntersectsMath(String gridCode, Geometry geom, Envelope geomEnvelope) {
        // 1. 网格解码并记录耗时
        long decodeStart = System.nanoTime();
        BeiDouGeoPoint swCorner = BeiDouGridUtils.decode2D(gridCode);
        long decodeTime = System.nanoTime() - decodeStart;
        if (decodeTime > 100000) { // 超过100μs的记录
            log.debug("网格解码 {} 耗时: {}μs", gridCode, decodeTime / 1000);
        }

        // 2. 获取网格级别和宽高
        int level = getGridLevel(gridCode);
        double gridWidth = getGridWidth(level);
        double gridHeight = getGridHeight(level);

        // 3. 计算网格的经纬度边界
        double rectMinX = swCorner.getLongitude();
        double rectMaxX = rectMinX + gridWidth;
        double rectMinY = swCorner.getLatitude();
        double rectMaxY = rectMinY + gridHeight;

        // 4. 快速边界框检查（快速排除不相交的情况）
        if (rectMaxX < geomEnvelope.getMinX() || rectMinX > geomEnvelope.getMaxX() ||
                rectMaxY < geomEnvelope.getMinY() || rectMinY > geomEnvelope.getMaxY()) {
            return false;
        }

        // 5. 根据几何类型分别判断
        switch (geom.getClass().getSimpleName()) {
            case "Point":
                return isPointInRectangleMath(geom.getCoordinate(), rectMinX, rectMaxX, rectMinY, rectMaxY);
            case "LineString":
                return isLineIntersectsRectangleMath((LineString) geom, rectMinX, rectMaxX, rectMinY, rectMaxY);
            case "Polygon":
                return isPolygonIntersectsRectangleMath((Polygon) geom, rectMinX, rectMaxX, rectMinY, rectMaxY);
            default:
                // 复杂几何回退到JTS原方法
                return geom.intersects(createGridPolygon(gridCode));
        }
    }

    /**
     * 判断网格编码与几何图形是否相交（数学计算）
     *
     * @param gridCode 网格编码
     * @param geom 几何图形（多边形、线、点等）
     * @return 如果相交返回 true，否则返回 false
     */
    public static boolean isGridIntersectsMath(String gridCode, Geometry geom) {
        return isGridIntersectsMath(gridCode, geom, geom.getEnvelopeInternal());
    }

    // 点与矩形相交判断
    private static boolean isPointInRectangleMath(Coordinate point,
                                                  double rectMinX, double rectMaxX, double rectMinY, double rectMaxY) {
        return point.x >= rectMinX && point.x <= rectMaxX &&
                point.y >= rectMinY && point.y <= rectMaxY;
    }

    // 线与矩形相交判断（使用Cohen-Sutherland算法）
    private static boolean isLineIntersectsRectangleMath(LineString line,
                                                         double rectMinX, double rectMaxX, double rectMinY, double rectMaxY) {
        Coordinate[] coords = line.getCoordinates();
        for (int i = 0; i < coords.length - 1; i++) {
            if (isLineSegmentIntersectsRectangleMath(coords[i], coords[i + 1],
                    rectMinX, rectMaxX, rectMinY, rectMaxY)) {
                return true;
            }
        }
        return false;
    }

    // 多边形与矩形相交判断（简化数学版）
    private static boolean isPolygonIntersectsRectangleMath(Polygon polygon,
                                                            double rectMinX, double rectMaxX, double rectMinY, double rectMaxY) {
        // 检查多边形顶点是否在矩形内
        for (Coordinate coord : polygon.getCoordinates()) {
            if (isPointInRectangleMath(coord, rectMinX, rectMaxX, rectMinY, rectMaxY)) {
                return true;
            }
        }

        // 检查矩形顶点是否在多边形内（使用射线法数学版）
        if (isPointInPolygonMath(rectMinX, rectMinY, polygon) ||
                isPointInPolygonMath(rectMaxX, rectMinY, polygon) ||
                isPointInPolygonMath(rectMaxX, rectMaxY, polygon) ||
                isPointInPolygonMath(rectMinX, rectMaxY, polygon)) {
            return true;
        }

        // 检查边相交
        return isPolygonEdgesIntersectRectangleMath(polygon, rectMinX, rectMaxX, rectMinY, rectMaxY);
    }

    /**
     * 判断线段是否与矩形相交（数学版）
     */
    private static boolean isLineSegmentIntersectsRectangleMath(Coordinate p1, Coordinate p2,
                                                                double rectMinX, double rectMaxX,
                                                                double rectMinY, double rectMaxY) {
        return isLineSegmentIntersectsRectangle(p1.x, p1.y, p2.x, p2.y, rectMinX, rectMaxX, rectMinY, rectMaxY);
    }

    /**
     * 判断点是否在多边形内（数学版，射线法）
     */
    private static boolean isPointInPolygonMath(double x, double y, Polygon polygon) {
        // 射线法实现
        Coordinate[] coords = polygon.getCoordinates();
        boolean inside = false;
        for (int i = 0, j = coords.length - 1; i < coords.length; j = i++) {
            if (((coords[i].y > y) != (coords[j].y > y)) &&
                    (x < (coords[j].x - coords[i].x) * (y - coords[i].y) / (coords[j].y - coords[i].y) + coords[i].x)) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * 判断多边形边与矩形边是否相交（数学版）
     */
    private static boolean isPolygonEdgesIntersectRectangleMath(Polygon polygon,
                                                                double rectMinX, double rectMaxX,
                                                                double rectMinY, double rectMaxY) {
        Coordinate[] coords = polygon.getCoordinates();
        for (int i = 0; i < coords.length - 1; i++) {
            if (isLineSegmentIntersectsRectangle(coords[i].x, coords[i].y,
                    coords[i + 1].x, coords[i + 1].y,
                    rectMinX, rectMaxX, rectMinY, rectMaxY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断线段是否与矩形相交
     */
    private static boolean isLineSegmentIntersectsRectangle(double x1, double y1, double x2, double y2,
                                                            double rectMinX, double rectMaxX,
                                                            double rectMinY, double rectMaxY) {
        // 使用Cohen-Sutherland线段裁剪算法
        int code1 = computeOutCode(x1, y1, rectMinX, rectMaxX, rectMinY, rectMaxY);
        int code2 = computeOutCode(x2, y2, rectMinX, rectMaxX, rectMinY, rectMaxY);

        // 如果两个端点都在矩形内，或者线段与矩形边界相交
        return (code1 & code2) == 0;
    }

    /**
     * 计算点的区域编码
     */
    private static int computeOutCode(double x, double y,
                                      double rectMinX, double rectMaxX,
                                      double rectMinY, double rectMaxY) {
        int code = 0;
        if (x < rectMinX) code |= 1;   // 左
        if (x > rectMaxX) code |= 2;   // 右
        if (y < rectMinY) code |= 4;   // 下
        if (y > rectMaxY) code |= 8;   // 上
        return code;
    }
}
