package io.github.ywx001.core.utils;

import io.github.ywx001.core.constants.BeiDouGridConstants;
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
                .latitude(-34.6037)
                .longitude(-58.3816)
                .height(50)
                .build();

        String code3D = BeiDouGridUtils.encode3D(point, 10);
        System.out.println(code3D);
        assertNotNull(code3D);
    }

    @Test
    void testDecode3D() {
        String code3D = "S021I0085010760D3000756024211474";
        BeiDouGeoPoint beiDouGeoPoint = BeiDouGridUtils.decode3D(code3D);
        System.out.println(beiDouGeoPoint);
        assertNotNull(beiDouGeoPoint);
    }

    /**
     * 测试西南半球编解码一致性问题
     * 验证解码后再编码的坐标误差是否在可接受范围内（一个网格误差）
     */
    @Test
    void testSouthwestHemisphereEncodingConsistency() {
        // 原始编码
        String originalCode = "S021I0085010760D3000756024211474";
        log.info("原始编码: {}", originalCode);

        // 解码
        BeiDouGeoPoint decodedPoint = BeiDouGridUtils.decode3D(originalCode);
        log.info("解码结果: {}", decodedPoint);

        // 再次编码
        String reencodedCode = BeiDouGridUtils.encode3D(decodedPoint, 10);
        log.info("重新编码: {}", reencodedCode);

        // 检查坐标误差是否在可接受范围内（一个网格误差）
        double latError = Math.abs(decodedPoint.getLatitude() - (-34.6037));
        double lngError = Math.abs(decodedPoint.getLongitude() - (-58.3816));
        double heightError = Math.abs(decodedPoint.getHeight() - 50);

        // 10级网格的精度为0.00048828125秒（约1.5厘米）
        // 考虑到编解码过程中的舍入误差，使用网格精度的3倍作为容差
        double gridPrecisionSeconds = 0.00048828125;
        double gridPrecisionDegrees = gridPrecisionSeconds / 3600.0; // 转换为度
        double maxGridError = gridPrecisionDegrees * 3; // 约4.5厘米的误差容差
        boolean isWithinGridError = latError <= maxGridError && lngError <= maxGridError;

        log.info("坐标误差是否在网格范围内: {}", isWithinGridError);
        log.info("精度误差 - 纬度: {}°, 经度: {}°, 高度: {}米",
                String.format("%.8f", latError), 
                String.format("%.8f", lngError), 
                String.format("%.4f", heightError));

        assertTrue(isWithinGridError, "西南半球编解码坐标误差超出网格范围");
        assertTrue(heightError <= 1.0, "高度误差应小于1米");
    }

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
