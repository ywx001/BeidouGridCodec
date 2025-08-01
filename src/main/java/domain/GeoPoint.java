package domain;

import lombok.Builder;
import lombok.Data;

/**
 * 自封装三维坐标点，包含经度、纬度和高度属性
 */
@Data
@Builder
public class GeoPoint {
    /**
     * 经度
     */
    private double longitude;

    /**
     * 纬度
     */
    private double latitude;

    /**
     * 高度
     */
    private double altitude;

}
