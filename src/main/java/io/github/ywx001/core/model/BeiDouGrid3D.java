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
public class BeiDouGrid3D extends BeiDouGrid2D{
    /**
     * 网格最小大地高度
     */
    private double minHeight;

    /**
     * 网格最大大地高度
     */
    private double maxHeight;

    public BeiDouGrid3D(int level, BeiDouGeoPoint point) {
        super(level, point);

        this.minHeight = point.getHeight();
        this.maxHeight = point.getHeight() + BeiDouGridConstants.GRID_SIZES_3D[level];

        this.code = BeiDouGridUtils.encode3D(point, level);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null){
            return false;
        }
        if (!(obj instanceof BeiDouGrid3D other)){
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
                this.maxLatitude == other.maxLatitude &&
                this.minHeight == other.minHeight &&
                this.maxHeight == other.maxHeight;
    }
}
