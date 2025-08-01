package utils;

import domain.GeoPoint;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 北斗网格码工具类
 * 实现北斗网格码的编码和解码功能
 */
@Slf4j
public class BeiDouGridUtils {

    /**
     * 示例方法：演示北斗网格码的编解码功能
     */
    public static void main(String[] args) {
        // 创建测试坐标点（北京附近）
        GeoPoint point = GeoPoint.builder().latitude(39.99316111).longitude(116.31260278).build();

        // 二维编码测试
        String code2D = encode2D(point, 10);
        log.info("二维编码: {}", code2D);

        // 二维解码测试
        GeoPoint decodedPoint = decode2D(code2D);
        log.info("二维解码: {}", decodedPoint);

        // 三维编码测试（高度部分）
        String altitudeCode = encode3DAltitude(-1000, 10);
        log.info("高度编码: {}", altitudeCode);

        // 完整三维编码测试
        String code3D = encode3D(point, -1000, 10);
        log.info("三维编码: {}", code3D);

        // 三维解码测试
        Map<String, Object> decoded3D = decode3D(code3D);
        log.info("三维解码: {}", decoded3D);
    }

    /**
     * 网格尺寸数组[层级][0:经度度数, 1:纬度度数]
     * 根据标准5.1条网格划分规则定义
     */
    public static final BigDecimal[][] GRID_SIZES_DEGREES = {
            {}, // 第0级占位
            {bd(6), bd(4)}, // 1级：6°×4°
            {bd(0.5), bd(0.5)}, // 2级：30′×30′
            {bd(0.25), bd(10).divide(bd(60), 10, RoundingMode.HALF_UP)}, // 3级：15′×10′
            {bd(1).divide(bd(60), 10, RoundingMode.HALF_UP), bd(1).divide(bd(60), 10, RoundingMode.HALF_UP)}, // 4级：1′×1′
            {bd(4).divide(bd(3600), 10, RoundingMode.HALF_UP), bd(4).divide(bd(3600), 10, RoundingMode.HALF_UP)}, // 5级：4″×4″
            {bd(2).divide(bd(3600), 10, RoundingMode.HALF_UP), bd(2).divide(bd(3600), 10, RoundingMode.HALF_UP)}, // 6级：2″×2″
            {bd(1).divide(bd(4 * 3600), 10, RoundingMode.HALF_UP), bd(1).divide(bd(4 * 3600), 10, RoundingMode.HALF_UP)}, // 7级：1/4″×1/4″
            {bd(1).divide(bd(32 * 3600), 10, RoundingMode.HALF_UP), bd(1).divide(bd(32 * 3600), 10, RoundingMode.HALF_UP)}, // 8级：1/32″×1/32″
            {bd(1).divide(bd(256 * 3600), 10, RoundingMode.HALF_UP), bd(1).divide(bd(256 * 3600), 10, RoundingMode.HALF_UP)}, // 9级：1/256″×1/256″
            {bd(1).divide(bd(2048 * 3600), 10, RoundingMode.HALF_UP), bd(1).divide(bd(2048 * 3600), 10, RoundingMode.HALF_UP)} // 10级：1/2048″×1/2048″
    };

    /**
     * 创建BigDecimal对象的辅助方法
     * 使用String构造器以避免精度问题
     */
    private static BigDecimal bd(double val) {
        return new BigDecimal(String.valueOf(val));
    }

    /**
     * 各层级网格行列数[经度方向, 纬度方向]
     */
    public static final int[][] GRID_DIVISIONS = {
            {},  // 第0级占位
            {60, 22},    // 第1级 (6°=360'/6°)
            {12, 8},     // 第2级
            {2, 3},      // 第3级
            {15, 10},    // 第4级
            {15, 15},    // 第5级
            {2, 2},      // 第6级
            {8, 8},      // 第7级
            {8, 8},      // 第8级
            {8, 8},      // 第9级
            {8, 8}       // 第10级
    };

    // 网格大小数据（单位：秒）
    private static final double[][] GRID_SIZES_SECONDS = {
            {},                           // 第0级（占位）
            {21600.0, 14400.0},          // 第1级
            {1800.0, 1800.0},            // 第2级
            {900.0, 600.0},              // 第3级
            {60.0, 60.0},                // 第4级
            {4.0, 4.0},                  // 第5级
            {2.0, 2.0},                  // 第6级
            {0.25, 0.25},                // 第7级
            {0.03125, 0.03125},          // 第8级
            {0.00390625, 0.00390625},    // 第9级
            {0.00048828125, 0.00048828125} // 第10级
    };

    // 各级网格编码长度
    private static final int[] CODE_LENGTH_AT_LEVEL = {
            1,  // 0级长度
            4,  // 1级长度
            6,  // 2级长度
            7,  // 3级长度
            9,  // 4级长度
            11, // 5级长度
            12, // 6级长度
            14, // 7级长度
            16, // 8级长度
            18, // 9级长度
            20  // 10级长度
    };

    // 三维网格长度数据（单位：米）
    private static final double[] GRID_SIZES_3D = {
            0,       // 0级长度
            445280,  // 1级长度
            55660,   // 2级长度
            27830,   // 3级长度
            1850,    // 4级长度
            123.69,  // 5级长度
            61.84,   // 6级长度
            7.73,    // 7级长度
            0.97,    // 8级长度
            0.121,   // 9级长度
            0.015    // 10级长度
    };

    // 半球信息枚举
    public enum Hemisphere {
        NE, NW, SE, SW;

        public static Hemisphere fromString(String code) {
            return valueOf(code);
        }
    }

    // 经纬度方向枚举
    public enum LngDirection {E, W}

    public enum LatDirection {N, S}

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
    public static String encode2D(GeoPoint point, Integer level) {
        if (point == null) {
            throw new IllegalArgumentException("坐标点不能为空");
        }

        if (level == null || level < 1 || level > 10) {
            throw new IllegalArgumentException("编码级别必须在1-10之间");
        }

        // 记录第n级网格的定位角点经纬度
        BigDecimal baseLng = BigDecimal.ZERO;
        BigDecimal baseLat = BigDecimal.ZERO;

        // 获取半球信息，用于网格码方向转换
        String hemisphere = getHemisphere(point);

        // 存储结果，以半球纬度方向开头
        StringBuilder resCode = new StringBuilder().append(hemisphere.charAt(0));

        BigDecimal latitude = BigDecimal.valueOf(point.getLatitude());
        BigDecimal longitude = BigDecimal.valueOf(point.getLongitude());

        // 南北极北斗二维网格位置码特殊处理
        if (latitude.abs().compareTo(new BigDecimal("88")) >= 0) {
            // 极地区域特殊处理（待实现）
            log.warn("极地区域编码尚未实现");
            throw new UnsupportedOperationException("极地区域编码尚未实现");
        } else {
            for (int i = 1; i <= level; i++) {
                // 获取当前层级的网格精度
                BigDecimal lngSize = GRID_SIZES_DEGREES[i][0];
                BigDecimal latSize = GRID_SIZES_DEGREES[i][1];

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
        }
        return resCode.toString();
    }

    /**
     * 生成指定层级的编码片段
     *
     * @param level      当前层级 (1-10)
     * @param lngCount   经度方向网格索引
     * @param latCount   纬度方向网格索引
     * @param hemisphere 半球信息，用于方向转换
     * @return 编码字符串
     */
    private static String encodeFragment(int level, int lngCount, int latCount, String hemisphere) {
        switch (level) {
            case 1:
                return encodeLevel1(lngCount, latCount);
            case 2:
                return encodeLevel2(lngCount, latCount, hemisphere);
            case 3:
                return encodeLevel3(lngCount, latCount, hemisphere);
            case 4:
            case 5:
                return encodeLevel4_5(lngCount, latCount, hemisphere);
            case 6:
                return encodeLevel6(lngCount, latCount, hemisphere);
            case 7:
            case 8:
            case 9:
            case 10:
                return encodeLevel7_10(lngCount, latCount, hemisphere);
            default:
                throw new IllegalArgumentException("非法层级level: " + level);
        }
    }

    /**
     * 三级网格Z序编码（标准图4）
     */
    private static String encodeLevel3(int lngCount, int latCount, String hemisphere) {
        int[][] encodingMap = getLevel3EncodingMap(hemisphere);
        return String.valueOf(encodingMap[latCount][lngCount]);
    }

    /**
     * 六级网格Z序编码（标准图7）
     */
    private static String encodeLevel6(int lngCount, int latCount, String hemisphere) {
        int[][] encodingMap = getLevel6EncodingMap(hemisphere);
        return String.valueOf(encodingMap[latCount][lngCount]);
    }

    /**
     * 二级网格编码（标准图3）
     */
    private static String encodeLevel2(int lngCount, int latCount, String hemisphere) {
        int[] adjusted = adjustCounts(lngCount, latCount, hemisphere, 11, 7);
        return toHexPair(adjusted[0], adjusted[1]);
    }

    /**
     * 四级/五级网格编码（标准图5、6）
     */
    private static String encodeLevel4_5(int lngCount, int latCount, String hemisphere) {
        int[] adjusted = adjustCounts(lngCount, latCount, hemisphere, 14, 14);
        return toHexPair(adjusted[0], adjusted[1]);
    }

    /**
     * 七到十级网格编码（标准图8）
     */
    private static String encodeLevel7_10(int lngCount, int latCount, String hemisphere) {
        int[] adjusted = adjustCounts(lngCount, latCount, hemisphere, 7, 7);
        return toHexPair(adjusted[0], adjusted[1]);
    }

    /**
     * 一级网格编码（标准图2）
     */
    private static String encodeLevel1(int lngCount, int latCount) {
        return String.format("%02d", lngCount) + (char) ('A' + latCount);
    }

    /**
     * 方向调整通用方法
     * 根据半球信息调整经纬度网格索引
     */
    private static int[] adjustCounts(int lng, int lat, String hemisphere,
                                      int maxLng, int maxLat) {
        switch (hemisphere) {
            case "NW":
                return new int[]{lng, maxLat - lat};          // 经度递增，纬度递减
            case "NE":
                return new int[]{lng, lat};                   // 双递增
            case "SW":
                return new int[]{maxLng - lng, maxLat - lat}; // 双递减
            case "SE":
                return new int[]{maxLng - lng, lat};          // 经度递减，纬度递增
            default:
                return new int[]{lng, lat};                   // 默认NE规则
        }
    }

    /**
     * 获取三级网格编码映射表
     * 使用缓存避免重复创建
     */
    private static int[][] getLevel3EncodingMap(String hemisphere) {
        return LEVEL3_ENCODING_MAP_CACHE.computeIfAbsent(hemisphere, key -> {
            switch (key) {
                case "NW":
                    return new int[][]{{1, 0}, {3, 2}, {5, 4}};
                case "NE":
                    return new int[][]{{0, 1}, {2, 3}, {4, 5}};
                case "SW":
                    return new int[][]{{5, 4}, {3, 2}, {1, 0}};
                case "SE":
                    return new int[][]{{4, 5}, {2, 3}, {0, 1}};
                default:
                    return new int[][]{{0, 1}, {2, 3}, {4, 5}}; // 默认东北半球
            }
        });
    }

    /**
     * 获取六级网格编码映射表
     * 使用缓存避免重复创建
     */
    private static int[][] getLevel6EncodingMap(String hemisphere) {
        return LEVEL6_ENCODING_MAP_CACHE.computeIfAbsent(hemisphere, key -> {
            switch (key) {
                case "NW":
                    return new int[][]{{1, 0}, {3, 2}};
                case "NE":
                    return new int[][]{{0, 1}, {2, 3}};
                case "SW":
                    return new int[][]{{3, 2}, {1, 0}};
                case "SE":
                    return new int[][]{{2, 3}, {0, 1}};
                default:
                    return new int[][]{{0, 1}, {2, 3}}; // 默认东北半球
            }
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
     * 获取经纬度坐标所在的半球信息（用于网格码方向转换）
     * 半球表示形式为：纬度方向(N/S) + 经度方向(E/W)，例如："NE"表示北半球东经区域
     *
     * @param point 经纬度坐标对象，包含经度和纬度字段
     * @return 半球标识字符串（格式：{N|S}{E|W}）
     * @throws IllegalArgumentException 如果经纬度参数无效（为空或非数字）
     */
    private static String getHemisphere(GeoPoint point) {
        // 参数校验：确保坐标对象不为空
        if (point == null) {
            throw new IllegalArgumentException("经纬度坐标对象不能为空");
        }

        // 获取并校验纬度值（范围：-90 ~ 90）
        Double latitude = point.getLatitude();
        if (latitude == null || latitude.isNaN() || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("纬度值无效，应为-90到90之间的数值");
        }

        // 获取并校验经度值（范围：-180 ~ 180）
        Double longitude = point.getLongitude();
        if (longitude == null || longitude.isNaN() || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("经度值无效，应为-180到180之间的数值");
        }

        // 确定纬度方向（北纬N/南纬S）
        String latDir = latitude >= 0 ? "N" : "S";  // 注意：0度属于北半球

        // 确定经度方向（东经E/西经W）
        String lngDir = longitude >= 0 ? "E" : "W"; // 注意：0度属于东经

        return latDir + lngDir;
    }

    /**
     * 从网格码中提取半球信息
     *
     * @param code 北斗网格码
     * @return 半球标识字符串（格式：{N|S}{E|W}）
     */
    private static String getHemisphereFromCode(String code) {
        if (code == null || code.length() < 3) {
            throw new IllegalArgumentException("无效的网格码格式");
        }

        String latDir = code.charAt(0) == 'N' ? "N" : "S";
        String lngDir = Integer.parseInt(code.substring(1, 3)) >= 31 ? "E" : "W";
        return latDir + lngDir;
    }

    /**
     * 对北斗二维网格位置码解码
     *
     * @param code 需要解码的北斗二维网格位置码
     * @return 经纬度坐标
     * @throws IllegalArgumentException 如果位置码格式无效
     */
    public static GeoPoint decode2D(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("位置码不能为空");
        }

        // 获取层级
        int level = getCodeLevel(code);

        // 获取经纬度方向
        Map<String, String> directions = getDirections(code);
        LngDirection lngDir = LngDirection.valueOf(directions.get("lngDirection"));
        LatDirection latDir = LatDirection.valueOf(directions.get("latDirection"));

        // 符号（东经为正，西经为负；北纬为正，南纬为负）
        int lngSign = lngDir == LngDirection.E ? 1 : -1;
        int latSign = latDir == LatDirection.N ? 1 : -1;

        // 累计经纬度（秒）
        double lngInSec = 0;
        double latInSec = 0;

        // 对每一级进行解码
        for (int i = 1; i <= level; i++) {
            double[] offsets = decodeN(code, i);
            lngInSec += offsets[0];
            latInSec += offsets[1];
        }

        // 创建结果对象
        return GeoPoint.builder()
                .longitude((lngInSec * lngSign) / 3600)
                .latitude((latInSec * latSign) / 3600)
                .build();
    }

    /**
     * 获取位置码的层级
     *
     * @param code 北斗网格码
     * @return 层级值（0-10）
     * @throws IllegalArgumentException 如果位置码长度无效
     */
    private static int getCodeLevel(String code) {
        int length = code.length();
        for (int i = 0; i < CODE_LENGTH_AT_LEVEL.length; i++) {
            if (CODE_LENGTH_AT_LEVEL[i] == length) {
                return i;
            }
        }
        throw new IllegalArgumentException("无效的位置码长度: " + length);
    }

    /**
     * 获取经纬度方向
     *
     * @param code 北斗网格码
     * @return 包含经纬度方向的Map
     */
    private static Map<String, String> getDirections(String code) {
        Map<String, String> directions = new HashMap<>(2);
        // 第一位表示纬度方向
        directions.put("latDirection", code.charAt(0) == 'N' ? "N" : "S");
        // 第二、三位表示经度方向（数值大于等于31为东经，否则为西经）
        int lngPart = Integer.parseInt(code.substring(1, 3));
        directions.put("lngDirection", lngPart >= 31 ? "E" : "W");
        return directions;
    }

    /**
     * 解码第n级网格码
     *
     * @param code 完整的北斗网格码
     * @param n    要解码的层级
     * @return 经纬度偏移量（秒）[经度偏移, 纬度偏移]
     */
    private static double[] decodeN(String code, int n) {
        if (n < 1 || n > 10) {
            throw new IllegalArgumentException("层级错误: " + n);
        }

        // 获取该层级的位置码片段
        String fragment = getCodeFragment(code, n);
        // 解析行列号
        int[] rowCol = getRowAndCol(fragment, n, code);

        int lng = rowCol[0];
        int lat = rowCol[1];

        // 特殊处理第1级
        if (n == 1) {
            if (lng == 0) {
                throw new IllegalArgumentException("暂不支持两极地区解码");
            }
            lng = lng >= 31 ? lng - 31 : 30 - lng;
        }

        // 计算偏移量（秒）
        double lngOffset = lng * GRID_SIZES_SECONDS[n][0];
        double latOffset = lat * GRID_SIZES_SECONDS[n][1];

        return new double[]{lngOffset, latOffset};
    }

    /**
     * 获取某一层级的位置码片段
     *
     * @param code  完整的北斗网格码
     * @param level 要获取的层级
     * @return 该层级的编码片段
     */
    private static String getCodeFragment(String code, int level) {
        if (level == 0) {
            return String.valueOf(code.charAt(0));
        }
        int start = CODE_LENGTH_AT_LEVEL[level - 1];
        int end = CODE_LENGTH_AT_LEVEL[level];
        return code.substring(start, end);
    }

    /**
     * 解析行列号
     *
     * @param codeFragment 编码片段
     * @param level        层级
     * @param code         完整的北斗网格码
     * @return 经纬度索引数组 [经度索引, 纬度索引]
     */
    private static int[] getRowAndCol(String codeFragment, int level, String code) {
        if (codeFragment.length() != (CODE_LENGTH_AT_LEVEL[level] - CODE_LENGTH_AT_LEVEL[level - 1])) {
            throw new IllegalArgumentException("编码片段长度错误: " + codeFragment);
        }

        int lng = 0;
        int lat = 0;

        switch (level) {
            case 0:
                return new int[]{0, 0};
            case 1:
                lng = Integer.parseInt(codeFragment.substring(0, 2));
                lat = codeFragment.charAt(2) - 'A';
                break;
            case 2:
                // 二级网格需要根据半球信息进行方向转换
                int encodedLng = Integer.parseInt(codeFragment.substring(0, 1), 16);
                int encodedLat = Integer.parseInt(codeFragment.substring(1, 2), 16);

                if (code != null) {
                    String hemisphere = getHemisphereFromCode(code);
                    switch (hemisphere) {
                        case "NW":
                            // 西北半球：经度递减，纬度递增
                            lng = 11 - encodedLng;
                            lat = encodedLat;
                            break;
                        case "NE":
                            // 东北半球：经度递增，纬度递增
                            lng = encodedLng;
                            lat = encodedLat;
                            break;
                        case "SW":
                            // 西南半球：经度递减，纬度递减
                            lng = 11 - encodedLng;
                            lat = 7 - encodedLat;
                            break;
                        case "SE":
                            // 东南半球：经度递增，纬度递减
                            lng = encodedLng;
                            lat = 7 - encodedLat;
                            break;
                        default:
                            // 默认使用东北半球规则
                            lng = encodedLng;
                            lat = encodedLat;
                    }
                } else {
                    // 如果没有提供完整的编码，默认使用东北半球规则
                    lng = encodedLng;
                    lat = encodedLat;
                }
                break;

            case 4:
            case 5:
                // 四级和五级网格需要根据半球信息进行方向转换
                int encodedLng45 = Integer.parseInt(codeFragment.substring(0, 1), 16);
                int encodedLat45 = Integer.parseInt(codeFragment.substring(1, 2), 16);

                if (code != null) {
                    String hemisphere45 = getHemisphereFromCode(code);
                    switch (hemisphere45) {
                        case "NW":
                            // 西北半球：经度递减，纬度递增
                            lng = 14 - encodedLng45;
                            lat = encodedLat45;
                            break;
                        case "NE":
                            // 东北半球：经度递增，纬度递增
                            lng = encodedLng45;
                            lat = encodedLat45;
                            break;
                        case "SW":
                            // 西南半球：经度递减，纬度递减
                            lng = 14 - encodedLng45;
                            lat = 14 - encodedLat45;
                            break;
                        case "SE":
                            // 东南半球：经度递增，纬度递减
                            lng = encodedLng45;
                            lat = 14 - encodedLat45;
                            break;
                        default:
                            // 默认使用东北半球规则
                            lng = encodedLng45;
                            lat = encodedLat45;
                    }
                } else {
                    // 如果没有提供完整的编码，默认使用东北半球规则
                    lng = encodedLng45;
                    lat = encodedLat45;
                }
                break;

            case 7:
            case 8:
            case 9:
            case 10:
                // 七到十级网格使用十六进制编码
                lng = Integer.parseInt(codeFragment.substring(0, 1), 16);
                lat = Integer.parseInt(codeFragment.substring(1, 2), 16);
                break;
            case 3: {
                int n = Integer.parseInt(codeFragment);
                // 三级网格使用Z序编码
                int[] indices = new int[2];

                if (code != null) {
                    String hemisphere = getHemisphereFromCode(code);

                    // 使用映射表查找Z序编码对应的索引
                    int[][] encodingMap = getLevel3EncodingMap(hemisphere);
                    boolean found = false;

                    for (int i = 0; i < encodingMap.length && !found; i++) {
                        for (int j = 0; j < encodingMap[i].length; j++) {
                            if (encodingMap[i][j] == n) {
                                indices[0] = j;  // 经度索引
                                indices[1] = i;  // 纬度索引
                                found = true;
                                break;
                            }
                        }
                    }

                    if (!found) {
                        throw new IllegalArgumentException("无效的三级网格编码: " + n);
                    }
                } else {
                    // 默认使用东北半球规则
                    if (n <= 1) {
                        indices[0] = n;
                        indices[1] = 0;
                    } else if (n <= 3) {
                        indices[0] = n - 2;
                        indices[1] = 1;
                    } else {
                        indices[0] = n - 4;
                        indices[1] = 2;
                    }
                }

                lng = indices[0];
                lat = indices[1];
                break;
            }
            case 6: {
                int n = Integer.parseInt(codeFragment);
                // 六级网格使用Z序编码
                int[] indices = new int[2];

                if (code != null) {
                    String hemisphere = getHemisphereFromCode(code);

                    // 使用映射表查找Z序编码对应的索引
                    int[][] encodingMap = getLevel6EncodingMap(hemisphere);
                    boolean found = false;

                    for (int i = 0; i < encodingMap.length && !found; i++) {
                        for (int j = 0; j < encodingMap[i].length; j++) {
                            if (encodingMap[i][j] == n) {
                                indices[0] = j;  // 经度索引
                                indices[1] = i;  // 纬度索引
                                found = true;
                                break;
                            }
                        }
                    }

                    if (!found) {
                        throw new IllegalArgumentException("无效的六级网格编码: " + n);
                    }
                } else {
                    // 默认使用东北半球规则
                    if (n <= 1) {
                        indices[0] = n;
                        indices[1] = 0;
                    } else {
                        indices[0] = n - 2;
                        indices[1] = 1;
                    }
                }

                lng = indices[0];
                lat = indices[1];
                break;
            }
            default:
                throw new IllegalArgumentException("不支持的层级: " + level);
        }

        return new int[]{lng, lat};
    }

    /**
     * 对一个经纬度坐标和高度进行三维编码（高度部分）
     *
     * @param altitude 高度（单位：米）
     * @param level    要编码到第几级
     * @return 北斗三维网格位置码的高度部分
     */
    public static String encode3DAltitude(double altitude, Integer level) {

        // 方向编码
        int i0 = altitude >= 0 ? 0 : 1;

        // 返回结果
        StringBuilder altitudeCode = new StringBuilder().append(i0);

        // 获取绝对值高度进行计算
        double absAltitude = Math.abs(altitude);

        // 循环计算每一级的高度编码
        double remainingAltitude = absAltitude;
        for (int i = 1; i <= level; i++) {
            // 获取当前层级的网格长度
            double gridSize = GRID_SIZES_3D[i];

            // 计算当前层级的高度索引
            int altitudeIndex = (int) Math.floor(remainingAltitude / gridSize);

            // 根据不同层级采用不同的编码方式
            String levelCode;
            switch (i) {
                case 1:
                    // 第一级返回00-63
                    levelCode = String.format("%02d", altitudeIndex);
                    break;
                case 2:
                case 3:
                case 6:
                case 7:
                case 8:
                case 9:
                case 10:
                    // 2-3级和6-10级直接返回数字编码
                    levelCode = String.valueOf(altitudeIndex);
                    break;
                case 4:
                case 5:
                    // 4-5级使用十六进制编码
                    levelCode = Integer.toHexString(altitudeIndex).toUpperCase();
                    break;
                default:
                    levelCode = Integer.toHexString(altitudeIndex).toUpperCase();
                    break;
            }

            altitudeCode.append(levelCode);

            // 更新剩余高度用于下一级计算
            remainingAltitude -= altitudeIndex * gridSize;
        }

        return altitudeCode.toString();
    }

    /**
     * 对一个经纬度坐标和高度进行三维编码（完整三维编码）
     *
     * @param GeoPoint 经纬度坐标
     * @param altitude 高度（单位：米）
     * @param level    要编码到第几级
     * @return 北斗三维网格位置码
     */
    public static String encode3D(GeoPoint GeoPoint, double altitude, Integer level) {
        // 先获取二维编码
        String code2D = encode2D(GeoPoint, level);

        // 获取高度编码（不包括方向位）
        String altitudeCode = encode3DAltitude(altitude, level);

        // 按照北斗网格标准，将高度编码逐级插入到二维编码中
        StringBuilder result = new StringBuilder();
        result.append(code2D.charAt(0)); // 先添加方向位 N/S
        result.append(altitudeCode.charAt(0)); // 在添加高度方向位 0 1

        // 逐级处理编码，将每级的高度编码插入到对应级别的二维编码后面
        int code2DIndex = 1; // 当前二维编码处理位置（跳过方向位）
        int altitudeIndex = 1; // 当前高度编码处理位置（跳过方向位）

        for (int i = 1; i <= level; i++) {
            // 计算当前级别的二维编码长度
            int level2DLength = CODE_LENGTH_AT_LEVEL[i] - CODE_LENGTH_AT_LEVEL[i - 1];

            // 添加该级别的二维编码
            result.append(code2D.substring(code2DIndex, code2DIndex + level2DLength));
            code2DIndex += level2DLength;

            // 添加该级别的高度编码
            if (i == 1) {
                // 第一级高度编码是两位数字
                result.append(altitudeCode.substring(altitudeIndex, altitudeIndex + 2));
                altitudeIndex += 2;
            } else {
                // 其他级别高度编码是一位数字
                result.append(altitudeCode.charAt(altitudeIndex));
                altitudeIndex++;
            }
        }

        return result.toString();
    }

    /**
     * 根据半球信息调整索引
     *
     * @param lng        经度索引
     * @param lat        纬度索引
     * @param hemisphere 半球信息
     * @param maxLng     经度最大值
     * @param maxLat     纬度最大值
     * @return 调整后的索引数组 [经度索引, 纬度索引]
     */
    private static int[] adjustIndices(int lng, int lat, String hemisphere, int maxLng, int maxLat) {
        switch (hemisphere) {
            case "NW":
                // 西北半球：经度递减，纬度递增
                return new int[]{maxLng - lng, lat};
            case "NE":
                // 东北半球：经度递增，纬度递增
                return new int[]{lng, lat};
            case "SW":
                // 西南半球：经度递减，纬度递减
                return new int[]{maxLng - lng, maxLat - lat};
            case "SE":
                // 东南半球：经度递增，纬度递减
                return new int[]{lng, maxLat - lat};
            default:
                // 默认使用东北半球规则
                return new int[]{lng, lat};
        }
    }

    /**
     * 对北斗三维网格位置码解码
     *
     * @param code 需要解码的北斗三维网格位置码
     * @return 经纬度坐标和高度信息
     * @throws IllegalArgumentException 如果位置码格式无效
     */
    public static Map<String, Object> decode3D(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("位置码不能为空");
        }

        // 获取层级
        int level = getCodeLevel3D(code);

        // 创建结果对象
        Map<String, Object> result = new HashMap<>();

        // 提取二维编码部分
        String code2D = extract2DCode(code, level);

        // 对二维部分进行解码
        GeoPoint geoPoint = decode2D(code2D);

        // 提取高度部分并解码
        double altitude = decode3DAltitude(code, level);
        geoPoint.setAltitude(altitude);
        result.put("geoPoint", geoPoint);

        return result;
    }

    /**
     * 从三维编码中提取二维编码部分
     *
     * @param code3D 三维编码
     * @param level  层级
     * @return 二维编码
     */
    private static String extract2DCode(String code3D, int level) {
        StringBuilder code2D = new StringBuilder();
        code2D.append(code3D.charAt(0)); // 添加方向位

        int code3DIndex = 2; // 跳过方向位和高度方向位

        for (int i = 1; i <= level; i++) {
            // 计算当前级别的二维编码长度
            int level2DLength = CODE_LENGTH_AT_LEVEL[i] - CODE_LENGTH_AT_LEVEL[i - 1];

            // 添加该级别的二维编码（跳过高度编码部分）
            code2D.append(code3D.substring(code3DIndex, code3DIndex + level2DLength));
            code3DIndex += level2DLength;

            // 跳过该级别的高度编码
            if (i == 1) {
                // 第一级高度编码是两位数字
                code3DIndex += 2;
            } else {
                // 其他级别高度编码是一位数字
                code3DIndex += 1;
            }
        }

        return code2D.toString();
    }

    /**
     * 从三维编码中解码高度信息
     *
     * @param code  三维编码
     * @param level 层级
     * @return 高度值（单位：米）
     */
    private static double decode3DAltitude(String code, int level) {
        // 获取高度方向
        int altitudeSign = code.charAt(1) == '0' ? 1 : -1;

        double altitude = 0;
        int codeIndex = 2; // 跳过方向位和高度方向位

        // 跳过二维编码部分，定位到高度编码部分
        for (int i = 1; i <= level; i++) {
            // 跳过二维编码
            int level2DLength = CODE_LENGTH_AT_LEVEL[i] - CODE_LENGTH_AT_LEVEL[i - 1];
            codeIndex += level2DLength;

            // 解码该级别的高度编码
            if (i == 1) {
                // 第一级高度是两位数字
                int altitudeIndex = Integer.parseInt(code.substring(codeIndex, codeIndex + 2));
                altitude += altitudeIndex * GRID_SIZES_3D[i];
                codeIndex += 2;
            } else {
                // 其他级别高度是一位数字或十六进制
                int altitudeIndex;
                if (i == 4 || i == 5) {
                    // 4、5级是十六进制
                    altitudeIndex = Integer.parseInt(code.substring(codeIndex, codeIndex + 1), 16);
                } else {
                    // 其他是十进制
                    altitudeIndex = Integer.parseInt(code.substring(codeIndex, codeIndex + 1));
                }
                altitude += altitudeIndex * GRID_SIZES_3D[i];
                codeIndex += 1;
            }
        }

        return altitude * altitudeSign;
    }

    /**
     * 获取三维位置码的层级
     */
    private static int getCodeLevel3D(String code) {
        // 三维编码长度计算：方向位(1) + 高度方向位(1) + 各级二维编码 + 各级高度编码
        // 第一级高度编码是2位，其余是1位
        int length = code.length();

        for (int level = 1; level <= 10; level++) {
            int expectedLength = 2; // 方向位 + 高度方向位
            for (int i = 1; i <= level; i++) {
                expectedLength += (CODE_LENGTH_AT_LEVEL[i] - CODE_LENGTH_AT_LEVEL[i - 1]); // 二维编码长度
                expectedLength += (i == 1) ? 2 : 1; // 高度编码长度
            }
            if (expectedLength == length) {
                return level;
            }
        }
        throw new IllegalArgumentException("无效的三维位置码长度: " + code);
    }


}
