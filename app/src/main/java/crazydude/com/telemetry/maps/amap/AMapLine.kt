package crazydude.com.telemetry.maps.amap

import com.amap.api.maps.AMap
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import crazydude.com.telemetry.maps.MapLine
import crazydude.com.telemetry.maps.Position

class AMapLine(private val aMap: AMap) : MapLine() {

    private val polylineOptions = PolylineOptions()
    private lateinit var polyline: Polyline

    init {
        polyline = aMap.addPolyline(polylineOptions)
    }

    override fun remove() {
        polyline.remove()
    }

    override fun addPoints(points: List<Position>) {
        val latLngs = points.map { LatLng(it.lat, it.lon) }
        polylineOptions.addAll(latLngs)
        polyline.points = polylineOptions.points // refresh the polyline with new points
    }

    override fun setPoint(index: Int, position: Position) {
        if (index >= 0 && index < polyline.points.size) {
            val points = ArrayList(polyline.points)
            points[index] = LatLng(position.lat, position.lon)
            polyline.points = points
        }
    }

    override fun clear() {
        polyline.points = emptyList() // Clear all points from the polyline
    }

    override fun removeAt(index: Int) {
        if (index >= 0 && index < polyline.points.size) {
            val points = ArrayList(polyline.points)
            points.removeAt(index)
            polyline.points = points
        }
    }

    override val size: Int
        get() = polyline.points.size
    
    override var color: Int
        get() = polyline.color
        set(value) {
            polyline.color = value  // set the color of the polyline
        }
}
