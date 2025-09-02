package io.github.ywx001.core.utils;


import io.github.ywx001.core.constants.BeiDouGridConstants;
import io.github.ywx001.core.decoder.BeiDouGridDecoder;
import io.github.ywx001.core.encoder.BeiDouGridEncoder;
import io.github.ywx001.core.model.BeiDouGeoPoint;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;

import java.util.Map;
import java.util.Set;

/**
 * 北斗网格码工具类
 *
 * <p>提供北斗网格位置码的编码和解码功能，支持二维（经纬度）和三维（经纬度+高度）编码。</p>
 *
 * <p><b>主要功能：</b></p>
 * <ul>
 *   <li>二维编码：将经纬度坐标转换为北斗网格位置极</li>
 *   <li>三维编码：将经纬度坐标和高度信息转换为完整的三维网格位置码</li>
 *   <li>二维解码：将北斗网格位置码还原为经纬度坐标</li>
 *   <li>三维解码：将三维网格位置码还原为经纬度坐标和高度信息</li>
 *   <li>半球信息获取：获取坐标所在的半球区域</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 二维编码
 * BeiDouGeoPoint point = new BeiDouGeoPoint(116.3974, 39.9093, 0);
 * String gridCode = BeiDouGridUtils.encode2D(point, 6);
 *
 * // 二维解码
 * BeiDouGeoPoint decodedPoint = BeiDouGridUtils.decode2D(gridCode);
 *
 * // 三维编码
 * String full3DCode = BeiDouGridUtils.encode3D(point, 50.0, 6);
 *
 * // 获取半球信息
 * String hemisphere = BeiDouGridUtils.getHemisphere(point);
 * </pre>
 *
 * <p><b>网格级别说明：</b></p>
 * <p>支持1-10级网格编码，级别越高精度越高：</p>
 * <ul>
 *   <li>1级：6°×4°</li>
 *   <li>2级：3°×2°</li>
 *   <li>3级：1.5°×1°</li>
 *   <li>...逐级细分</li>
 *   <li>10级：最高精度级别</li>
 * </ul>
 *
 * @version 1.0
 * @see BeiDouGridEncoder 编码器实现
 * @see BeiDouGridDecoder 解码器实现
 * @see BeiDouGridConstants 网格常量定义
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
     * @param point 经纬高度坐标
     * @param level 要编码到第几级
     * @return 北斗三维网格位置码
     */
    public static String encode3D(BeiDouGeoPoint point, Integer level) {
        return encoder.encode3D(point, level);
    }

    /**
     * 对北斗二维网格位置码解码（所在网格西南角点，即左下角点）
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

    /**
     * 生成指定二维父网格的所有二维子网格集合
     *
     * 功能说明：
     * 1. 自动识别父网格的级别，并生成下一级别的子网格。
     * 2. 子网格的生成基于北斗网格编码规范，确保唯一性和正确性。
     *
     * 使用场景：
     * - 需要将父网格细化为更小粒度的子网格时（如地图分层展示、空间分析等）。
     *
     * @param parentGrid 父网格编码（格式示例：N50J475）
     *                  - 必须为有效的北斗二维网格码
     *                  - 编码级别需小于10（最高级网格无子网格）
     * @return 子网格集合（Set<String>）
     *         - 每个子网格的级别为父网格级别 + 1
     *         - 集合无序但保证唯一性
     *
     * @throws IllegalArgumentException 如果参数不合法：
     *         - parentGrid格式无效
     *         - parentGrid是10级网格（无子网格）
     *
     * @see BeiDouGrid2DRangeQuery#generateChildGrids2D 二维子网格生成实现
     */
    public static Set<String> getChildGrids2D(String parentGrid) {
        return BeiDouGrid2DRangeQuery.generateChildGrids2D(parentGrid);
    }

    /**
     * 生成指定三维父网格的所有三维子网格集合
     *
     * 功能说明：
     * 1. 自动识别父网格的级别，并生成下一级别的子网格（包括高度方向）。
     * 2. 子网格的生成基于北斗网格编码规范，确保唯一性和正确性。
     *
     * 使用场景：
     * - 需要将父网格细化为更小粒度的子网格时（如三维空间分析、高度分层等）。
     *
     * @param parentGrid 父网格编码（格式示例：N050J0047050）
     *                  - 必须为有效的北斗三维网格码
     *                  - 编码级别需小于10（最高级网格无子网格）
     * @return 子网格集合（Set<String>）
     *         - 每个子网格的级别为父网格级别 + 1
     *         - 集合无序但保证唯一性
     *
     * @throws IllegalArgumentException 如果参数不合法：
     *         - parentGrid格式无效
     *         - parentGrid是10级网格（无子网格）
     *
     * @see BeiDouGrid3DRangeQuery#generateChildGrids3D 三维子网格生成实现
     */
    public static Set<String> getChildGrids3D(String parentGrid) {
        return BeiDouGrid3DRangeQuery.generateChildGrids3D(parentGrid);
    }

    /**
     * 查询与几何图形相交的二维北斗网格码集合
     * <p>
     * 本方法是 {@link BeiDouGrid2DRangeQuery#find2DGridCodesInRange} 的便捷封装，
     * 用于根据几何图形获取相交的二维网格码集合。
     *
     * @param geometry    查询几何图形（支持点、线、多边形等JTS几何类型）
     * @param targetLevel 目标网格级别（1-10）
     * @return 与几何图形相交的所有指定级别二维网格码集合
     * @throws IllegalArgumentException 如果参数不合法（几何图形为空、级别越界或高度范围无效）
     * @see BeiDouGrid2DRangeQuery#find2DGridCodesInRange 实际执行二维查询的方法
     */
    public static Set<String> find2DIntersectingGridCodes(Geometry geometry, int targetLevel) {
        return BeiDouGrid2DRangeQuery.find2DGridCodesInRange(geometry, targetLevel);
    }

    /**
     * 查找与几何图形相交的三维网格码（指定高度范围）
     * <p>
     * 本方法是 {@link BeiDouGrid3DRangeQuery#find3DGridCodesInRange} 的便捷封装，
     * 用于根据几何图形和高度范围获取相交的三维网格码集合。
     *
     * @param geometry    几何图形对象（支持点、线、多边形等JTS几何类型）
     * @param targetLevel 目标网格级别（1-10）
     * @param minAltitude 最小海拔高度（单位：米）
     * @param maxAltitude 最大海拔高度（单位：米）
     * @return 与几何图形相交的所有指定级别三维网格码集合
     * @throws IllegalArgumentException 如果参数不合法（几何图形为空、级别越界或高度范围无效）
     * @see BeiDouGrid3DRangeQuery#find3DGridCodesInRange 实际执行三维查询的方法
     */
    public static Set<String> find3DIntersectingGridCodes(Geometry geometry, int targetLevel, double minAltitude, double maxAltitude) {
        return BeiDouGrid3DRangeQuery.find3DGridCodesInRange(geometry, targetLevel, minAltitude, maxAltitude);
    }

}
