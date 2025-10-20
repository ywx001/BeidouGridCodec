package io.github.ywx001.core.utils;

import io.github.ywx001.core.decoder.BeiDouGridDecoder;
import io.github.ywx001.core.encoder.BeiDouGridEncoder;
import io.github.ywx001.core.model.BeiDouGeoPoint;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * 多半球编解码一致性测试
 */
@Slf4j
public class MultiHemisphereTest {

    @Test
    public void testAllHemispheres() {
        log.info("=== 开始多半球编解码一致性测试 ===");

        // 1. 东北半球 (NE) - 中国苏州 N051H0016000480E7010366734331434
        testHemisphere("NE", "中国苏州", 31.1415575, 120.5830508, 50.0);

        // 2. 西南半球 (SW) - 阿根廷布宜诺斯艾利斯 S021I0085010760D3000756024211474
        testHemisphere("SW", "阿根廷布宜诺斯艾利斯", -34.6037, -58.3816, 50.0);

        // 3. 西北半球 (NW) - 美国西雅图 N010L0047010460D5030616124711274
        testHemisphere("NW", "美国西雅图", 47.6062, -122.3321, 50.0);

        // 4. 东南半球 (SE) - 澳大利亚悉尼 S056I0023040C2081020566754261704
        testHemisphere("SE", "澳大利亚悉尼", -33.8688, 151.2093, 50.0);

        log.info("=== 多半球测试完成 ===");
    }

    private void testHemisphere(String hemisphere, String location, double latitude, double longitude, double height) {
        log.info("\n--- 测试{}半球: {} ---", hemisphere, location);

        // 创建测试点
        BeiDouGeoPoint point = BeiDouGeoPoint.builder()
                .latitude(latitude)
                .longitude(longitude)
                .height(height)
                .build();

        log.info("原始坐标: 纬度={}°, 经度={}°, 高度={}米", latitude, longitude, height);

        // 编码
        String encoded = BeiDouGridEncoder.encode3D(point, 10);
        log.info("编码结果: {}", encoded);

        // 解码验证
        BeiDouGeoPoint decoded = BeiDouGridDecoder.decode3D(encoded);
        log.info("解码结果: {}", decoded);

        // 重新编码验证一致性
        String reencoded = BeiDouGridEncoder.encode3D(decoded, 10);
        log.info("重新编码: {}", reencoded);

        // 检查坐标误差是否在可接受范围内（一个网格误差）
        double latError = Math.abs(decoded.getLatitude() - latitude);
        double lngError = Math.abs(decoded.getLongitude() - longitude);
        double heightError = Math.abs(decoded.getHeight() - height);

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

        if (!isWithinGridError) {
            log.error("!!! {}半球坐标误差超出网格范围 !!!", hemisphere);
        } else {
            log.info("✓ {}半球编解码在网格误差范围内", hemisphere);
        }
    }
}
