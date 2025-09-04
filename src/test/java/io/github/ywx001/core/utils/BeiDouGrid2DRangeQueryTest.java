package io.github.ywx001.core.utils;

import io.github.ywx001.core.common.BeiDouGrid2DRangeQuery;
import io.github.ywx001.core.decoder.BeiDouGridDecoder;
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
class BeiDouGrid2DRangeQueryTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private static final BeiDouGridDecoder decoder = new BeiDouGridDecoder();

    @Test
    void testFind2DGridCodesInRangeWithSmallPolygon() {
        // 创建一个小的多边形（北京故宫区域）
        Coordinate[] polygonCoords = new Coordinate[]{
                new Coordinate(116.391, 39.913),
                new Coordinate(116.401, 39.913),
                new Coordinate(116.401, 39.923),
                new Coordinate(116.391, 39.923),
                new Coordinate(116.391, 39.913)
        };
        // 查找网格码层级
        int targetLevel = 8;

        Geometry polygon = GEOMETRY_FACTORY.createPolygon(polygonCoords);
        log.info("初始数据{}", new GeoJsonWriter().write(polygon));
        // 查找网格码
        Set<String> gridCodes = BeiDouGridUtils.find2DIntersectingGridCodes(polygon, targetLevel);

        assertNotNull(gridCodes);
        assertFalse(gridCodes.isEmpty());

        log.info("找到 {} 个{}级二维网格码:", gridCodes.size(), targetLevel);
//        for(String code : gridCodes) {
//            assertTrue(BeiDouGrid2DRangeQuery.isGridIntersectsMath(code, polygon));
//        }
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
        Set<String> gridCodes = BeiDouGrid2DRangeQuery.findGridCodesInRange(line, 2);

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
    void testFindGridCodesInRangeWithPoint() {
        // 创建一个点
        Coordinate pointCoords = new Coordinate(120.5830508, 31.1415575);
        // 查找网格码层级
        int targetLevel = 10;

        Geometry point = GEOMETRY_FACTORY.createPoint(pointCoords);

        // 查找二级网格码
        Set<String> gridCodes = BeiDouGrid2DRangeQuery.findGridCodesInRange(point, targetLevel);

        assertNotNull(gridCodes);
        assertFalse(gridCodes.isEmpty());

        System.out.println("找到 " + gridCodes.size() + " 个" + targetLevel + "级网格码:");
        gridCodes.forEach(System.out::println);
    }

    @Test
    void testSpatialRelationEnum() {
        // 测试空间关系枚举值
        BeiDouGrid2DRangeQuery.SpatialRelation contains = BeiDouGrid2DRangeQuery.SpatialRelation.CONTAINS;
        BeiDouGrid2DRangeQuery.SpatialRelation intersects = BeiDouGrid2DRangeQuery.SpatialRelation.INTERSECTS;
        BeiDouGrid2DRangeQuery.SpatialRelation within = BeiDouGrid2DRangeQuery.SpatialRelation.WITHIN;
        BeiDouGrid2DRangeQuery.SpatialRelation disjoint = BeiDouGrid2DRangeQuery.SpatialRelation.DISJOINT;

        assertNotNull(contains);
        assertNotNull(intersects);
        assertNotNull(within);
        assertNotNull(disjoint);
    }

    @Test
    void testInvalidParameters() {
        // 测试参数验证
        assertThrows(IllegalArgumentException.class, () -> {
            BeiDouGrid2DRangeQuery.findGridCodesInRange(null, 3);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            Geometry geom = GEOMETRY_FACTORY.createPoint(new Coordinate(116.0, 39.0));
            BeiDouGrid2DRangeQuery.findGridCodesInRange(geom, 0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            Geometry geom = GEOMETRY_FACTORY.createPoint(new Coordinate(116.0, 39.0));
            BeiDouGrid2DRangeQuery.findGridCodesInRange(geom, 11);
        });
    }

    @Test
    void testCreateGridPolygon() {
        // 测试网格码
        String g = "N050K0040010";
        String s = decoder.extract2DCode(g, 3);
        Geometry p = BeiDouGrid2DRangeQuery.createGridPolygon(s);
        System.out.println(new GeoJsonWriter().write(p));

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
