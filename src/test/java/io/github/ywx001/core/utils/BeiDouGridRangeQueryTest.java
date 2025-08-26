package io.github.ywx001.core.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 北斗网格范围查询工具测试类
 */
@Slf4j
class BeiDouGridRangeQueryTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    @Test
    void testFindGridCodesInRangeWithSmallPolygon() {
        // 创建一个小的多边形（北京故宫区域）
        Coordinate[] polygonCoords = new Coordinate[]{
            new Coordinate(116.391, 39.913),
            new Coordinate(116.401, 39.913),
            new Coordinate(116.401, 39.923),
            new Coordinate(116.391, 39.923),
            new Coordinate(116.391, 39.913)
        };
        // 查找网格码层级
        int targetLevel = 7;

        Geometry polygon = GEOMETRY_FACTORY.createPolygon(polygonCoords);
        System.out.println("初始数据"+ new GeoJsonWriter().write(polygon));
        // 查找网格码
        Set<String> gridCodes = BeiDouGridUtils.findIntersectingGridCodes(polygon, targetLevel);

        assertNotNull(gridCodes);
        assertFalse(gridCodes.isEmpty());

        System.out.println("找到 " + gridCodes.size() + " 个"+targetLevel+"级网格码:");
        gridCodes.forEach(System.out::println);
    }

    @Test
    void testFindGridCodesInRangeWithLineString() {
        // 创建一个线（长安街一段）
        Coordinate[] lineCoords = new Coordinate[]{
            new Coordinate(116.35, 39.90),
            new Coordinate(116.45, 39.90)
        };

        Geometry line = GEOMETRY_FACTORY.createLineString(lineCoords);

        // 查找二级网格码
        Set<String> gridCodes = BeiDouGridRangeQuery.findGridCodesInRange(line, 2);

        assertNotNull(gridCodes);
        assertFalse(gridCodes.isEmpty());

        // 验证网格码格式
        for (String code : gridCodes) {
            assertTrue(code.startsWith("N"));
            // 二级网格码应该是6位长度
            assertEquals(6, code.length());
        }

        System.out.println("找到 " + gridCodes.size() + " 个二级网格码:");
        gridCodes.forEach(System.out::println);
    }

    @Test
    void testSpatialRelationEnum() {
        // 测试空间关系枚举值
        BeiDouGridRangeQuery.SpatialRelation contains = BeiDouGridRangeQuery.SpatialRelation.CONTAINS;
        BeiDouGridRangeQuery.SpatialRelation intersects = BeiDouGridRangeQuery.SpatialRelation.INTERSECTS;
        BeiDouGridRangeQuery.SpatialRelation within = BeiDouGridRangeQuery.SpatialRelation.WITHIN;
        BeiDouGridRangeQuery.SpatialRelation disjoint = BeiDouGridRangeQuery.SpatialRelation.DISJOINT;

        assertNotNull(contains);
        assertNotNull(intersects);
        assertNotNull(within);
        assertNotNull(disjoint);
    }

    @Test
    void testInvalidParameters() {
        // 测试参数验证
        assertThrows(IllegalArgumentException.class, () -> {
            BeiDouGridRangeQuery.findGridCodesInRange(null, 3);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            Geometry geom = GEOMETRY_FACTORY.createPoint(new Coordinate(116.0, 39.0));
            BeiDouGridRangeQuery.findGridCodesInRange(geom, 0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            Geometry geom = GEOMETRY_FACTORY.createPoint(new Coordinate(116.0, 39.0));
            BeiDouGridRangeQuery.findGridCodesInRange(geom, 11);
        });
    }

    @Test
    void testCreateGridPolygon() {
        // 测试网格码
        String g = "N50J47595013";
        Geometry p = BeiDouGridRangeQuery.createGridPolygon(g);

//        // 测试一级网格码
//        String level1Grid = "N31A";
//        Geometry polygon1 = BeiDouGridRangeQuery.createGridPolygon(level1Grid);
//
//        assertNotNull(polygon1);
//        assertTrue(polygon1.isValid());
//        assertEquals("Polygon", polygon1.getGeometryType());
//
//        // 验证一级网格尺寸（6°×4°）
//        Envelope envelope1 = polygon1.getEnvelopeInternal();
//        assertEquals(6.0, envelope1.getWidth(), 0.001);
//        assertEquals(4.0, envelope1.getHeight(), 0.001);
//
//        // 测试二级网格码
//        String level2Grid = "N31A00";
//        Geometry polygon2 = BeiDouGridRangeQuery.createGridPolygon(level2Grid);
//
//        assertNotNull(polygon2);
//        assertTrue(polygon2.isValid());
//        assertEquals("Polygon", polygon2.getGeometryType());
//
//        // 验证二级网格尺寸（0.5°×0.5°）
//        Envelope envelope2 = polygon2.getEnvelopeInternal();
//        assertEquals(0.5, envelope2.getWidth(), 0.001);
//        assertEquals(0.5, envelope2.getHeight(), 0.001);
//
//        // 测试无效网格码
//        assertThrows(IllegalArgumentException.class, () -> {
//            BeiDouGridRangeQuery.createGridPolygon("INVALID");
//        });
//
//        System.out.println("一级网格 " + level1Grid + " 的多边形: " + new GeoJsonWriter().write(polygon1));
//        System.out.println("二级网格 " + level2Grid + " 的多边形: " + new GeoJsonWriter().write(polygon2));
    }
}
