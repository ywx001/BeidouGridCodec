package io.github.ywx001.core.common;

import io.github.ywx001.core.model.BeiDouGeoPoint;

/**
 * 北斗网格码通用工具类
 */
public class BeiDouGridCommonUtils {

    /**
     * 获取经纬度坐标所在的半球信息（用于网格码方向转换）
     * 半球表示形式为：纬度方向(N/S) + 经度方向(E/W)，例如："NE"表示北半球东经区域
     *
     * @param point 经纬度坐标对象，包含经度和纬度字段
     * @return 半球标识字符串（格式：{N|S}{E|W}）
     * @throws IllegalArgumentException 如果经纬度参数无效（为空或非数字）
     */
    public static String getHemisphere(BeiDouGeoPoint point) {
        // 参数校验：确保坐标对象不为空
        if (point == null) {
            throw new IllegalArgumentException("经纬度坐标对象不能为空");
        }

        // 获取并校验纬度值（范围：-90 ~ 90）
        double latitude = point.getLatitude();
        if (Double.isNaN(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("纬度值无效，应为-90到90之间的数值");
        }

        // 获取并校验经度值（范围：-180 ~ 180）
        double longitude = point.getLongitude();
        if (Double.isNaN(longitude) || longitude < -180 || longitude > 180) {
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
    public static String getHemisphereFromCode(String code) {
        if (code == null || code.length() < 3) {
            throw new IllegalArgumentException("无效的网格码格式");
        }

        String latDir = code.charAt(0) == 'N' ? "N" : "S";
        String lngDir = Integer.parseInt(code.substring(1, 3)) >= 31 ? "E" : "W";
        return latDir + lngDir;
    }

}
