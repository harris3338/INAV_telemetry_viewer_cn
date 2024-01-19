package crazydude.com.telemetry.maps.amap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.amap.api.maps.AMap
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import crazydude.com.telemetry.maps.MapMarker
import crazydude.com.telemetry.maps.Position

class AMapMarker(icon: Int, color: Int, position: Position, private val aMap: AMap, private val context: Context) : MapMarker {

    private val marker: Marker

    init {
        val drawable = ContextCompat.getDrawable(context, icon)
        DrawableCompat.setTint(drawable!!, color)
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        val tintedIcon = BitmapDescriptorFactory.fromBitmap(bitmap)


        // Setup the marker options and create the marker on the map
        val markerOptions = MarkerOptions()
            .position(position.toAmapLatLng(context))
            .icon(tintedIcon)
        marker = aMap.addMarker(markerOptions)
    }

    override var rotation: Float
        get() = marker.rotateAngle
        set(value) { marker.rotateAngle = -value }
        
    override var position: Position
        get() = Position(marker.position.latitude, marker.position.longitude)
        set(value) { marker.position = value.toAmapLatLng(context) }

    override fun setIcon(icon: Int, color: Int) {
        // Convert drawable resource to BitmapDescriptor, applying the tint
        val drawable = ContextCompat.getDrawable(context, icon)
        DrawableCompat.setTint(drawable!!, color)
        val bitmap = BitmapFactory.decodeResource(context.resources, icon)
        val tintedIcon = BitmapDescriptorFactory.fromBitmap(bitmap)

        // Set the updated icon for the marker
        marker.setIcon(tintedIcon)
    }

    override fun remove() {
        marker.remove()
    }

//    private fun Position.toLatLng(): com.amap.api.maps.model.LatLng {
//        return com.amap.api.maps.model.LatLng(this.latitude, this.longitude)
//    }
}
