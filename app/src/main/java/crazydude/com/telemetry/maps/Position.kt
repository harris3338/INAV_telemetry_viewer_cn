package crazydude.com.telemetry.maps

import android.content.Context
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.CoordinateConverter.CoordType
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.util.GeoPoint
import com.amap.api.maps.model.LatLng as aMapLatLng

data class Position(var lat: Double, var lon: Double) {

    fun toLatLng() : LatLng {
        return LatLng(lat, lon)
    }

    fun toGeoPoint(): GeoPoint {
        return GeoPoint(lat, lon)
    }

    fun toAmapLatLng(context: Context) : aMapLatLng {
        val converter =
            CoordinateConverter(context)
        val sourceLatLng = aMapLatLng(lat, lon)
        // CoordType.GPS 待转换坐标类型
        converter.from(CoordType.GPS)
        // sourceLatLng待转换坐标点
        converter.coord(sourceLatLng)
        // 执行转换操作
        return converter.convert()
    }
}