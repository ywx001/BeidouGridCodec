package io.github.ywx001.core.decoder;

import io.github.ywx001.core.constants.BeiDouGridConstants;
import io.github.ywx001.core.model.BeiDouGeoPoint;
import io.github.ywx001.core.common.BeiDouGridCommonUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 北斗网格码解码器接口
 * 定义所有解码相关的操作
 */
public class BeiDouGridDecoder {

    // 缓存编码映射表，避免重复创建
    private static final Map<String, int[][]> LEVEL3_ENCODING_MAP_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, int[][]> LEVEL6_ENCODING_MAP_CACHE = new ConcurrentHashMap<>();

    /**
     * 解码二维网格编码为地理点
     *
     * @param code 二维网格编码
     * @return 解码后的地理点对象（所在网格左下角点）
     */
    public BeiDouGeoPoint decode2D(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("位置码不能为空");
        }

        int level = getCodeLevel2D(code);
        Map<String, String> directions = getDirections(code);
        BeiDouGridConstants.LngDirection lngDir = BeiDouGridConstants.LngDirection.valueOf(directions.get("lngDirection"));
        BeiDouGridConstants.LatDirection latDir = BeiDouGridConstants.LatDirection.valueOf(directions.get("latDirection"));

        int lngSign = lngDir == BeiDouGridConstants.LngDirection.E ? 1 : -1;
        int latSign = latDir == BeiDouGridConstants.LatDirection.N ? 1 : -1;

        double lngInSec = 0;
        double latInSec = 0;

        for (int i = 1; i <= level; i++) {
            double[] offsets = decodeN(code, i);
            lngInSec += offsets[0];
            latInSec += offsets[1];
        }

        return BeiDouGeoPoint.builder()
                .longitude((lngInSec * lngSign) / 3600)
                .latitude((latInSec * latSign) / 3600)
                .build();
    }

    /**
     * 解码三维网格编码为包含地理点和高度信息的 Map
     *
     * @param code 三维网格编码
     * @return 包含地理点和高度信息的 Map
     */
    public Map<String, Object> decode3D(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("位置码不能为空");
        }

        int level = getCodeLevel3D(code);
        Map<String, Object> result = new HashMap<>();

        String code2D = extract2DCode(code, level);
        BeiDouGeoPoint beiDouGeoPoint = decode2D(code2D);
        double altitude = decode3DAltitude(code, level);

        beiDouGeoPoint.setAltitude(altitude);
        result.put("geoPoint", beiDouGeoPoint);

        return result;
    }

    /**
     * 获取二维网格码的层级
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
     */
    private Map<String, String> getDirections(String code) {
        Map<String, String> directions = new HashMap<>(2);
        directions.put("latDirection", code.charAt(0) == 'N' ? "N" : "S");
        int lngPart = Integer.parseInt(code.substring(1, 3));
        directions.put("lngDirection", lngPart >= 31 ? "E" : "W");
        return directions;
    }

    /**
     * 解码第n级网格码
     */
    private double[] decodeN(String code, int n) {
        if (n < 1 || n > 10) {
            throw new IllegalArgumentException("层级错误: " + n);
        }

        String fragment = getCodeFragment(code, n);
        int[] rowCol = getRowAndCol(fragment, n, code);

        int lng = rowCol[0];
        int lat = rowCol[1];

        if (n == 1) {
            if (lng == 0) {
                throw new IllegalArgumentException("暂不支持两极地区解码");
            }
            lng = lng >= 31 ? lng - 31 : 30 - lng;
        }

        double lngOffset = lng * BeiDouGridConstants.GRID_SIZES_SECONDS[n][0];
        double latOffset = lat * BeiDouGridConstants.GRID_SIZES_SECONDS[n][1];

        return new double[]{lngOffset, latOffset};
    }

    /**
     * 获取某一层级的位置码片段
     */
    private String getCodeFragment(String code, int level) {
        if (level == 0) {
            return String.valueOf(code.charAt(0));
        }
        int start = BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[level - 1];
        int end = BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[level];
        return code.substring(start, end);
    }

    /**
     * 解析行列号
     */
    private int[] getRowAndCol(String codeFragment, int level, String code) {
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
     */
    private int decodeLevel2(String codeFragment, String code, boolean isLng) {
        int index = isLng ? 0 : 1;
        int encoded = Integer.parseInt(codeFragment.substring(index, index + 1), 16);
        if (code != null) {
            String hemisphere = BeiDouGridCommonUtils.getHemisphereFromCode(code);
            return switch (hemisphere) {
                case "NE", "NW" -> encoded;
                case "SE", "SW" -> isLng ? 11 - encoded : 7 - encoded;
                default -> encoded;
            };
        }
        return encoded;
    }

    /**
     * 解码四级/五级网格
     */
    private int decodeLevel4_5(String codeFragment, String code, boolean isLng) {
        int index = isLng ? 0 : 1;
        int encoded = Integer.parseInt(codeFragment.substring(index, index + 1), 16);
        if (code != null) {
            String hemisphere = BeiDouGridCommonUtils.getHemisphereFromCode(code);
            return switch (hemisphere) {
                case "NE", "NW" -> encoded;
                case "SE", "SW" -> 14 - encoded;
                default -> encoded;
            };
        }
        return encoded;
    }

    /**
     * 解码三级网格
     */
    private int[] decodeLevel3(String codeFragment, String code) {
        int n = Integer.parseInt(codeFragment);
        int[] indices = new int[2];

        if (code != null) {
            String hemisphere = BeiDouGridCommonUtils.getHemisphereFromCode(code);
            int[][] encodingMap = getLevel3EncodingMap(hemisphere);
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
     */
    private int[] decodeLevel6(String codeFragment, String code) {
        int n = Integer.parseInt(codeFragment);
        int[] indices = new int[2];

        if (code != null) {
            String hemisphere = BeiDouGridCommonUtils.getHemisphereFromCode(code);
            int[][] encodingMap = getLevel6EncodingMap(hemisphere);
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
     */
    public String extract2DCode(String code3D, int level) {
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
     */
    private double decode3DAltitude(String code, int level) {
        int altitudeSign = code.charAt(1) == '0' ? 1 : -1;
        double altitude = 0;
        int codeIndex = 2;

        for (int i = 1; i <= level; i++) {
            int level2DLength = BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i] - BeiDouGridConstants.CODE_LENGTH_AT_LEVEL[i - 1];
            codeIndex += level2DLength;

            if (i == 1) {
                int altitudeIndex = Integer.parseInt(code.substring(codeIndex, codeIndex + 2));
                altitude += altitudeIndex * BeiDouGridConstants.GRID_SIZES_3D[i];
                codeIndex += 2;
            } else {
                int altitudeIndex;
                if (i == 4 || i == 5) {
                    altitudeIndex = Integer.parseInt(code.substring(codeIndex, codeIndex + 1), 16);
                } else {
                    altitudeIndex = Integer.parseInt(code.substring(codeIndex, codeIndex + 1));
                }
                altitude += altitudeIndex * BeiDouGridConstants.GRID_SIZES_3D[i];
                codeIndex += 1;
            }
        }

        return altitude * altitudeSign;
    }

    /**
     * 获取三级网格编码映射表
     */
    private int[][] getLevel3EncodingMap(String hemisphere) {
        return LEVEL3_ENCODING_MAP_CACHE.computeIfAbsent(hemisphere, key -> switch (key) {
            case "NW" -> new int[][]{{1, 0}, {3, 2}, {5, 4}};
            case "NE" -> new int[][]{{0, 1}, {2, 3}, {4, 5}};
            case "SW" -> new int[][]{{5, 4}, {3, 2}, {1, 0}};
            case "SE" -> new int[][]{{4, 5}, {2, 3}, {0, 1}};
            default -> new int[][]{{0, 1}, {2, 3}, {4, 5}};
        });
    }

    /**
     * 获取六级网格编码映射表
     */
    private int[][] getLevel6EncodingMap(String hemisphere) {
        return LEVEL6_ENCODING_MAP_CACHE.computeIfAbsent(hemisphere, key -> switch (key) {
            case "NW" -> new int[][]{{1, 0}, {3, 2}};
            case "NE" -> new int[][]{{0, 1}, {2, 3}};
            case "SW" -> new int[][]{{3, 2}, {1, 0}};
            case "SE" -> new int[][]{{2, 3}, {0, 1}};
            default -> new int[][]{{0, 1}, {2, 3}};
        });
    }
}
