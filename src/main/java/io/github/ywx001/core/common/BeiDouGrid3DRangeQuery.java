package io.github.ywx001.core.common;

import io.github.ywx001.core.constants.BeiDouGridConstants;
import io.github.ywx001.core.decoder.BeiDouGridDecoder;
import io.github.ywx001.core.encoder.BeiDouGridEncoder;
import io.github.ywx001.core.model.BeiDouGeoPoint;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 北斗三维网格范围查询工具类
 * 根据三维几何图形生成包含的北斗三维网格码集合
 */
@Slf4j
public class BeiDouGrid3DRangeQuery {

    /**
     * 主方法，根据几何图形查找相交的三维网格码（显式指定高度范围）
     * <p>
     * 实现逻辑分为两个阶段：
     * 1. 使用二维查询获取基础网格
     * 2. 为每个基础网格生成三维编码并筛选
     *
     * @param geom        几何图形对象，支持多边形、线、点等JTS几何类型
     * @param targetLevel 目标网格级别，范围1-10
     * @param minAltitude 最小海拔高度（单位：米）
     * @param maxAltitude 最大海拔高度（单位：米）
     * @return 与几何图形相交的所有指定级别三维网格码集合
     * @throws IllegalArgumentException 如果几何图形为空、目标级别不在 1-10 范围内或高度范围无效
     */
    public static Set<String> find3DGridCodesInRange(Geometry geom, int targetLevel,
                                                     double minAltitude, double maxAltitude) {
        long startTime = System.currentTimeMillis();
        validateParameters(geom, targetLevel, minAltitude, maxAltitude);

        Set<String> result = new HashSet<>();

        // 第一阶段：使用二维查询获取基础网格
        Set<String> baseGrids = BeiDouGrid2DRangeQuery.find2DGridCodesInRange(geom, targetLevel);
        long totalTime = System.currentTimeMillis() - startTime;
        log.debug("二维基础网格筛选完成，找到 {} 个网格，总耗时 {}ms", baseGrids.size(), totalTime);

        // 第二阶段：为每个基础网格生成三维编码并筛选
        for (String grid2D : baseGrids) {
            generate3DGridsFor2DBase(grid2D, geom, minAltitude, maxAltitude, result);
        }
        totalTime = System.currentTimeMillis() - startTime;
        log.debug("三维查询完成：找到 {} 个{}级三维网格，总耗时 {}ms", result.size(), targetLevel, totalTime);

        return result;
    }


    /**
     * 直接生成与几何图形相交的三维网格编码集合
     *
     * @param geom 几何图形
     * @param targetLevel 目标网格级别
     * @param minAltitude 最小高度
     * @param maxAltitude 最大高度
     * @return 相交的三维网格编码集合
     */
    public static Set<String> generate3DGridCodesDirectly(Geometry geom, int targetLevel,
                                                          double minAltitude, double maxAltitude) {
        long startTime = System.currentTimeMillis();
        validateParameters(geom, targetLevel, minAltitude, maxAltitude);

        Set<String> result = new HashSet<>();

        // 1. 快速筛选一级三维网格（包含高度范围）
        Set<String> level1Grids = findIntersectingLevel1Grids3D(geom, minAltitude, maxAltitude);

        // 2. 对每个相交的一级网格进行递归细化
        level1Grids.parallelStream().forEach(level1Grid ->
                refineGrid3D(level1Grid, geom, targetLevel, 1, minAltitude, maxAltitude, result)
        );

        long totalTime = System.currentTimeMillis() - startTime;
        log.debug("直接三维网格生成完成：找到 {} 个{}级网格，耗时 {}ms", result.size(), targetLevel, totalTime);

        return result;
    }

    /**
     * 快速筛选一级三维网格（包含高度范围）
     */
    private static Set<String> findIntersectingLevel1Grids3D(Geometry geom,
                                                             double minAltitude, double maxAltitude) {
        // 1. 计算几何图形的边界范围（包括高度）
        Envelope envelope = geom.getEnvelopeInternal();
        double minLng = envelope.getMinX();
        double maxLng = envelope.getMaxX();
        double minLat = envelope.getMinY();
        double maxLat = envelope.getMaxY();

        // 2. 计算一级网格索引范围（一级网格尺寸：6°×4°×高度）
        int minLngIdx = (int) Math.floor(minLng / 6.0);
        int maxLngIdx = (int) Math.floor(maxLng / 6.0);
        int minLatIdx = (int) Math.floor(minLat / 4.0);
        int maxLatIdx = (int) Math.floor(maxLat / 4.0);
        int minAltIdx = (int) Math.floor(minAltitude / getGridHeight3D(1));
        int maxAltIdx = (int) Math.floor(maxAltitude / getGridHeight3D(1));

        // 3. 生成候选一级网格码
        Set<String> candidateGrids = new HashSet<>();
        for (int altIdx = minAltIdx; altIdx <= maxAltIdx; altIdx++) {
            for (int lngIdx = minLngIdx; lngIdx <= maxLngIdx; lngIdx++) {
                for (int latIdx = minLatIdx; latIdx <= maxLatIdx; latIdx++) {
                    // 生成一级网格的三维编码
                    String grid3D = generateLevel1GridCode3D(lngIdx, latIdx, altIdx);
                    candidateGrids.add(grid3D);
                }
            }
        }

        // 4. 精确筛选：判断几何图形是否与网格相交（包括高度范围）
        Set<String> intersectingGrids = new HashSet<>();
        for (String grid3D : candidateGrids) {
            if (is3DGridValidDirectly(grid3D, geom, minAltitude, maxAltitude)) {
                intersectingGrids.add(grid3D);
            }
        }

        return intersectingGrids;
    }

    /**
     * 生成一级三维网格码
     */
    private static String generateLevel1GridCode3D(int lngIdx, int latIdx, int altIdx) {
        // 生成二维部分
        String grid2D = BeiDouGridEncoder.encode2D(
                new BeiDouGeoPoint(lngIdx * 6.0 + 3.0, latIdx * 4.0 + 2.0, 0), 1);

        // 生成高度部分
        String altitudeCode = BeiDouGridEncoder.encode3DAltitude(altIdx * getGridHeight3D(1) + getGridHeight3D(1) / 2, 1);

        // 组合成完整的三维编码
        return combine2DAndAltitude(grid2D, altitudeCode, 1);
    }

    /**
     * 递归细化三维网格
     */
    private static void refineGrid3D(String parentGrid, Geometry geom, int targetLevel,
                                     int currentLevel, double minAltitude, double maxAltitude,
                                     Set<String> result) {
        if (currentLevel == targetLevel) {
            result.add(parentGrid);
            return;
        }

        // 生成当前层级网格的所有子网格
        Set<String> childGrids = generateChildGrids3D(parentGrid);

        // 普通for循环处理子网格（便于调试）
        log.debug("生成子网格数量: " + childGrids.size());
        int validCount = 0;
        for (String childGrid : childGrids) {
            if (is3DGridValidDirectly(childGrid, geom, minAltitude, maxAltitude)) {
                refineGrid3D(childGrid, geom, targetLevel, currentLevel + 1,
                        minAltitude, maxAltitude, result);
                validCount++;
            }
        }
        log.debug("有效子网格数量: " + validCount);
    }

    /**
     * 生成指定父三维网格的所有子三维网格集合
     *
     *
     * @param parentGrid  父三维网格编码（格式示例：N050J0047050）
     *                    - 必须为有效的北斗三维网格码
     *                    - 编码级别需小于10（最高级网格无子网格）
     * @return 子三维网格集合（可能为空集合）
     *         - 每个子网格的级别为 parentGrid的级别 + 1
     *         - 集合无序但保证唯一性
     *
     * @throws IllegalArgumentException 如果参数不合法：
     *         - parentGrid格式无效
     *         - parentGrid是10级网格（无子网格）
     *
     * @see BeiDouGridDecoder#decode3D 三维网格解码实现
     * @see BeiDouGridConstants#GRID_DIVISIONS 各级网格划分规则
     * @see BeiDouGridConstants#GRID_SIZES_DEGREES 各级网格尺寸定义
     */
    public static Set<String> generateChildGrids3D(String parentGrid) {
        Set<String> childGrids = new HashSet<>();

        // 当前三维网格码层级
        int currentLevel = BeiDouGridDecoder.getCodeLevel3D(parentGrid);
        if (currentLevel < 1 || currentLevel >= 10) {
            throw new IllegalArgumentException("只能生成1-9级网格的子网格");
        }

        // 解码父网格获取西南角点（包括高度）
        Map<String, Object> decoded = BeiDouGridDecoder.decode3D(parentGrid);
        BeiDouGeoPoint parentSWCorner = (BeiDouGeoPoint) decoded.get("geoPoint");
        double parentSWCornerLatitude = parentSWCorner.getLatitude();
        double parentSWCornerLongitude = parentSWCorner.getLongitude();
        double parentMinAltitude = parentSWCorner.getAltitude();
        double parentMaxAltitude = parentSWCorner.getAltitude() + getGridHeight3D(currentLevel);


        // 获取子网格的划分数量
        int[] divisions = BeiDouGridConstants.GRID_DIVISIONS[currentLevel + 1];
        int lngDivisions = divisions[0];
        int latDivisions = divisions[1];

        // 获取子网格尺寸
        BigDecimal[] childSize = BeiDouGridConstants.GRID_SIZES_DEGREES[currentLevel + 1];
        double lngSize = childSize[0].doubleValue();
        double latSize = childSize[1].doubleValue();

        // 高度方向的网格尺寸
        double altSize = getGridHeight3D(currentLevel + 1);

        // 计算高度方向的网格数量
        int minAltIdx = (int) Math.floor(parentMinAltitude / altSize);
        int maxAltIdx = (int) Math.ceil(parentMaxAltitude / altSize);
        log.debug("高度方向网格数量: minAltIdx={}, maxAltIdx={}, 总数量={}", minAltIdx, maxAltIdx, maxAltIdx - minAltIdx + 1);

        // 生成所有子网格的中心点并编码（高度方向生成多个网格）
        for (int i = 0; i < lngDivisions; i++) {
            for (int j = 0; j < latDivisions; j++) {
                // 计算子网格的西南角点（经纬度）
                double childSWLng = parentSWCornerLongitude + i * lngSize;
                double childSWLat = parentSWCornerLatitude + j * latSize;

                // 计算子网格中心点（经纬度）
                double childLng = childSWLng + lngSize / 2;
                double childLat = childSWLat + latSize / 2;

                // 生成高度方向的网格
                for (int k = minAltIdx; k <= maxAltIdx; k++) {
                    double childAlt = k * altSize + altSize / 2;
                    String childGrid = BeiDouGridEncoder.encode3D(
                            new BeiDouGeoPoint(childLng, childLat, childAlt), currentLevel + 1
                    );
                    childGrids.add(childGrid);
                }
            }
        }
        log.debug("生成层级 {} 网格 {} 的 {} 个子网格",
                currentLevel, parentGrid, childGrids.size());
        return childGrids;
    }


    /**
     * 直接验证三维网格有效性（优化版）
     */
    private static boolean is3DGridValidDirectly(String grid3D, Geometry geom,
                                                 double gridMinAlt, double gridMaxAlt) {
        try {
            // 解码获取网格边界
            Map<String, Object> decoded = BeiDouGridDecoder.decode3D(grid3D);
            BeiDouGeoPoint swPoint = (BeiDouGeoPoint) decoded.get("geoPoint");

            // 获取网格的尺寸（经度、纬度、高度）
            double gridHeight = getGridHeight3D(getGridLevel3D(grid3D));

            // 计算网格的东北角点（右上角点）
            double neAlt = swPoint.getAltitude() + gridHeight;

            // 高度范围验证
            if (gridMaxAlt < swPoint.getAltitude() || gridMinAlt > neAlt) {
                return false;
            }

            // 二维空间验证（使用BeiDouGridRangeQuery）
            String grid2D = extract2DCode(grid3D, getGridLevel3D(grid3D));
            Set<String> intersectingGrids = BeiDouGrid2DRangeQuery.findGridCodesInRange(geom, getGridLevel(grid2D));
            return intersectingGrids.contains(grid2D);

        } catch (Exception e) {
            log.debug("三维网格验证失败: " + grid3D + " " + e.getMessage());
            return false;
        }
    }


    private static void validateParameters(Geometry geom, int targetLevel, double minAltitude, double maxAltitude) {
        if (geom == null) {
            throw new IllegalArgumentException("几何图形不能为空");
        }
        if (targetLevel < 1 || targetLevel > 10) {
            throw new IllegalArgumentException("目标层级必须在1到10之间");
        }
        if (Double.isNaN(minAltitude) || Double.isNaN(maxAltitude) ||
                Double.isInfinite(minAltitude) || Double.isInfinite(maxAltitude)) {
            throw new IllegalArgumentException("高度范围必须是有效的数值");
        }
        if (minAltitude > maxAltitude) {
            throw new IllegalArgumentException("最小高度不能大于最大高度");
        }
    }

    /**
     * 主方法：根据几何图形查找相交的三维网格码（自动计算高度范围）
     */
    public static Set<String> find3DGridCodesInRange(Geometry geom, int targetLevel) {
        // 从几何图形中提取高度范围
        double[] altitudeRange = extractAltitudeRangeFromGeometry(geom);
        return find3DGridCodesInRange(geom, targetLevel, altitudeRange[0], altitudeRange[1]);
    }

    /**
     * 从几何图形中提取高度范围
     */
    private static double[] extractAltitudeRangeFromGeometry(Geometry geom) {
        if (geom == null) {
            throw new IllegalArgumentException("几何图形不能为空");
        }

        double minAltitude = Double.MAX_VALUE;
        double maxAltitude = Double.MIN_VALUE;

        // 遍历几何图形的所有点，提取高度信息
        for (Coordinate coord : geom.getCoordinates()) {
            double altitude = Double.isNaN(coord.z) ? 0.0 : coord.z;
            minAltitude = Math.min(minAltitude, altitude);
            maxAltitude = Math.max(maxAltitude, altitude);
        }

        return new double[]{minAltitude, maxAltitude};
    }

    /**
     * 为二维基础网格生成三维编码并筛选（带高度参数，以高度字段为范围）
     */
    private static void generate3DGridsFor2DBase(String grid2D, Geometry geom,
                                                 double minAltitude, double maxAltitude,
                                                 Set<String> result) {
        int level = getGridLevel(grid2D);

        // 计算网格的高度尺寸
        double gridHeight = getGridHeight3D(level);

        // 计算起始和结束的高度索引
        int startIndex = (int) Math.floor(minAltitude / gridHeight);
        int endIndex = (int) Math.ceil(maxAltitude / gridHeight);

        // 为每个高度索引生成编码
        for (int i = startIndex; i <= endIndex; i++) {
            double gridMinAlt = i * gridHeight;
            double gridMaxAlt = gridMinAlt + gridHeight;

            // 生成高度编码
            String altitudeCode = BeiDouGridEncoder.encode3DAltitude(gridMinAlt + gridHeight / 2, level);

            // 组合成完整的三维编码
            String grid3D = combine2DAndAltitude(grid2D, altitudeCode, level);

            // 检查是否与几何图形相交
//            if (is3DGridValid(grid3D, geom, gridMinAlt, gridMaxAlt)) {
//                result.add(grid3D);
//            }
            result.add(grid3D);
        }
    }

    /**
     * 从几何图形中提取高度点集合
     *
     * @param geom 几何图形
     * @return 高度点集合
     */
    public static Set<Double> extractAltitudePointsFromGeometry(Geometry geom) {
        if (geom == null) {
            throw new IllegalArgumentException("几何图形不能为空");
        }

        Set<Double> altitudePoints = new HashSet<>();

        // 遍历几何图形的所有点，提取高度信息
        for (Coordinate coord : geom.getCoordinates()) {
            // 假设 Coordinate 的 z 值代表高度
            double altitude = Double.isNaN(coord.z) ? 0.0 : coord.z;
            altitudePoints.add(altitude);
        }

        return altitudePoints;
    }

    /**
     * 组合二维编码和高度编码
     */
    private static String combine2DAndAltitude(String grid2D, String altitudeCode, int level) {
        StringBuilder result = new StringBuilder();

        // 添加纬度方向
        result.append(grid2D.charAt(0));

        // 添加高度方向
        result.append(altitudeCode.charAt(0));

        int grid2DIndex = 1;
        int altitudeCodeIndex = 1;

        // 逐级组合编码
        for (int i = 1; i <= level; i++) {
            // 添加二维编码片段
            int level2DLength = BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i] -
                    BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i - 1];
            result.append(grid2D, grid2DIndex, grid2DIndex + level2DLength);
            grid2DIndex += level2DLength;

            // 添加高度编码片段
            if (i == 1) {
                result.append(altitudeCode, altitudeCodeIndex, altitudeCodeIndex + 2);
                altitudeCodeIndex += 2;
            } else {
                result.append(altitudeCode, altitudeCodeIndex, altitudeCodeIndex + 1);
                altitudeCodeIndex += 1;
            }
        }

        return result.toString();
    }

    /**
     * 判断三维网格是否有效（2.5D方案：二维空间关系 + 高度范围判断），待优化
     */
    private static boolean is3DGridValid(String grid3D, Geometry geom, double queryMinAlt, double queryMaxAlt) {
        try {
            // 解码获取网格的三维边界信息
            Map<String, Object> decoded = BeiDouGridDecoder.decode3D(grid3D);
            BeiDouGeoPoint swPoint = (BeiDouGeoPoint) decoded.get("geoPoint");
            int level = getGridLevel3D(grid3D);

            // 获取网格的尺寸
            double gridAltitudeSize = getGridHeight3D(level);

            // 计算网格的高度范围
            double gridMinAlt = swPoint.getAltitude();
            double gridMaxAlt = gridMinAlt + gridAltitudeSize;

            // 1. 高度范围相交判断（精确匹配）
            boolean heightIntersects = (gridMaxAlt > queryMinAlt) && (gridMinAlt < queryMaxAlt);
            if (!heightIntersects) {
                return false;
            }

            // 2. 二维空间关系判断（复用现有优化逻辑）
            String grid2D = extract2DCode(grid3D, level);
            Set<String> intersectingGrids = BeiDouGrid2DRangeQuery.findGridCodesInRange(geom, getGridLevel(grid2D));

            // 3. 如果二维相交且高度相交，则三维相交（精确方案）
            return intersectingGrids.contains(grid2D);

        } catch (Exception e) {
            log.debug("三维网格验证失败: " + grid3D + " " + e.getMessage());
            return false;
        }
    }

    /**
     * 从三维编码中提取二维编码部分
     */
    private static String extract2DCode(String grid3D, int level) {
        StringBuilder code2D = new StringBuilder();
        code2D.append(grid3D.charAt(0)); // 纬度方向

        int index = 2; // 跳过纬度方向和高度方向

        for (int i = 1; i <= level; i++) {
            int level2DLength = BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i] -
                    BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i - 1];
            code2D.append(grid3D, index, index + level2DLength);
            index += level2DLength;

            // 跳过高度编码部分
            if (i == 1) {
                index += 2;
            } else {
                index += 1;
            }
        }

        return code2D.toString();
    }

    /**
     * 获取三维网格的高度尺寸
     */
    private static double getGridHeight3D(int level) {
        if (level < 1 || level > 10) {
            throw new IllegalArgumentException("层级必须在1-10之间");
        }
        return BeiDouGridConstants.GRID_SIZES_3D[level];
    }

    /**
     * 获取二维网格码的层级
     */
    private static int getGridLevel(String grid2D) {
        int length = grid2D.length();
        for (int i = 1; i <= 10; i++) {
            if (length == BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i]) {
                return i;
            }
        }
        throw new IllegalArgumentException("无效的网格码格式: " + grid2D);
    }

    /**
     * 获取三维网格码的层级
     */
    private static int getGridLevel3D(String grid3D) {
        int length = grid3D.length();
        for (int i = 1; i <= 10; i++) {
            int expectedLength = 2; // 方向位
            for (int j = 1; j <= i; j++) {
                expectedLength += (BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[j] -
                        BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[j - 1]);
                expectedLength += (j == 1) ? 2 : 1;
            }
            if (expectedLength == length) {
                return i;
            }
        }
        throw new IllegalArgumentException("无效的三维网格码格式: " + grid3D);
    }

    /**
     * 创建网格边界几何图形
     */
    private static Geometry createGridBounds(BeiDouGeoPoint swPoint, int level) {
        double gridSize = BeiDouGridConstants.GRID_SIZES_DEGREES[level][0].doubleValue();
        Coordinate[] coordinates = new Coordinate[5];
        coordinates[0] = new Coordinate(swPoint.getLongitude(), swPoint.getLatitude());
        coordinates[1] = new Coordinate(swPoint.getLongitude() + gridSize, swPoint.getLatitude());
        coordinates[2] = new Coordinate(swPoint.getLongitude() + gridSize, swPoint.getLatitude() + gridSize);
        coordinates[3] = new Coordinate(swPoint.getLongitude(), swPoint.getLatitude() + gridSize);
        coordinates[4] = coordinates[0]; // 闭合多边形
        return new GeometryFactory().createPolygon(coordinates);
    }

}
