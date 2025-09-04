package io.github.ywx001.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自封装三维坐标点，包含经度、纬度和高度属性
 * 经度范围：-180到180度
 * 纬度范围：-90到90度
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BeiDouGeoPoint {
    /**
     * 经度，有效范围：-180到180度
     */
    private double longitude;

    /**
     * 纬度，有效范围：-90到90度
     */
    private double latitude;

    /**
     * 大地高，单位：米
     */
    private double height;

    /**
     * 验证经纬度是否在有效范围内
     * @return 是否有效
     */
    public boolean isValid() {
        return longitude >= -180 && longitude <= 180 &&
               latitude >= -90 && latitude <= 90;
    }

    /**
     * 计算与另一点的距离（使用Haversine公式）
     * @param other 另一个地理点
     * @return 距离，单位：米
     */
    public double distanceTo(BeiDouGeoPoint other) {
        final int R = 6371000; // 地球半径，单位：米

        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);
        double lon1 = Math.toRadians(this.longitude);
        double lon2 = Math.toRadians(other.longitude);

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(lat1) * Math.cos(lat2) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        double distance = R * c;

        // 考虑高度差
        if (this.height != 0 || other.height != 0) {
            double heightDiff = this.height - other.height;
            distance = Math.sqrt(distance * distance + heightDiff * heightDiff);
        }

        return distance;
    }

    /**
     * 格式化输出经纬度信息
     * @return 格式化的字符串
     */
    @Override
    public String toString() {
        return String.format("GeoPoint(经度=%.6f°, 纬度=%.6f°, 高度=%.2f米)",
                             longitude, latitude, height);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null){
            return false;
        }
        if (!(obj instanceof BeiDouGeoPoint other)){
            return false;
        }
        return this.longitude == other.longitude &&
                this.latitude == other.latitude &&
                this.height == other.height;
    }
}
