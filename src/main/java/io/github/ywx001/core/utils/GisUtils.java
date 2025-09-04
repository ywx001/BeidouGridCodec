package io.github.ywx001.core.utils;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.ArrayList;
import java.util.List;

import static io.github.ywx001.core.constants.BeiDouGridConstants.EARTH_RADIUS;

public class GisUtils {
    /**
     * 将几何类型为LineString的对象根据距离填充点
     *
     * @param originalLine 初始几何对象
     * @param distance 多少米距离填充一个点
     * @return 填充了点的线
     */
    public static Geometry lineFillPoints(Geometry originalLine, double distance) {
        if (originalLine == null) {
            return null;
        }
        GeometryFactory geometryFactory = new GeometryFactory();
        if (Geometry.TYPENAME_LINESTRING.equals(originalLine.getGeometryType())) {
            Coordinate[] coordinates = originalLine.getCoordinates();
            List<Coordinate> resultCoordinates = new ArrayList<>();
            for (int i = 0; i < coordinates.length - 1; i++) {
                if (Double.isNaN(coordinates[i].z)) {
                    coordinates[i].z = 0;
                }
                if (Double.isNaN(coordinates[i + 1].z)) {
                    coordinates[i + 1].z = 0;
                }
                double actualDistance = calculateDistance(coordinates[i].x, coordinates[i].y, coordinates[i].z,
                        coordinates[i + 1].x, coordinates[i + 1].y, coordinates[i + 1].z);
                //两点之间需要新生成的点的数量
                int pointCount = 0;
                if (actualDistance % distance == 0) {
                    pointCount = (int) (actualDistance / distance) - 1;
                } else {
                    pointCount = (int) (actualDistance / distance);
                }

                resultCoordinates.add(coordinates[i]);

                for (int j = 1; j <= pointCount; j++) {
                    double newPointX = coordinates[i].x + ((coordinates[i + 1].x - coordinates[i].x) * j) / (pointCount + 1);
                    double newPointY = coordinates[i].y + ((coordinates[i + 1].y - coordinates[i].y) * j) / (pointCount + 1);
                    double newPointZ = coordinates[i].z + ((coordinates[i + 1].z - coordinates[i].z) * j) / (pointCount + 1);
                    resultCoordinates.add(new Coordinate(newPointX, newPointY, newPointZ));
                }
            }
            resultCoordinates.add(coordinates[coordinates.length - 1]);
            return geometryFactory.createLineString(resultCoordinates.toArray(new Coordinate[0]));
        }
        return originalLine;
    }
    // 计算两个经纬高之间的距离，高度的单位是米
    private static double calculateDistance(double lon1, double lat1, Double h1, double lon2, double lat2, Double h2) {
        // 将经纬度转换为弧度
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);

        // 转换为三维直角坐标
        double x1 = (EARTH_RADIUS + h1) * Math.cos(lat1Rad) * Math.cos(lon1Rad);
        double y1 = (EARTH_RADIUS + h1) * Math.cos(lat1Rad) * Math.sin(lon1Rad);
        double z1 = (EARTH_RADIUS + h1) * Math.sin(lat1Rad);

        double x2 = (EARTH_RADIUS + h2) * Math.cos(lat2Rad) * Math.cos(lon2Rad);
        double y2 = (EARTH_RADIUS + h2) * Math.cos(lat2Rad) * Math.sin(lon2Rad);
        double z2 = (EARTH_RADIUS + h2) * Math.sin(lat2Rad);

        // 计算坐标差
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;

        // 返回欧几里得距离,单位为米
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
