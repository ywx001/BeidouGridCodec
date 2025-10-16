package io.github.ywx001.core.encoder;

import io.github.ywx001.core.constants.BeiDouGridConstants;
import io.github.ywx001.core.model.BeiDouGeoPoint;
import io.github.ywx001.core.common.BeiDouGridCommonUtils;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 北斗网格码编码器
 * 负责所有编码相关的逻辑
 */
@Slf4j
public class BeiDouGridEncoder {

    // 缓存编码映射表，避免重复创建
    private static final Map<String, int[][]> LEVEL3_ENCODING_MAP_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, int[][]> LEVEL6_ENCODING_MAP_CACHE = new ConcurrentHashMap<>();

    /**
     * 对一个经纬度坐标进行二维编码
     *
     * @param point 经纬度坐标，使用小数形式（正负号表示方向）
     * @param level 要编码到第几级，范围1-10
     * @return 北斗二维网格位置码
     */
    public static String encode2D(BeiDouGeoPoint point, Integer level) {
        validateEncodeParameters(point, level);

        // 记录第n级网格的定位角点经纬度
        BigDecimal baseLng = BigDecimal.ZERO;
        BigDecimal baseLat = BigDecimal.ZERO;

        // 获取半球信息，用于网格码方向转换
        String hemisphere = BeiDouGridCommonUtils.getHemisphere(point);

        // 存储结果，以半球纬度方向开头
        StringBuilder resCode = new StringBuilder().append(hemisphere.charAt(0));

        BigDecimal latitude = BigDecimal.valueOf(point.getLatitude());
        BigDecimal longitude = BigDecimal.valueOf(point.getLongitude());

        // 南北极北斗二维网格位置码特殊处理
        if (latitude.abs().compareTo(new BigDecimal("88")) >= 0) {
            log.warn("极地区域编码尚未实现");
            throw new UnsupportedOperationException("极地区域编码尚未实现");
        }

        // 逐级编码
        for (int i = 1; i <= level; i++) {
            // 获取当前层级的网格精度
            BigDecimal lngSize = BeiDouGridConstants.GRID_SIZES_DEGREES[i][0];
            BigDecimal latSize = BeiDouGridConstants.GRID_SIZES_DEGREES[i][1];

            // 计算经纬度坐标在当前层级的网格索引
            BigDecimal lngDiff = longitude.subtract(baseLng);
            int lngP = lngDiff.divide(lngSize, 0, RoundingMode.FLOOR).intValue();

            BigDecimal latDiff = latitude.abs().subtract(baseLat);
            int latP = latDiff.divide(latSize, 0, RoundingMode.FLOOR).intValue();

            if (i == 1) {
                // 第一级特殊处理
                if (lngP < 0) {
                    baseLng = baseLng.add(new BigDecimal(-lngP - 1).multiply(lngSize));
                } else {
                    baseLng = baseLng.add(new BigDecimal(lngP).multiply(lngSize));
                }
                baseLat = baseLat.add(new BigDecimal(latP).multiply(latSize));

                resCode.append(encodeFragment(i, lngP + 31, latP, hemisphere));

                // 从第二级开始使用绝对值
                latitude = latitude.abs();
                longitude = longitude.abs();
            } else {
                // 更新基准点坐标
                baseLng = baseLng.add(new BigDecimal(lngP).multiply(lngSize));
                baseLat = baseLat.add(new BigDecimal(latP).multiply(latSize));
                resCode.append(encodeFragment(i, lngP, latP, hemisphere));
            }
        }

        return resCode.toString();
    }

    /**
     * 对一个经纬度坐标和高度进行三维编码（高度部分）
     *
     * @param height 高度（单位：米）
     * @param level    要编码到第几级
     * @return 北斗三维网格位置码的高度部分
     */
    public static String encode3DHeight(double height, Integer level) {
        if (level == null || level < 1 || level > 10) {
            throw new IllegalArgumentException("编码级别必须在1-10之间");
        }

        // 计算高度编码的数学参数
        double theta = Math.PI / 180 / 60 / 60 / 2048;  // theta = π/180/3600/2048
        double theta0 = Math.PI / 180;                  // theta0 = π/180

        // 计算高度编码的值
        int n = (int) Math.floor(
                (theta0 / theta) *
                        (Math.log((height + BeiDouGridConstants.EARTH_RADIUS) / BeiDouGridConstants.EARTH_RADIUS) / Math.log(1 + theta0))
        );

        // 确定高度方向编码（0表示正，1表示负）
        String signCode = n < 0 ? "1" : "0";
        n = Math.abs(n);

        // 将高度编码转换为32位二进制字符串
        StringBuilder binaryString = buildBinaryString(n, signCode);

        // 构建高度编码结果
        return buildHeightCode(binaryString, level, signCode);
    }

    /**
     * 对一个经纬度坐标和高度进行三维编码（完整三维编码）
     *
     * @param point    经纬高度坐标
     * @param level    要编码到第几级
     * @return 北斗三维网格位置码
     */
    public static String encode3D(BeiDouGeoPoint point, Integer level) {
        validateEncodeParameters(point, level);

        // 计算高度编码的数学参数
        double theta = Math.PI / 180 / 60 / 60 / 2048;  // theta = π/180/3600/2048
        double theta0 = Math.PI / 180;                  // theta0 = π/180

        // 计算高度编码的值
        int n = (int) Math.floor(
                (theta0 / theta) *
                        (Math.log((point.getHeight() + BeiDouGridConstants.EARTH_RADIUS) / BeiDouGridConstants.EARTH_RADIUS) / Math.log(1 + theta0))
        );

        // 确定高度方向编码（0表示正，1表示负）
        String signCode = n < 0 ? "1" : "0";
        n = Math.abs(n);

        // 将高度编码转换为32位二进制字符串
        StringBuilder binaryString = buildBinaryString(n, signCode);

        // 获取半球信息，用于网格码方向转换
        String hemisphere = BeiDouGridCommonUtils.getHemisphere(point);
        String latDirection = String.valueOf(hemisphere.charAt(0));

        // 构建结果
        StringBuilder result = new StringBuilder();
        result.append(latDirection); // 纬度方向位
        result.append(signCode); // 高度方向位

        double longitude = point.getLongitude();
        double latitude = Math.abs(point.getLatitude()); // 使用纬度绝对值

        // 转换为秒
        double lngInSec = longitude * 3600;
        double latInSec = latitude * 3600;

        double lngOffset = 0;
        double latOffset = 0;
        int binaryIndex = 1; // 跳过高度方向位

        // 逐级编码
        for (int i = 1; i <= level; i++) {
            String fragment2D;

            if (i == 1) {
                // 第一级特殊处理
                int lngIndex = (int) Math.floor(lngInSec / BeiDouGridConstants.GRID_SIZES_SECONDS[i][0]);
                int latIndex = (int) Math.floor(latInSec / BeiDouGridConstants.GRID_SIZES_SECONDS[i][1]);

                // 更新偏移量
                lngOffset = (lngIndex >= 0 ? lngIndex : -lngIndex - 1) * BeiDouGridConstants.GRID_SIZES_SECONDS[i][0];
                latOffset = latIndex * BeiDouGridConstants.GRID_SIZES_SECONDS[i][1];

                // 生成二维编码片段
                fragment2D = encodeFragment(i, lngIndex + 31, latIndex, BeiDouGridCommonUtils.getHemisphere(point));
            } else {
                // 其他级别 - 使用绝对值的差值计算索引
                int lngIndex = (int) Math.floor((Math.abs(lngInSec) - lngOffset) / BeiDouGridConstants.GRID_SIZES_SECONDS[i][0]);
                int latIndex = (int) Math.floor((Math.abs(latInSec) - latOffset) / BeiDouGridConstants.GRID_SIZES_SECONDS[i][1]);

                // 调试日志：记录第三级网格索引计算
                if (i == 3) {
                    log.debug("L3索引诊断: lngInSec={}, lngOffset={}, gridSizeLng={}, lngIndex={}",
                            Math.abs(lngInSec), lngOffset, BeiDouGridConstants.GRID_SIZES_SECONDS[i][0], lngIndex);
                    log.debug("L3索引诊断: latInSec={}, latOffset={}, gridSizeLat={}, latIndex={}",
                            Math.abs(latInSec), latOffset, BeiDouGridConstants.GRID_SIZES_SECONDS[i][1], latIndex);
                }

                // 更新偏移量
                lngOffset += lngIndex * BeiDouGridConstants.GRID_SIZES_SECONDS[i][0];
                latOffset += latIndex * BeiDouGridConstants.GRID_SIZES_SECONDS[i][1];

                // 生成二维编码片段
                fragment2D = encodeFragment(i, lngIndex, latIndex, BeiDouGridCommonUtils.getHemisphere(point));


            }

            // 添加二维编码片段
            result.append(fragment2D);

            // 添加高度编码片段
            int bits = BeiDouGridConstants.ELEVATION_ENCODING[i][0];
            int radix = BeiDouGridConstants.ELEVATION_ENCODING[i][1];

            // 从二进制字符串中提取对应位数
            String elevationFragment = binaryString.substring(binaryIndex, binaryIndex + bits);
            int codeI = Integer.parseInt(elevationFragment, 2);

            // 转换为对应进制的字符串
            String codeStr = Integer.toString(codeI, radix).toUpperCase();

            // 第一级需要补零至2位
            if (i == 1) {
                codeStr = String.format("%2s", codeStr).replace(' ', '0');
            }

            result.append(codeStr);
            binaryIndex += bits;
        }

        return result.toString();
    }

    /**
     * 验证编码参数
     */
    private static void validateEncodeParameters(BeiDouGeoPoint point, Integer level) {
        if (point == null) {
            throw new IllegalArgumentException("坐标点不能为空");
        }

        if (level == null || level < 1 || level > 10) {
            throw new IllegalArgumentException("编码级别必须在1-10之间");
        }
    }

    /**
     * 生成指定层级的编码片段
     */
    private static String encodeFragment(int level, int lngCount, int latCount, String hemisphere) {
        return switch (level) {
            case 1 -> encodeLevel1(lngCount, latCount);
            case 2 -> encodeLevel2(lngCount, latCount, hemisphere);
            case 3 -> encodeLevel3(lngCount, latCount, hemisphere);
            case 4, 5 -> encodeLevel4_5(lngCount, latCount, hemisphere);
            case 6 -> encodeLevel6(lngCount, latCount, hemisphere);
            case 7, 8, 9, 10 -> encodeLevel7_10(lngCount, latCount, hemisphere);
            default -> throw new IllegalArgumentException("非法层级level: " + level);
        };
    }

    /**
     * 一级网格编码（标准图2）
     */
    private static String encodeLevel1(int lngCount, int latCount) {
        return String.format("%02d", lngCount) + (char) ('A' + latCount);
    }

    /**
     * 二级网格编码（标准图3）
     */
    private static String encodeLevel2(int lngCount, int latCount, String hemisphere) {
        int[] adjusted = adjustCounts(lngCount, latCount, hemisphere, 11, 7);
        return toHexPair(adjusted[0], adjusted[1]);
    }

    /**
     * 三级网格Z序编码（标准图4）
     */
    private static String encodeLevel3(int lngCount, int latCount, String hemisphere) {
        log.debug("L3编码输入: lngCount={}, latCount={}, hemisphere={}", lngCount, latCount, hemisphere);
        int[][] encodingMap = getLevel3EncodingMap(hemisphere);

        // 调试：打印整个编码映射表
        log.debug("L3编码映射表: {} hemisphere", hemisphere);
        for (int i = 0; i < encodingMap.length; i++) {
            log.debug("  行{}: [{}, {}]", i, encodingMap[i][0], encodingMap[i][1]);
        }

        String result = String.valueOf(encodingMap[latCount][lngCount]);
        log.debug("L3编码结果: encodingMap[{}][{}] = {}", latCount, lngCount, result);
        return result;
    }

    /**
     * 四级/五级网格编码（标准图5、6）
     */
    private static String encodeLevel4_5(int lngCount, int latCount, String hemisphere) {
        int[] adjusted = adjustCounts(lngCount, latCount, hemisphere, 14, 14);
        return toHexPair(adjusted[0], adjusted[1]);
    }

    /**
     * 六级网格Z序编码（标准图7）
     */
    private static String encodeLevel6(int lngCount, int latCount, String hemisphere) {
        int[][] encodingMap = getLevel6EncodingMap(hemisphere);
        return String.valueOf(encodingMap[latCount][lngCount]);
    }

    /**
     * 七到十级网格编码（标准图8）
     */
    private static String encodeLevel7_10(int lngCount, int latCount, String hemisphere) {
        int[] adjusted = adjustCounts(lngCount, latCount, hemisphere, 7, 7);
        return toHexPair(adjusted[0], adjusted[1]);
    }

    /**
     * 方向调整通用方法
     * 根据半球信息调整经纬度网格索引
     */
    private static int[] adjustCounts(int lng, int lat, String hemisphere, int maxLng, int maxLat) {
        return switch (hemisphere) {
            case "NW" -> new int[]{lng, maxLat - lat};          // 经度递增，纬度递减
            case "NE" -> new int[]{lng, lat};                   // 双递增
            case "SW" -> new int[]{maxLng - lng, maxLat - lat}; // 双递减
            case "SE" -> new int[]{maxLng - lng, lat};          // 经度递减，纬度递增
            default -> new int[]{lng, lat};                   // 默认NE规则
        };
    }

    /**
     * 获取三级网格编码映射表
     * 使用缓存避免重复创建
     */
    private static int[][] getLevel3EncodingMap(String hemisphere) {
        return LEVEL3_ENCODING_MAP_CACHE.computeIfAbsent(hemisphere, key -> switch (key) {
            case "NW" -> new int[][]{{1, 0}, {3, 2}, {5, 4}};
            case "NE" -> new int[][]{{0, 1}, {2, 3}, {4, 5}};
            case "SW" -> new int[][]{{5, 4}, {3, 2}, {1, 0}};
            case "SE" -> new int[][]{{4, 5}, {2, 3}, {0, 1}};
            default -> new int[][]{{0, 1}, {2, 3}, {4, 5}}; // 默认东北半球
        });
    }

    /**
     * 获取六级网格编码映射表
     * 使用缓存避免重复创建
     */
    private static int[][] getLevel6EncodingMap(String hemisphere) {
        return LEVEL6_ENCODING_MAP_CACHE.computeIfAbsent(hemisphere, key -> switch (key) {
            case "NW" -> new int[][]{{1, 0}, {3, 2}};
            case "NE" -> new int[][]{{0, 1}, {2, 3}};
            case "SW" -> new int[][]{{3, 2}, {1, 0}};
            case "SE" -> new int[][]{{2, 3}, {0, 1}};
            default -> new int[][]{{0, 1}, {2, 3}}; // 默认东北半球
        });
    }

    /**
     * 转换为十六进制对（如 3,A）
     */
    private static String toHexPair(int lng, int lat) {
        return Integer.toHexString(lng).toUpperCase() +
                Integer.toHexString(lat).toUpperCase();
    }

    /**
     * 构建二进制字符串
     */
    private static StringBuilder buildBinaryString(int n, String signCode) {
        StringBuilder binaryString = new StringBuilder();
        binaryString.append(signCode); // 高度方向位

        // 生成31位二进制表示
        for (int i = 30; i >= 0; i--) {
            binaryString.append((n >> i) & 1);
        }
        return binaryString;
    }

    /**
     * 构建高度编码
     */
    private static String buildHeightCode(StringBuilder binaryString, int level, String signCode) {
        StringBuilder heightCode = new StringBuilder();
        heightCode.append(signCode); // 高度方向位

        int binaryIndex = 1; // 跳过高度方向位

        // 根据各级网格的高度编码位数和基数，生成各级高度编码
        for (int i = 1; i <= level; i++) {
            int bits = BeiDouGridConstants.ELEVATION_ENCODING[i][0];
            int radix = BeiDouGridConstants.ELEVATION_ENCODING[i][1];

            // 从二进制字符串中提取对应位数
            String elevationFragment = binaryString.substring(binaryIndex, binaryIndex + bits);
            int codeI = Integer.parseInt(elevationFragment, 2);

            // 转换为对应进制的字符串
            String codeStr = Integer.toString(codeI, radix).toUpperCase();

            // 第一级需要补零至2位
            if (i == 1) {
                codeStr = String.format("%2s", codeStr).replace(' ', '0');
            }

            heightCode.append(codeStr);
            binaryIndex += bits;
        }

        return heightCode.toString();
    }
}
