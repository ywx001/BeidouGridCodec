package io.github.ywx001.core.utils;


import io.github.ywx001.core.constants.BeiDouGridConstants;
import io.github.ywx001.core.decoder.BeiDouGridDecoder;
import io.github.ywx001.core.encoder.BeiDouGridEncoder;
import io.github.ywx001.core.model.BeiDouGeoPoint;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 北斗网格码工具类
 * 提供公共接口，委托具体的编码和解码工作给专门的类
 */
@Slf4j
public class BeiDouGridUtils {

    // 编码器和解码器实例
    private static final BeiDouGridEncoder encoder = new BeiDouGridEncoder();
    private static final BeiDouGridDecoder decoder = new BeiDouGridDecoder();

    /**
     * 对一个经纬度坐标进行二维编码
     *
     * @param point 经纬度坐标，使用小数形式（正负号表示方向）
     * @param level 要编码到第几级，范围1-10
     * @return 北斗二维网格位置码
     */
    public static String encode2D(BeiDouGeoPoint point, Integer level) {
        return encoder.encode2D(point, level);
    }

    /**
     * 对高度部分进行三维编码
     *
     * @param altitude 高度（单位：米）
     * @param level    要编码到第几级
     * @return 北斗三维网格位置码的高度部分
     */
    public static String encode3DAltitude(double altitude, Integer level) {
        return encoder.encode3DAltitude(altitude, level);
    }

    /**
     * 对一个经纬度坐标和高度进行三维编码（完整三维编码）
     *
     * @param point    经纬度坐标
     * @param altitude 高度（单位：米）
     * @param level    要编码到第几级
     * @return 北斗三维网格位置码
     */
    public static String encode3D(BeiDouGeoPoint point, double altitude, Integer level) {
        return encoder.encode3D(point, altitude, level);
    }

    /**
     * 对北斗二维网格位置码解码
     *
     * @param code 需要解码的北斗二维网格位置码
     * @return 经纬度坐标
     * @throws IllegalArgumentException 如果位置码格式无效
     */
    public static BeiDouGeoPoint decode2D(String code) {
        return decoder.decode2D(code);
    }

    /**
     * 对北斗三维网格位置码解码
     *
     * @param code 需要解码的北斗三维网格位置码
     * @return 经纬度坐标和高度信息
     * @throws IllegalArgumentException 如果位置码格式无效
     */
    public static Map<String, Object> decode3D(String code) {
        return decoder.decode3D(code);
    }

    /**
     * 获取经纬度坐标所在的半球信息（用于网格码方向转换）
     * 半球表示形式为：纬度方向(N/S) + 经度方向(E/W)，例如："NE"表示北半球东经区域
     *
     * @param point 经纬度坐标对象，包含经度和纬度字段
     * @return 半球标识字符串（格式：{N|S}{E|W}）
     * @throws IllegalArgumentException 如果经纬度参数无效（为空或非数字）
     */
    public static String getHemisphere(BeiDouGeoPoint point) {
        return BeiDouGridConstants.getHemisphere(point);
    }
}
