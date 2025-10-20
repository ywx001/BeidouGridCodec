package io.github.ywx001.core.encoder;

import io.github.ywx001.core.common.BeiDouGridCommonUtils;
import io.github.ywx001.core.constants.BeiDouGridConstants;
import io.github.ywx001.core.decoder.BeiDouGridDecoder;
import io.github.ywx001.core.model.BeiDouGeoPoint;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 北斗网格码编码器
 * 负责所有编码相关的逻辑
 */
@Slf4j
public class BeiDouGridEncoder {

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
                // 生成编码
                resCode.append(encodeFragment(i, lngP, latP, hemisphere));
            }
        }

        return resCode.toString();
    }

    /**
     * 对一个经纬度坐标和高度进行三维编码（高度部分）
     *
     * @param height 高度（单位：米）
     * @param level  要编码到第几级
     * @return 北斗三维网格位置码的高度部分
     */
    public static String encode3DHeight(double height, Integer level) {
        if (level == null || level < 1 || level > 10) {
            throw new IllegalArgumentException("编码级别必须在1-10之间");
        }

        // 计算高度编码的值
        int n = calculateHeightEncodingValue(height);

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
     * @param point 经纬高度坐标
     * @param level 要编码到第几级
     * @return 北斗三维网格位置码
     */
    public static String encode3D(BeiDouGeoPoint point, Integer level) {
        validateEncodeParameters(point, level);

        // 计算高度编码的值
        int n = calculateHeightEncodingValue(point.getHeight());

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
            case 2, 4, 5, 7, 8, 9, 10 -> toHexPair(lngCount, latCount);
            case 3 -> encodeLevel3(lngCount, latCount, hemisphere);
            case 6 -> encodeLevel6(lngCount, latCount, hemisphere);
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
     * 三级网格Z序编码（标准图4）
     */
    private static String encodeLevel3(int lngCount, int latCount, String hemisphere) {
        // 根据半球信息调整经纬度网格索引
        int[] adjusted = BeiDouGridCommonUtils.adjustCounts(lngCount, latCount, hemisphere, 1, 2);
        lngCount = adjusted[0];
        latCount = adjusted[1];

        int[][] encodingMap = BeiDouGridConstants.getLevel3EncodingMap(hemisphere);

        return String.valueOf(encodingMap[latCount][lngCount]);
    }

    /**
     * 六级网格Z序编码（标准图7）
     */
    private static String encodeLevel6(int lngCount, int latCount, String hemisphere) {
        // 根据半球信息调整经纬度网格索引
        int[] adjusted = BeiDouGridCommonUtils.adjustCounts(lngCount, latCount, hemisphere, 1, 1);
        lngCount = adjusted[0];
        latCount = adjusted[1];

        int[][] encodingMap = BeiDouGridConstants.getLevel6EncodingMap(hemisphere);
        return String.valueOf(encodingMap[latCount][lngCount]);
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

    /**
     * 计算高度编码索引值
     *
     * <p>根据北斗网格编码标准的高度编码公式，将实际海拔高度转换为二进制编码索引值。</p>
     *
     * <p>该方法实现的是编码过程的逆运算：</p>
     * <ul>
     *   <li>编码公式：H = (1 + θ0)^(n*θ/θ0) * r0 - r0</li>
     *   <li>解码公式：n = floor((θ0/θ) * log((H + r0)/r0) / log(1 + θ0))</li>
     * </ul>
     *
     * <p>其中：</p>
     * <ul>
     *   <li>H：海拔高度（米）</li>
     *   <li>n：高度编码索引值，用于生成二进制编码</li>
     *   <li>θ = π/180/3600/2048 ≈ 2.38e-11 rad</li>
     *   <li>θ0 = π/180 ≈ 0.0174533 rad</li>
     *   <li>r0：地球半径（米）</li>
     * </ul>
     *
     * @param height 海拔高度（米）
     * @return 高度编码索引值，用于后续二进制编码生成
     * @see #buildBinaryString(int, String) 将索引值转换为二进制字符串
     * @see BeiDouGridConstants#EARTH_RADIUS 地球半径常量
     * @see BeiDouGridDecoder#decode3DHeight(String, int) 对应的解码方法
     */
    private static int calculateHeightEncodingValue(double height) {
        // 计算高度编码的数学参数
        double theta = Math.PI / 180 / 60 / 60 / 2048;  // theta = π/180/3600/2048
        double theta0 = Math.PI / 180;                  // theta0 = π/180

        // 计算高度编码索引值：n = floor((θ0/θ) * ln((H + r0)/r0) / ln(1 + θ0))
        return (int) Math.floor(
                (theta0 / theta) *
                        (Math.log((height + BeiDouGridConstants.EARTH_RADIUS) / BeiDouGridConstants.EARTH_RADIUS) / Math.log(1 + theta0))
        );
    }
}
