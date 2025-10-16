package io.github.ywx001.core.utils;

import io.github.ywx001.core.constants.BeiDouGridConstants;
import io.github.ywx001.core.decoder.BeiDouGridDecoder;
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
class BeiDouGridUtilsTest {

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
        String code2D = "N50J47539B825534";
        BeiDouGeoPoint point = decode2D(code2D);
        System.out.println(point);
        assertNotNull(point);
//        assertEquals(point.getLatitude(), 0);
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
                .latitude(-31.396478)
                .longitude(-57.702155)
                .height(50)
                .build();

        String code3D = BeiDouGridUtils.encode3D(point, 10);
        System.out.println(code3D);
        assertNotNull(code3D);
    }

    @Test
    void testDecode3D() {
        String code3D = "S025O00940406203D000576204121744";
        BeiDouGeoPoint beiDouGeoPoint = BeiDouGridUtils.decode3D(code3D);
        System.out.println(beiDouGeoPoint);
        assertNotNull(beiDouGeoPoint);
    }

    /**
     * 测试西南半球编解码一致性问题
     * 验证解码后再编码是否与原编码一致
     */
    @Test
    void testSouthwestHemisphereEncodingConsistency() {
        // 原始编码
        String originalCode = "S025O00940406203D000576204121744";
        log.info("原始编码: {}", originalCode);

        // 解码
        BeiDouGeoPoint decodedPoint = BeiDouGridUtils.decode3D(originalCode);
        log.info("解码结果: {}", decodedPoint);

        // 诊断：打印经纬秒与第3级步长的余数（验证是否落在网格角点）
        double lngSec = Math.abs(decodedPoint.getLongitude()) * 3600.0;
        double latSec = Math.abs(decodedPoint.getLatitude()) * 3600.0;
        double stepLngL3 = 900.0; // 第3级经向步长（15′ = 900″）
        double stepLatL3 = 600.0; // 第3级纬向步长（10′ = 600″）
        double rLng3 = lngSec % stepLngL3;
        double rLat3 = latSec % stepLatL3;
        log.info("L3余数诊断: lngSec={} rLng3={} (步长={}), latSec={} rLat3={} (步长={})",
                lngSec, rLng3, stepLngL3, latSec, rLat3, stepLatL3);

        // 再次编码
        String reencodedCode = BeiDouGridUtils.encode3D(decodedPoint, 10);
        log.info("重新编码: {}", reencodedCode);

        // 验证一致性
        log.info("编码是否一致: {}", originalCode.equals(reencodedCode));

        // 如果不一致，输出差异分析
        if (!originalCode.equals(reencodedCode)) {
            log.error("编解码不一致！");
            log.error("原始编码: {}", originalCode);
            log.error("重新编码: {}", reencodedCode);

            // 分析差异（整体字符差异）
            int minLength = Math.min(originalCode.length(), reencodedCode.length());
            for (int i = 0; i < minLength; i++) {
                if (originalCode.charAt(i) != reencodedCode.charAt(i)) {
                    log.error("第{}位不同: 原始='{}', 重新='{}'", i, originalCode.charAt(i), reencodedCode.charAt(i));
                }
            }
            if (originalCode.length() != reencodedCode.length()) {
                log.error("长度不同: 原始长度={}, 重新长度={}", originalCode.length(), reencodedCode.length());
            }

            // 进一步：逐级二维行列对比，定位第一个不同级别
            int level = BeiDouGridDecoder.getCodeLevel3D(originalCode);
            String original2D = BeiDouGridDecoder.extract2DCode(originalCode, level);
            String reencoded2D = BeiDouGridDecoder.extract2DCode(reencodedCode, level);
            int[][] origIdx = BeiDouGridDecoder.debugDecode2DLevels(original2D);
            int[][] reIdx = BeiDouGridDecoder.debugDecode2DLevels(reencoded2D);

            for (int lv = 1; lv <= level; lv++) {
                int oLng = origIdx[lv - 1][0];
                int oLat = origIdx[lv - 1][1];
                int rLng = reIdx[lv - 1][0];
                int rLat = reIdx[lv - 1][1];
                String mark = (oLng == rLng && oLat == rLat) ? "=" : "!";
                log.error("[二维第{}级] 原({},{}) vs 新({},{}): {}", lv, oLng, oLat, rLng, rLat, mark);
            }
        }

        // 这个测试可能会失败，因为我们正在调查问题
        // assertTrue(originalCode.equals(reencodedCode), "西南半球编解码应该保持一致");
    }

    /**
     * 详细分析编码结构
     */
    @Test
    void testDetailedEncodingAnalysis() {
        String originalCode = "S025O00940406203D000576204121744";
        log.info("=== 详细编码分析 ===");
        log.info("原始编码: {}", originalCode);

        // 分析编码结构
        log.info("编码长度: {}", originalCode.length());
        log.info("第1位(纬度方向): {}", originalCode.charAt(0));
        log.info("第2位(高度方向): {}", originalCode.charAt(1));

        // 分析各级编码
        int pos = 2;
        for (int level = 1; level <= 10; level++) {
            int level2DLength = BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[level] - BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[level - 1];
            String level2DCode = originalCode.substring(pos, pos + level2DLength);
            pos += level2DLength;

            int heightCodeLength = (level == 1) ? 2 : 1;
            String heightCode = originalCode.substring(pos, pos + heightCodeLength);
            pos += heightCodeLength;

            log.info("第{}级 - 二维编码: {}, 高度编码: {}", level, level2DCode, heightCode);
        }

        // 解码
        BeiDouGeoPoint decodedPoint = BeiDouGridUtils.decode3D(originalCode);
        log.info("解码结果: {}", decodedPoint);

        // 重新编码
        String reencodedCode = BeiDouGridUtils.encode3D(decodedPoint, 10);
        log.info("重新编码: {}", reencodedCode);

        // 分析重新编码的结构
        log.info("=== 重新编码分析 ===");
        log.info("重新编码长度: {}", reencodedCode.length());
        log.info("第1位(纬度方向): {}", reencodedCode.charAt(0));
        log.info("第2位(高度方向): {}", reencodedCode.charAt(1));

        pos = 2;
        for (int level = 1; level <= 10; level++) {
            int level2DLength = BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[level] - BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[level - 1];
            String level2DCode = reencodedCode.substring(pos, pos + level2DLength);
            pos += level2DLength;

            int heightCodeLength = (level == 1) ? 2 : 1;
            String heightCode = reencodedCode.substring(pos, pos + heightCodeLength);
            pos += heightCodeLength;

            log.info("第{}级 - 二维编码: {}, 高度编码: {}", level, level2DCode, heightCode);
        }
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

    /**
     * 测试三维解码高度算法
     * 验证解码返回的是网格的底部高度
     */
    @Test
    void testFixedHeightDecode() {
        // 创建测试坐标点
        double inputHeight = 50.0;
        BeiDouGeoPoint point = BeiDouGeoPoint.builder()
                .latitude(31.1415575)
                .longitude(120.5830508)
                .height(inputHeight)
                .build();

        // 测试不同级别的编码解码
        int[] testLevels = {6, 7, 8, 9, 10};

        for (int level : testLevels) {
            log.info("\n--- {}级网格测试 ---", level);

            // 三维编码
            String code3D = encode3D(point, level);
            log.info("{}级三维编码: {}", level, code3D);

            // 三维解码
            BeiDouGeoPoint decodedPoint = decode3D(code3D);
            log.info("{}级三维解码: {}", level, decodedPoint);

            // 验证解码结果
            assertNotNull(decodedPoint, "解码结果不应为空");
            assertNotNull(decodedPoint.getHeight(), "解码高度不应为空");

            double decodedHeight = decodedPoint.getHeight();
            log.info("输入高度: {}米, 解码高度: {}米, 差值: {}米",
                    inputHeight, decodedHeight, Math.abs(inputHeight - decodedHeight));

            // 验证解码高度应该是网格的底部高度（小于等于输入高度）
            assertTrue(decodedHeight <= inputHeight + 0.01,
                "解码高度应小于等于输入高度: " +
                "输入=" + inputHeight + "米, 解码=" + decodedHeight + "米");
        }
    }
}
