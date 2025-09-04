package io.github.ywx001.core.utils;

import io.github.ywx001.core.model.BeiDouGeoPoint;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.github.ywx001.core.utils.BeiDouGridUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit测试类 - 北斗网格码工具类测试
 */
@Slf4j
class BeiDouGrid2DUtilsTest {

    /**
     * 示例方法：演示北斗网格码的编解码功能
     */
    @Test
    void testAll() {
        // 创建测试坐标点
        BeiDouGeoPoint point = BeiDouGeoPoint.builder().latitude(31.1415575).longitude(120.5830508).height(50).build();

        // 二维编码测试
        String code2D = encode2D(point, 10);
        log.info("二维编码: {}", code2D);

        // 二维解码测试
        BeiDouGeoPoint decodedPoint = decode2D(code2D);
        log.info("二维解码: {}", decodedPoint);

        // 三维编码测试（高度部分）
        String heightCode = encode3DHeight(50, 10);
        log.info("高度编码: {}", heightCode);

        // 完整三维编码测试
        String code3D = encode3D(point, 10);
        log.info("三维编码: {}", code3D);

        // 三维解码测试
        BeiDouGeoPoint beiDouGeoPoint = decode3D(code3D);
        log.info("三维解码: {}", beiDouGeoPoint);
    }

    @Test
    void testEncode2D() {
        BeiDouGeoPoint point = BeiDouGeoPoint.builder()
                .latitude(31.1415575)
                .longitude(120.5830508)
                .build();

        String code2D = encode2D(point, 5);
        assertNotNull(code2D);
        assertTrue(code2D.startsWith("N"));
        log.info("code2D: {}", code2D);
    }

    @Test
    void testDecode2D() {
        String code2D = "N31A";
        BeiDouGeoPoint point = decode2D(code2D);
        assertNotNull(point);
        assertTrue(point.getLatitude() > 0);
    }

    @Test
    void testEncode3DHeight() {
        String heightCode = encode3DHeight(50, 5);
        assertNotNull(heightCode);
        assertFalse(heightCode.isEmpty());
    }

    @Test
    void testEncode3D() {
        BeiDouGeoPoint point = BeiDouGeoPoint.builder()
                .latitude(31.1415575)
                .longitude(120.5830508)
                .height(50)
                .build();

        String code3D = BeiDouGridUtils.encode3D(point, 5);
        assertNotNull(code3D);
        assertTrue(code3D.startsWith("N"));
    }

    @Test
    void testDecode3D() {
        String code3D = "N050J0047050";
        BeiDouGeoPoint beiDouGeoPoint = BeiDouGridUtils.decode3D(code3D);
        assertNotNull(beiDouGeoPoint);
    }

    @Test
    void testGetHemisphere() {
        BeiDouGeoPoint point = BeiDouGeoPoint.builder()
                .latitude(31.1415575)
                .longitude(120.5830508)
                .build();

        String hemisphere = BeiDouGridUtils.getHemisphere(point);
        assertEquals("NE", hemisphere);
    }

    @Test
    void testGetChildGrids2D() {
        String code2D = "N31A";
        Set<String> result = BeiDouGridUtils.getChildGrids2D(code2D);
        log.info("生成子级2维网格码: {}", result);
        assertNotNull(result);
    }

    @Test
    void testGetChildGrids3D() {
        String code3D = "N050J0047050";
        Set<String> result = BeiDouGridUtils.getChildGrids3D(code3D);
        log.info("生成子级3维网格码: {}", result);
        assertNotNull(result);
    }

}
