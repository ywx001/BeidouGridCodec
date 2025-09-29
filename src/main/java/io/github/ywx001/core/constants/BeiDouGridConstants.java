package io.github.ywx001.core.constants;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 北斗网格码常量定义类
 * 集中管理所有网格相关的常量
 */
public class BeiDouGridConstants {

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

    /**
     * 网格大小数据（单位：秒）
     */
    public static final double[][] GRID_SIZES_SECONDS = {
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

    /**
     * 各级网格编码长度
     */
    public static final int[] CODE_LENGTH_AT_LEVEL = {
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

    /**
     * 赤道周长（单位：米）
     */
    public static final double EARTH_EQUATOR_CIRCUMFERENCE = 40075000.0;

    /**
     * 地球半径（单位：米）
     */
    public static final double EARTH_RADIUS = 6378137;

    /**
     * 三维网格长度数据（单位：米）
     * 根据赤道周长和各级网格的角度划分计算得出
     */
    public static final double[] GRID_SIZES_3D = calculateGridSizes3D();

    /**
     * 各级网格的高度编码位数和基数
     */
    public static final int[][] ELEVATION_ENCODING = {
            {0, 0},    // 0级（占位）
            {6, 10},   // 1级：6位，10进制
            {3, 8},    // 2级：3位，8进制
            {1, 2},    // 3级：1位，2进制
            {4, 16},   // 4级：4位，16进制
            {4, 16},   // 5级：4位，16进制
            {1, 2},    // 6级：1位，2进制
            {3, 8},    // 7级：3位，8进制
            {3, 8},    // 8级：3位，8进制
            {3, 8},    // 9级：3位，8进制
            {3, 8}     // 10级：3位，8进制
    };

    /**
     * 32位整数n中各级别编码的位位置（按照GB/T 39409-2020标准附录C）
     * 从低位到高位的位范围：[起始位, 结束位]
     */
    public static final int[][] HEIGHT_BIT_RANGES = {
            {32, 32},   // a0: 第32位
            {26, 31},   // a1a2: 第26-31位
            {23, 25},   // a3: 第23-25位
            {22, 22},   // a4: 第22位
            {18, 21},   // a5: 第18-21位
            {14, 17},   // a6: 第14-17位
            {13, 13},   // a7: 第13位
            {10, 12},   // a8: 第10-12位
            {7, 9},     // a9: 第7-9位
            {4, 6},     // a10: 第4-6位
            {1, 3}      // a11: 第1-3位
    };

    /**
     * 创建BigDecimal对象的辅助方法
     * 使用String构造器以避免精度问题
     */
    private static BigDecimal bd(double val) {
        return new BigDecimal(String.valueOf(val));
    }

    /**
     * 计算各级网格的长度
     * 根据赤道周长和各级网格的角度划分计算
     *
     * @return 各级网格长度数组
     */
    private static double[] calculateGridSizes3D() {
        double[] sizes = new double[11];

        // 0级长度为0
        sizes[0] = 0;

        // 第一级网格：4°
        sizes[1] = EARTH_EQUATOR_CIRCUMFERENCE / 360.0 * 4.0;

        // 第二级网格：30′
        sizes[2] = EARTH_EQUATOR_CIRCUMFERENCE / 360.0 * (30.0 / 60.0);

        // 第三级网格：15′
        sizes[3] = EARTH_EQUATOR_CIRCUMFERENCE / 360.0 * (15.0 / 60.0);

        // 第四级网格：1′
        sizes[4] = EARTH_EQUATOR_CIRCUMFERENCE / 360.0 * (1.0 / 60.0);

        // 第五级网格：4″
        sizes[5] = EARTH_EQUATOR_CIRCUMFERENCE / 360.0 * (4.0 / 3600.0);

        // 第六级网格：2″
        sizes[6] = EARTH_EQUATOR_CIRCUMFERENCE / 360.0 * (2.0 / 3600.0);

        // 第七级网格：1/4″
        sizes[7] = EARTH_EQUATOR_CIRCUMFERENCE / 360.0 * (0.25 / 3600.0);

        // 第八级网格：1/32″
        sizes[8] = EARTH_EQUATOR_CIRCUMFERENCE / 360.0 * (1.0 / 32.0 / 3600.0);

        // 第九级网格：1/256″
        sizes[9] = EARTH_EQUATOR_CIRCUMFERENCE / 360.0 * (1.0 / 256.0 / 3600.0);

        // 第十级网格：1/2048″
        sizes[10] = EARTH_EQUATOR_CIRCUMFERENCE / 360.0 * (1.0 / 2048.0 / 3600.0);

        return sizes;
    }

    /**
     * 经度方向枚举
     */
    public enum LngDirection {
        /**
         * 东经
         */
        E,
        /**
         * 西经
         */
        W
    }

    /**
     * 纬度方向枚举
     */
    public enum LatDirection {
        /**
         * 北纬
         */
        N,
        /**
         * 南纬
         */
        S
    }
}
