package io.github.ywx001.core.utils;

import io.github.ywx001.core.common.BeiDouGrid3DRangeQuery;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class BeiDouGrid2D3DRangeQueryTest {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void testFind3DGridCodesInRangeWithExplicitHeight() {
        // 创建一个简单的矩形几何图形（包含高度数据）
        Coordinate[] coordinates = new Coordinate[]{
                new Coordinate(116.391, 39.913, 100),
                new Coordinate(116.401, 39.913, 100),
                new Coordinate(116.401, 39.923, 100),
                new Coordinate(116.391, 39.923, 100),
                new Coordinate(116.391, 39.913, 100)
        };
        Geometry geom = GEOMETRY_FACTORY.createPolygon(coordinates);
        log.info("初始数据{}", new GeoJsonWriter().write(geom));
        // 查询网格
        Set<String> result = BeiDouGridUtils.find3DIntersectingGridCodes(
                geom, 8, 0, 0);


        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testFind3DGridCodesInRangeWithAutoHeight() {
        // 创建一个简单的矩形几何图形（包含高度数据）
        Coordinate[] coordinates = new Coordinate[]{
                new Coordinate(116.3, 39.9, 100),
                new Coordinate(116.4, 39.9, 120),
                new Coordinate(116.4, 40.0, 120),
                new Coordinate(116.3, 40.0, 100),
                new Coordinate(116.3, 39.9, 100)
        };
        Geometry geom = GEOMETRY_FACTORY.createPolygon(coordinates);

        // 查询3级网格（自动计算高度范围）
        Set<String> result = BeiDouGrid3DRangeQuery.find3DGridCodesInRange(
                geom, 3);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        // 验证结果网格码格式
        result.forEach(code -> {
            assertTrue(code.startsWith("N")); // 纬度方向
            assertTrue(code.length() >= 5); // 3级网格码长度
        });
    }

    @Test
    void testParameterValidation() {
        // 测试空几何图形
        assertThrows(IllegalArgumentException.class, () -> BeiDouGrid3DRangeQuery.find3DGridCodesInRange(null, 3));

        // 测试无效层级
        Geometry geom = GEOMETRY_FACTORY.createPoint(new Coordinate(116.3, 39.9));
        assertThrows(IllegalArgumentException.class, () -> BeiDouGrid3DRangeQuery.find3DGridCodesInRange(geom, 0));
        assertThrows(IllegalArgumentException.class, () -> BeiDouGrid3DRangeQuery.find3DGridCodesInRange(geom, 11));

        // 测试无效高度范围
        assertThrows(IllegalArgumentException.class, () -> BeiDouGrid3DRangeQuery.find3DGridCodesInRange(geom, 3, 200, 100));
    }

    @Test
    void testExtractHeightPointsFromGeometry() {
        // 创建一个包含多个高度点的几何图形
        Coordinate[] coordinates = new Coordinate[]{
                new Coordinate(116.3, 39.9, 100),
                new Coordinate(116.4, 39.9, 120),
                new Coordinate(116.4, 40.0, 150),
                new Coordinate(116.3, 40.0, 100)
        };
        Geometry geom = GEOMETRY_FACTORY.createLineString(coordinates);

        Set<Double> heights = BeiDouGrid3DRangeQuery.extractHeightPointsFromGeometry(geom);
        assertEquals(3, heights.size()); // 去重后的高度点数量
        assertTrue(heights.contains(100.0));
        assertTrue(heights.contains(120.0));
        assertTrue(heights.contains(150.0));
    }
    @Test
    void testFind3DGridCodesWithLineString(){
        Coordinate[] coordinates = new Coordinate[]{
                new Coordinate(113.551158, 22.40233,100),
                new Coordinate(113.55386, 22.402613, 120),
                new Coordinate(113.556889, 22.40143, 150),
                new Coordinate(113.558322, 22.399175, 100)
        };
        LineString lineString = GEOMETRY_FACTORY.createLineString(coordinates);
        List<String> result = BeiDouGridUtils.find3DIntersectingGridCodes(lineString, 6);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        log.debug("结果{}", result);
    }
}
