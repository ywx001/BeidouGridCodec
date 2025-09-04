package io.github.ywx001.core.model;

import io.github.ywx001.core.constants.BeiDouGridConstants;
import io.github.ywx001.core.utils.BeiDouGridUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BeiDouGrid2D {
    /**
     * 网格等级
     */
    protected int level;
    /**
     * 网格最小经度
     */
    protected double minLongitude;
    /**
     * 网格最大经度
     */
    protected double maxLongitude;
    /**
     * 网格最小纬度
     */
    protected double minLatitude;
    /**
     * 网格最大纬度
     */
    protected double maxLatitude;
    /**
     * 网格编码
     */
    protected String code;

    public BeiDouGrid2D(int level, BeiDouGeoPoint point) {
        this.level = level;

        this.minLongitude = point.getLongitude();
        this.maxLongitude = point.getLongitude() + BeiDouGridConstants.GRID_SIZES_DEGREES[level][0].doubleValue();

        this.minLatitude = point.getLatitude();
        this.maxLatitude = point.getLatitude() + BeiDouGridConstants.GRID_SIZES_DEGREES[level][1].doubleValue();

        this.code = BeiDouGridUtils.encode2D(point, level);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null){
            return false;
        }
        if (!(obj instanceof BeiDouGrid2D other)){
            return false;
        }
        if (this.level != other.level){
            return false;
        }
        if (this.code != null && other.code != null){
            return this.code.equals(other.code);
        }
        return this.minLongitude == other.minLongitude &&
                this.maxLongitude == other.maxLongitude &&
                this.minLatitude == other.minLatitude &&
                this.maxLatitude == other.maxLatitude;
    }

}
