package io.github.ywx001.core.decoder;

import io.github.ywx001.core.common.BeiDouGridCommonUtils;
import io.github.ywx001.core.constants.BeiDouGridConstants;
import io.github.ywx001.core.model.BeiDouGeoPoint;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 北斗网格码解码器接口
 * 定义所有解码相关的操作
 */
@Slf4j
public class BeiDouGridDecoder {

    /**
     * 解码二维网格编码为地理点
     *
     * @param code 二维网格编码
     * @return 解码后的地理点对象（所在网格左下角点）
     */
    public static BeiDouGeoPoint decode2D(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("位置码不能为空");
        }

        int level = getCodeLevel2D(code);
        Map<String, String> directions = getDirections(code);
        BeiDouGridConstants.LngDirection lngDir = BeiDouGridConstants.LngDirection.valueOf(directions.get("lngDirection"));
        BeiDouGridConstants.LatDirection latDir = BeiDouGridConstants.LatDirection.valueOf(directions.get("latDirection"));

        // 调试日志：记录方向判断结果
        log.debug("编码: {}, 经度方向: {}, 纬度方向: {}", code, lngDir, latDir);

        int lngSign = lngDir == BeiDouGridConstants.LngDirection.E ? 1 : -1;
        int latSign = latDir == BeiDouGridConstants.LatDirection.N ? 1 : -1;

        // 调试日志：记录符号
        log.debug("经度符号: {}, 纬度符号: {}", lngSign, latSign);

        double lngInSec = 0;
        double latInSec = 0;

        for (int i = 1; i <= level; i++) {
            double[] offsets = decodeN(code, i);
            lngInSec += offsets[0];
            latInSec += offsets[1];
        }

        // 调试日志：记录累计秒数
        log.debug("累计秒数 - 经度: {}秒, 纬度: {}秒", lngInSec, latInSec);

        double longitude = (lngInSec * lngSign) / 3600;
        double latitude = (latInSec * latSign) / 3600;

        // 调试日志：记录最终坐标
        log.debug("最终坐标 - 经度: {}°, 纬度: {}°", longitude, latitude);

        return BeiDouGeoPoint.builder()
                .longitude(longitude)
                .latitude(latitude)
                .build();
    }

    /**
     * 解码三维网格编码为包含地理点和高度信息的 Map
     *
     * @param code 三维网格编码
     * @return 包含地理点和高度信息的 Map
     */
    public static BeiDouGeoPoint decode3D(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("位置码不能为空");
        }

        int level = getCodeLevel3D(code);

        String code2D = extract2DCode(code, level);
        BeiDouGeoPoint beiDouGeoPoint = decode2D(code2D);
        double height = decode3DHeight(code, level);

        beiDouGeoPoint.setHeight(height);

        return beiDouGeoPoint;
    }

    /**
     * 获取二维网格码的层级
     *
     * <p>根据北斗网格编码标准，每个层级的网格码有固定的长度。</p>
     *
     * @param code 二维网格编码字符串
     * @return 网格层级（1-10）
     * @throws IllegalArgumentException 如果编码长度不符合任何已知层级
     * @see BeiDouGridConstants#CODE_LENGTH_AT_LEVEL 各级网格编码长度定义
     */
    public static int getCodeLevel2D(String code) {
        int length = code.length();
        for (int i = 0; i < BeiDouGridConstants.CODE_LENGTH_AT_LEVEL.length; i++) {
            if (BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i] == length) {
                return i;
            }
        }
        throw new IllegalArgumentException("无效的二维网格码长度: " + length);
    }

    /**
     * 获取三维网格码的层级
     *
     * <p>三维网格码包含二维编码部分和高度编码部分，总长度根据层级变化。</p>
     *
     * @param code 三维网格编码字符串
     * @return 网格层级（1-10）
     * @throws IllegalArgumentException 如果编码长度不符合任何已知层级
     * @see BeiDouGridConstants#CODE_LENGTH_AT_LEVEL 各级网格编码长度定义
     */
    public static int getCodeLevel3D(String code) {
        int length = code.length();
        for (int level = 1; level <= 10; level++) {
            int expectedLength = 2;
            for (int i = 1; i <= level; i++) {
                expectedLength += (BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i] - BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i - 1]);
                expectedLength += (i == 1) ? 2 : 1;
            }
            if (expectedLength == length) {
                return level;
            }
        }
        throw new IllegalArgumentException("无效的三维网格码码长度: " + code);
    }

    /**
     * 获取经纬度方向
     *
     * <p>根据网格码的首字符和经度部分判断经纬度方向：</p>
     * <ul>
     *   <li>纬度方向：首字符为'N'表示北纬，'S'表示南纬</li>
     *   <li>经度方向：前两位数字>=31表示东经，否则为西经</li>
     * </ul>
     *
     * @param code 网格编码字符串
     * @return 包含经纬度方向的Map，键为"lngDirection"和"latDirection"
     */
    private static Map<String, String> getDirections(String code) {
        Map<String, String> directions = new HashMap<>(2);

        // 纬度方向判断
        char latChar = code.charAt(0);
        String latDirection = latChar == 'N' ? "N" : "S";
        directions.put("latDirection", latDirection);

        // 经度方向判断
        int lngPart = Integer.parseInt(code.substring(1, 3));
        String lngDirection = lngPart >= 31 ? "E" : "W";
        directions.put("lngDirection", lngDirection);

        // 调试日志：记录方向判断过程
        log.debug("方向判断 - 编码首字符: {}, 纬度方向: {}, 经度部分: {}, 经度方向: {}",
                latChar, latDirection, lngPart, lngDirection);

        return directions;
    }

    /**
     * 解码第n级网格码
     *
     * <p>解析指定层级的网格编码片段，计算该层级对应的经纬度偏移量。</p>
     *
     * @param code 完整的网格编码字符串
     * @param n    要解码的层级（1-10）
     * @return 包含经度偏移量和纬度偏移量的数组，单位：秒
     * @throws IllegalArgumentException 如果层级不在有效范围内
     * @see #getCodeFragment(String, int) 获取层级片段
     * @see #getRowAndCol(String, int, String) 解析行列号
     */
    private static double[] decodeN(String code, int n) {
        if (n < 1 || n > 10) {
            throw new IllegalArgumentException("层级错误: " + n);
        }

        String fragment = getCodeFragment(code, n);
        int[] rowCol = getRowAndCol(fragment, n, code);

        int lng = rowCol[0];
        int lat = rowCol[1];

        // 调试日志：记录原始行列值
        log.debug("第{}级网格解码 - 片段: {}, 原始行列: lng={}, lat={}", n, fragment, lng, lat);

        if (n == 1) {
            if (lng == 0) {
                throw new IllegalArgumentException("暂不支持两极地区解码");
            }
            // 调试日志：记录第一级网格调整前的值
            log.debug("第一级网格调整前: lng={}, 判断条件: lng>=31?{}", lng, lng >= 31);
            lng = lng >= 31 ? lng - 31 : 30 - lng;
            // 调试日志：记录调整后的值
            log.debug("第一级网格调整后: lng={}", lng);
        }

        double lngOffset = lng * BeiDouGridConstants.GRID_SIZES_SECONDS[n][0];
        double latOffset = lat * BeiDouGridConstants.GRID_SIZES_SECONDS[n][1];

        // 调试日志：记录最终偏移量
        log.debug("第{}级网格偏移量: lngOffset={}秒, latOffset={}秒", n, lngOffset, latOffset);

        return new double[]{lngOffset, latOffset};
    }

    /**
     * 获取某一层级的位置码片段
     *
     * <p>根据层级索引从完整编码中提取对应的片段。</p>
     *
     * @param code  完整的网格编码字符串
     * @param level 要提取的层级（0-10）
     * @return 指定层级的编码片段
     * @see BeiDouGridConstants#CODE_LENGTH_AT_LEVEL 各级网格编码长度定义
     */
    private static String getCodeFragment(String code, int level) {
        if (level == 0) {
            return String.valueOf(code.charAt(0));
        }
        int start = BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[level - 1];
        int end = BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[level];
        return code.substring(start, end);
    }

    /**
     * 解析行列号
     *
     * <p>根据层级和编码片段解析出经度列索引和纬度行索引。</p>
     *
     * @param codeFragment 指定层级的编码片段
     * @param level        当前层级（1-10）
     * @param code         完整的网格编码（用于获取半球信息）
     * @return 包含经度列索引和纬度行索引的数组[经度索引, 纬度索引]
     * @throws IllegalArgumentException 如果编码片段长度不符合预期或层级不支持
     */
    private static int[] getRowAndCol(String codeFragment, int level, String code) {
        if (codeFragment.length() != (BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[level] - BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[level - 1])) {
            throw new IllegalArgumentException("编码片段长度错误: " + codeFragment);
        }

        int lng;
        int lat;

        switch (level) {
            case 1:
                lng = Integer.parseInt(codeFragment.substring(0, 2));
                lat = codeFragment.charAt(2) - 'A';
                break;
            case 2:
                lng = decodeLevel2(codeFragment, code, true);
                lat = decodeLevel2(codeFragment, code, false);
                break;
            case 4:
            case 5:
                lng = decodeLevel4_5(codeFragment, code, true);
                lat = decodeLevel4_5(codeFragment, code, false);
                break;
            case 7:
            case 8:
            case 9:
            case 10:
                lng = Integer.parseInt(codeFragment.substring(0, 1), 16);
                lat = Integer.parseInt(codeFragment.substring(1, 2), 16);
                break;
            case 3:
                int[] indices3 = decodeLevel3(codeFragment, code);
                lng = indices3[0];
                lat = indices3[1];
                break;
            case 6:
                int[] indices6 = decodeLevel6(codeFragment, code);
                lng = indices6[0];
                lat = indices6[1];
                break;
            default:
                throw new IllegalArgumentException("不支持的层级: " + level);
        }

        return new int[]{lng, lat};
    }

    /**
     * 解码二级网格
     *
     * <p>解析二级网格编码片段，返回经度或纬度的索引值。</p>
     *
     * @param codeFragment 二级网格编码片段
     * @param code         完整的网格编码（用于获取半球信息）
     * @param isLng        是否为经度方向（true表示经度，false表示纬度）
     * @return 经度或纬度的索引值
     */
    private static int decodeLevel2(String codeFragment, String code, boolean isLng) {
        int index = isLng ? 0 : 1;
        int encoded = Integer.parseInt(codeFragment.substring(index, index + 1), 16);
        if (code != null) {
            String hemisphere = BeiDouGridCommonUtils.getHemisphereFromCode(code);

            // 调试日志：记录二级网格解码过程
            log.debug("二级网格解码 - 片段: {}, 半球: {}, 经度: {}, 原始编码: {}",
                    codeFragment, hemisphere, isLng, encoded);

            return encoded;
        }
        return encoded;
    }

    /**
     * 解码四级/五级网格
     *
     * <p>解析四级或五级网格编码片段，返回经度或纬度的索引值。</p>
     *
     * @param codeFragment 四级或五级网格编码片段
     * @param code         完整的网格编码（用于获取半球信息）
     * @param isLng        是否为经度方向（true表示经度，false表示纬度）
     * @return 经度或纬度的索引值
     */
    private static int decodeLevel4_5(String codeFragment, String code, boolean isLng) {
        int index = isLng ? 0 : 1;
        int encoded = Integer.parseInt(codeFragment.substring(index, index + 1), 16);
        if (code != null) {
            String hemisphere = BeiDouGridCommonUtils.getHemisphereFromCode(code);

            // 调试日志：记录四级/五级网格解码过程
            log.debug("四级/五级网格解码 - 片段: {}, 半球: {}, 经度: {}, 原始编码: {}",
                    codeFragment, hemisphere, isLng, encoded);

            return encoded;
        }
        return encoded;
    }

    /**
     * 解码三级网格
     *
     * <p>解析三级网格编码片段，返回经度和纬度的索引值。</p>
     *
     * @param codeFragment 三级网格编码片段（1位数字）
     * @param code         完整的网格编码（用于获取半球信息）
     * @return 包含经度索引和纬度索引的数组[经度索引, 纬度索引]
     * @throws IllegalArgumentException 如果编码值无效
     */
    private static int[] decodeLevel3(String codeFragment, String code) {
        int n = Integer.parseInt(codeFragment);
        int[] indices = new int[2];

        if (code != null) {
            String hemisphere = BeiDouGridCommonUtils.getHemisphereFromCode(code);
            int[][] encodingMap = BeiDouGridConstants.getLevel3EncodingMap(hemisphere);
            boolean found = false;

            for (int i = 0; i < encodingMap.length && !found; i++) {
                for (int j = 0; j < encodingMap[i].length; j++) {
                    if (encodingMap[i][j] == n) {
                        indices[0] = j;
                        indices[1] = i;
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                throw new IllegalArgumentException("无效的三级网格编码: " + n);
            }

            // 根据半球信息进行调整
            indices = BeiDouGridCommonUtils.adjustCounts(indices[0], indices[1], hemisphere, 1, 2);
        } else {
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

        return indices;
    }

    /**
     * 解码六级网格
     *
     * <p>解析六级网格编码片段，返回经度和纬度的索引值。</p>
     *
     * @param codeFragment 六级网格编码片段（1位数字）
     * @param code         完整的网格编码（用于获取半球信息）
     * @return 包含经度索引和纬度索引的数组[经度索引, 纬度索引]
     * @throws IllegalArgumentException 如果编码值无效
     */
    private static int[] decodeLevel6(String codeFragment, String code) {
        int n = Integer.parseInt(codeFragment);
        int[] indices = new int[2];

        if (code != null) {
            String hemisphere = BeiDouGridCommonUtils.getHemisphereFromCode(code);
            int[][] encodingMap = BeiDouGridConstants.getLevel6EncodingMap(hemisphere);
            boolean found = false;

            for (int i = 0; i < encodingMap.length && !found; i++) {
                for (int j = 0; j < encodingMap[i].length; j++) {
                    if (encodingMap[i][j] == n) {
                        indices[0] = j;
                        indices[1] = i;
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                throw new IllegalArgumentException("无效的六级网格编码: " + n);
            }

            // 根据半球信息进行调整
            indices = BeiDouGridCommonUtils.adjustCounts(indices[0], indices[1], hemisphere, 1, 1);
        } else {
            if (n <= 1) {
                indices[0] = n;
                indices[1] = 0;
            } else {
                indices[0] = n - 2;
                indices[1] = 1;
            }
        }

        return indices;
    }

    /**
     * 从三维编码中提取二维编码部分
     *
     * <p>从三维网格编码中分离出二维地理编码部分。</p>
     *
     * @param code3D 三维网格编码字符串
     * @param level  三维网格层级（1-10）
     * @return 提取出的二维网格编码
     */
    public static String extract2DCode(String code3D, int level) {
        StringBuilder code2D = new StringBuilder();
        code2D.append(code3D.charAt(0));

        int code3DIndex = 2;

        for (int i = 1; i <= level; i++) {
            int level2DLength = BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i] - BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i - 1];
            code2D.append(code3D, code3DIndex, code3DIndex + level2DLength);
            code3DIndex += level2DLength;

            if (i == 1) {
                code3DIndex += 2;
            } else {
                code3DIndex += 1;
            }
        }

        return code2D.toString();
    }

    /**
     * 从三维编码中解码高度信息（网格底平面高度）
     *
     * <p>解析三维网格编码中的高度部分，计算网格底平面的海拔高度。</p>
     *
     * @param code  三维网格编码字符串
     * @param level 三维网格层级（1-10）
     * @return 网格底平面的海拔高度（米），正值表示地上，负值表示地下
     * @see BeiDouGridConstants#HEIGHT_BIT_RANGES 高度位范围定义
     * @see BeiDouGridConstants#ELEVATION_ENCODING 高度编码进制定义
     */
    private static double decode3DHeight(String code, int level) {
        // 高度方向符号：0表示地上，1表示地下
        int heightSign = code.charAt(1) == '0' ? 1 : -1;

        // 重建完整的高度索引n
        int n = 0;
        int codeIndex = 2;

        for (int i = 1; i <= level; i++) {
            // 跳过二维编码部分
            int level2DLength = BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i] - BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i - 1];
            codeIndex += level2DLength;

            // 获取当前级别的高度编码
            int heightCodeLength = (i == 1) ? 2 : 1;
            String heightCodeStr = code.substring(codeIndex, codeIndex + heightCodeLength);
            codeIndex += heightCodeLength;

            // 解析高度编码值
            int heightIndex;
            if (i == 1) {
                // 第一级特殊处理：2位十进制数表示6位二进制值
                heightIndex = Integer.parseInt(heightCodeStr, 10);
            } else {
                int radix = BeiDouGridConstants.ELEVATION_ENCODING[i][1];
                heightIndex = Integer.parseInt(heightCodeStr, radix);

            }

            // 按照标准将编码值放置到正确的位位置
            int[] bitRange = BeiDouGridConstants.HEIGHT_BIT_RANGES[i];
            int startBit = bitRange[0];

            // 将heightIndex的各位设置到n的相应位置（从第1位开始计数）
            for (int bit = 0; bit < (bitRange[1] - bitRange[0] + 1); bit++) {
                if (((heightIndex >> bit) & 1) == 1) {
                    n |= (1 << (startBit - 1 + bit));
                }
            }
        }

        // 使用标准逆公式计算高度：H = (1 + θ0)^(n*θ/θ0) * r0 - r0
        double theta = Math.PI / 180 / 60 / 60 / 2048;  // theta = π/180/3600/2048
        double theta0 = Math.PI / 180;                  // theta0 = π/180

        double height = Math.pow(1 + theta0, n * theta / theta0) * BeiDouGridConstants.EARTH_RADIUS - BeiDouGridConstants.EARTH_RADIUS;

        return height * heightSign;
    }

}
