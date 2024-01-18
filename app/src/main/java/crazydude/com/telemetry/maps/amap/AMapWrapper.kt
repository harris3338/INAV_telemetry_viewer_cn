package crazydude.com.telemetry.maps.amap

import android.content.Context
import android.os.Bundle
import android.graphics.BitmapFactory
import com.amap.api.maps.AMap
import com.amap.api.maps.MapView
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.maps.model.BitmapDescriptorFactory;
import crazydude.com.telemetry.maps.MapLine
import crazydude.com.telemetry.maps.MapMarker
import crazydude.com.telemetry.maps.MapWrapper
import crazydude.com.telemetry.maps.Position

class AMapWrapper(private val context: Context, private val mapView: MapView, private val callback: () -> Unit) : MapWrapper {
    private lateinit var aMap: AMap

    companion object {
        const val MAP_TYPE_AMAP_SATELLITE = 7
    }

    init {
        initializeMap()
    }

    private fun initializeMap() {
        // Only proceed if aMap has not been initialized
        if (!::aMap.isInitialized) {
            aMap = mapView.getMap() // Initialize the aMap object from the mapView

            with(aMap.uiSettings) {
                isZoomControlsEnabled = true
                isMyLocationButtonEnabled = true
            }
            aMap.isMyLocationEnabled = true // Set default value
            aMap.setMapType(AMap.MAP_TYPE_SATELLITE);// 设置卫星地图模式，aMap是地图控制器对象。
            callback() // Invoke the callback now that aMap is set up
        }
    }

    override fun initialized(): Boolean {
        return ::aMap.isInitialized
    }

    override var mapType: Int
        get() = aMap.mapType
        set(value) {
            aMap.mapType = value
        }

    override var isMyLocationEnabled: Boolean
        get() = aMap.isMyLocationEnabled
        set(value) {
            aMap.isMyLocationEnabled = value
        }

    override fun moveCamera(position: Position) {
        aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLng(position.toAmapLatLng(context)))
    }

    override fun moveCamera(position: Position, zoom: Float) {
        aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(position.toAmapLatLng(context), zoom))
    }

    override fun addMarker(icon: Int, color: Int, position: Position): MapMarker {
        return AMapMarker(icon, color, position, aMap, context)
    }

    override fun addPolyline(width: Float, color: Int, vararg points: Position): MapLine {
        val polylineOptions = PolylineOptions()
        for (point in points) {
            polylineOptions.add(point.toAmapLatLng(context))
        }
        polylineOptions.width(width).color(color)
        val polyline = aMap.addPolyline(polylineOptions)
        return AMapLine(aMap)
    }

    override fun setOnCameraMoveStartedListener(function: () -> Unit) {
        aMap.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChangeFinish(p0: com.amap.api.maps.model.CameraPosition?) {
                function()
            }

            override fun onCameraChange(p0: com.amap.api.maps.model.CameraPosition?) {
                // This can be empty if we just want to listen for the finish event
            }
        })
    }

     override fun addPolyline(color: Int): MapLine {
         val res = aMap.addPolyline(PolylineOptions().color(color))
         return AMapLine(aMap)
     }

    override fun onCreate(bundle: Bundle?) {
        mapView.onCreate(bundle)
    }

    override fun onResume() {
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
    }

    override fun onLowMemory() {
        mapView.onLowMemory()
    }

    override fun onStart() {
        // Not required for AMap as mapView.onStart() is not available
    }

    override fun onStop() {
        // Not required for AMap as mapView.onStop() is not available
    }

    override fun onDestroy() {
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        mapView.onSaveInstanceState(outState)
    }

     override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
         // not found in AMap SDK
     }

    override fun invalidate() {
        mapView.invalidate()
    }

//    private fun position.toAmapLatLng(context): LatLng {
//        return LatLng(this.lat, this.lon)
//    }
}
