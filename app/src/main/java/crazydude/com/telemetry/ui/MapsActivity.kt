package crazydude.com.telemetry.ui

import android.app.Activity
import android.app.PendingIntent
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.media.SoundPool
import android.net.Uri
import android.os.AsyncTask
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.text.Html
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.amap.api.maps.AMap
import com.amap.api.maps.MapsInitializer
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.maps.android.SphericalUtil
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.nex3z.flowlayout.FlowLayout
import com.serenegiant.usbcameratest4.CameraFragment
import com.serenegiant.usbcameratest4.CameraFragmentListener
import crazydude.com.telemetry.R
import crazydude.com.telemetry.converter.Converter
import crazydude.com.telemetry.manager.PreferenceManager
import crazydude.com.telemetry.manager.SensorTimeoutManager
import crazydude.com.telemetry.maps.MapLine
import crazydude.com.telemetry.maps.MapMarker
import crazydude.com.telemetry.maps.MapWrapper
import crazydude.com.telemetry.maps.Position
import crazydude.com.telemetry.maps.amap.AMapWrapper
import crazydude.com.telemetry.maps.google.GoogleMapWrapper
import crazydude.com.telemetry.maps.osm.OsmMapWrapper
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import crazydude.com.telemetry.protocol.pollers.LogPlayer
import crazydude.com.telemetry.service.DataService
import kotlinx.android.synthetic.main.top_layout.*
import kotlinx.android.synthetic.main.view_map.*
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import uk.co.deanwild.materialshowcaseview.IShowcaseListener
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt


//class MapsActivity : AppCompatActivity(), DataDecoder.Listener {
class MapsActivity : com.serenegiant.common.BaseActivity(), DataDecoder.Listener, SensorTimeoutManager.Listener, CameraFragmentListener {

    companion object {
        private const val REQUEST_ENABLE_BT: Int = 0
        private const val REQUEST_LOCATION_PERMISSION: Int = 1
        private const val REQUEST_WRITE_PERMISSION: Int = 2
        private const val REQUEST_READ_PERMISSION: Int = 3
        private const val ACTION_USB_DEVICE = "action_usb_device"
        private val MAP_TYPE_ITEMS = arrayOf(
            "谷歌路网地图",
            "谷歌卫星地图",
            "谷歌地形图",
            "谷歌混合地图",
            "OSM地图（可缓存）",
            "OSM地形图（可缓存）",
            "高德卫星地图"
        )

        private const val CONNTYPE_NONE = 0
        private const val CONNTYPE_BT = 1
        private const val CONNTYPE_BLE = 2
        private const val CONNTYPE_USB = 3
    }

    enum class RequestWritePermissionSequenceType {
        NONE, CONNECT, RENAME, DELETE, EXPORT_GPX, EXPORT_KML
    }

    private var map: MapWrapper? = null
    private var amap: AMap? = null

    private var soundPool: SoundPool? = null
    private var connectedSoundId: Int = 0
    private var disconnectedSoundId: Int = 0
    private var connectionFailedSoundId: Int = 0
    private var reconnectingSoundId : Int = 0

    private var marker: MapMarker? = null
    private var polyLine: MapLine? = null
    private var headingPolyline: MapLine? = null

    private lateinit var connectButton: Button
    private lateinit var replayButton: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: FloatingActionButton
    private lateinit var fuel: TextView
    private lateinit var rssi: TextView
    private lateinit var satellites: TextView
    private lateinit var current: TextView
    private lateinit var voltage: TextView
    private lateinit var phoneBattery: TextView
    private lateinit var speed: TextView
    private lateinit var airspeed: TextView
    private lateinit var vspeed: TextView
    private lateinit var distance: TextView
    private lateinit var traveled_distance: TextView
    private lateinit var altitude: TextView
    private lateinit var altitude_msl: TextView
    private lateinit var mode: TextView
    private lateinit var statustext: TextView
    private lateinit var followButton: FloatingActionButton
    private lateinit var mapTypeButton: FloatingActionButton
    private lateinit var fullscreenButton: ImageView
    private lateinit var videoFullscreenButton: ImageView
    private lateinit var layoutButton: ImageView
    private lateinit var menuButton: FloatingActionButton
    private lateinit var settingsButton: ImageView
    private lateinit var topLayout: RelativeLayout
    private lateinit var bottomLayout: RelativeLayout
    private lateinit var horizonView: HorizonView
    private lateinit var topList: FlowLayout
    private lateinit var bottomList: FlowLayout
    private lateinit var rootLayout: CoordinatorLayout
    private lateinit var mapHolder: FrameLayout
    private lateinit var videoHolder: AspectFrameLayout
    private lateinit var mapViewHolder: FrameLayout
    private lateinit var rc_widget: RCWidget
    private lateinit var dnSnr: TextView
    private lateinit var upSnr: TextView
    private lateinit var upLq: TextView
    private lateinit var dnLq: TextView
    private lateinit var elrsRate: TextView
    private lateinit var ant: TextView
    private lateinit var power: TextView
    private lateinit var rssiDbm1: TextView
    private lateinit var rssiDbm2: TextView
    private lateinit var rssiDbmd: TextView
    private lateinit var cell_voltage: TextView
    private lateinit var throttle: TextView
    private lateinit var tlmRate: TextView

    private lateinit var mCameraFragment: com.serenegiant.usbcameratest4.CameraFragment
    private lateinit var cameraImageView: ImageView
    private lateinit var cameraImageDesc: TextView

    private lateinit var sensorViewMap: HashMap<String, View>
    private lateinit var sensorsConverters: HashMap<String, Converter>

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    private var mapType = GoogleMap.MAP_TYPE_NORMAL

    private var lastGPS = Position(0.0, 0.0)
    private var lastHeading = 0f
    private var followMode = true
    private var hasGPSFix = false
    private var replayFileString: String? = null
    private var dataService: DataService? = null
    private var lastPhoneBattery = 0
    private var lastTraveledDistance = 0.0
    private var lastCellVoltage = 0.0f

    private var fullscreenWindow = false

    private var gotHeading = false;

    private var logPlayer : LogPlayer? = null;

    private var requestWritePermissionSequence = RequestWritePermissionSequenceType.NONE;

    private var lastFileDialogSelectionIndex = -1;
    private var lastFileDialogSelection = "";

    private var lastSelectedDataPooler = "";
    private var lastSelectedBluetoothDeviceAddress = "";
    private var lastSelectedBLEDeviceAddress = "";

    private var reconnectionStartTime = 0L;
    private var lastConnectionType = CONNTYPE_NONE;
    private var lastBluetoothDevice: BluetoothDevice? = null;
    private var reconnectOnFailure = false;

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(p0: ComponentName?) {
            onDisconnected()
        }

        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            dataService = (p1 as DataService.DataBinder).getService()
            dataService?.setDataListener(this@MapsActivity)
            dataService?.let {
                if (it.isConnected()) {
                    switchToConnectedState()
                    polyLine?.submitPoints(it.points)
                }
            }
        }
    }

    private val sensorTimeoutManager: SensorTimeoutManager = SensorTimeoutManager(this);

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        preferenceManager = PreferenceManager(this)

        soundPool = SoundPool(5, AudioManager.STREAM_NOTIFICATION, 0)
        connectedSoundId = soundPool!!.load(this, R.raw.connected, 1)
        disconnectedSoundId = soundPool!!.load(this, R.raw.disconnected, 1)
        connectionFailedSoundId = soundPool!!.load(this, R.raw.connection_failed, 1)
        reconnectingSoundId = soundPool!!.load(this, R.raw.reconnecting, 1)

        mapType = preferenceManager.getMapType()
        followMode = savedInstanceState?.getBoolean("follow_mode", true) ?: true
        replayFileString = savedInstanceState?.getString("replay_file_name")
        fullscreenWindow = preferenceManager.isFullscreenWindow()

        lastSelectedDataPooler = preferenceManager.getLastSelectedDataPooler()
        lastSelectedBluetoothDeviceAddress = preferenceManager.getLastSelectedBluetoothDeviceAddress()
        lastSelectedBLEDeviceAddress = preferenceManager.getLastSelectedBLEDeviceAddress()

        rootLayout = findViewById(R.id.rootLayout)
        fuel = findViewById(R.id.fuel)
        rssi = findViewById(R.id.rssi)
        satellites = findViewById(R.id.satellites)
        topLayout = findViewById(R.id.top_layout)
        bottomLayout = findViewById(R.id.bottom_layout)
        connectButton = findViewById(R.id.connect_button)
        current = findViewById(R.id.current)
        voltage = findViewById(R.id.voltage)
        phoneBattery = findViewById(R.id.phone_battery)
        speed = findViewById(R.id.speed)
        airspeed = findViewById(R.id.airspeed)
        vspeed = findViewById(R.id.vspeed)
        distance = findViewById(R.id.distance)
        traveled_distance = findViewById(R.id.traveled_distance)
        altitude = findViewById(R.id.altitude)
        altitude_msl = findViewById(R.id.altitude_msl)
        mode = findViewById(R.id.mode)
        statustext = findViewById(R.id.statustext)
        followButton = findViewById(R.id.follow_button)
        mapTypeButton = findViewById(R.id.map_type_button)
        settingsButton = findViewById(R.id.settings_button)
        replayButton = findViewById(R.id.replay_button)
        seekBar = findViewById(R.id.seekbar)
        playButton = findViewById(R.id.play_button)
        horizonView = findViewById(R.id.horizon_view)
        fullscreenButton = findViewById(R.id.fullscreen_button)
        videoFullscreenButton = findViewById(R.id.video_fullscreen_button)
        layoutButton = findViewById(R.id.layout_button)
        menuButton = findViewById(R.id.replay_menu_button)
        topList = findViewById(R.id.top_list)
        bottomList = findViewById(R.id.bottom_list)
        mapHolder = findViewById(R.id.map_holder)
        videoHolder = findViewById(R.id.viewHolder)
        mapViewHolder = findViewById(R.id.mapViewHolder)
        rc_widget = findViewById(R.id.rc_widget)
        dnSnr = findViewById(R.id.dn_snr)
        upSnr = findViewById(R.id.up_snr)
        upLq = findViewById(R.id.up_lq)
        dnLq = findViewById(R.id.dn_lq)
        elrsRate = findViewById(R.id.elrs_rate)
        ant = findViewById(R.id.ant)
        power = findViewById(R.id.power)
        rssiDbm1 = findViewById(R.id.up_rssi_dbm1)
        rssiDbm2 = findViewById(R.id.up_rssi_dbm2)
        rssiDbmd = findViewById(R.id.dn_rssi_dbm)
        cell_voltage = findViewById(R.id.cell_voltage)
        throttle = findViewById(R.id.throttle)
        tlmRate = findViewById(R.id.tlm_rate)

        cameraImageView = findViewById(R.id.cameraImageView)
        cameraImageDesc = findViewById(R.id.cameraImageDesc)

        videoHolder.setAspectRatio(640.0 / 480)

        sensorViewMap = hashMapOf(
            Pair(PreferenceManager.sensors.elementAt(0).name, satellites),
            Pair(PreferenceManager.sensors.elementAt(1).name, fuel),
            Pair(PreferenceManager.sensors.elementAt(2).name, voltage),
            Pair(PreferenceManager.sensors.elementAt(3).name, current),
            Pair(PreferenceManager.sensors.elementAt(4).name, speed),
            Pair(PreferenceManager.sensors.elementAt(5).name, distance),
            Pair(PreferenceManager.sensors.elementAt(6).name, traveled_distance),
            Pair(PreferenceManager.sensors.elementAt(7).name, altitude),
            Pair(PreferenceManager.sensors.elementAt(8).name, phoneBattery),
            Pair(PreferenceManager.sensors.elementAt(9).name, rc_widget),
            Pair(PreferenceManager.sensors.elementAt(10).name, rssi),
            Pair(PreferenceManager.sensors.elementAt(11).name, dnSnr),
            Pair(PreferenceManager.sensors.elementAt(12).name, upSnr),
            Pair(PreferenceManager.sensors.elementAt(13).name, upLq),
            Pair(PreferenceManager.sensors.elementAt(14).name, dnLq),
            Pair(PreferenceManager.sensors.elementAt(15).name, elrsRate),
            Pair(PreferenceManager.sensors.elementAt(16).name, ant),
            Pair(PreferenceManager.sensors.elementAt(17).name, power),
            Pair(PreferenceManager.sensors.elementAt(18).name, rssiDbm1),
            Pair(PreferenceManager.sensors.elementAt(19).name, rssiDbm2),
            Pair(PreferenceManager.sensors.elementAt(20).name, rssiDbmd),
            Pair(PreferenceManager.sensors.elementAt(21).name, airspeed),
            Pair(PreferenceManager.sensors.elementAt(22).name, vspeed),
            Pair(PreferenceManager.sensors.elementAt(23).name, cell_voltage),
            Pair(PreferenceManager.sensors.elementAt(24).name, altitude_msl),
            Pair(PreferenceManager.sensors.elementAt(25).name, throttle),
            Pair(PreferenceManager.sensors.elementAt(26).name, tlmRate)
        )

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        fullscreenButton.setOnClickListener {
            updateFullscreenState()
            this.fullscreenWindow = !this.fullscreenWindow
            preferenceManager.setFullscreenWindow(fullscreenWindow)
            updateWindowFullscreenDecoration()
        }

        layoutButton.setOnClickListener {
            setNextLayout();
        }

        videoFullscreenButton.setOnClickListener {
            var layout = preferenceManager.getMainLayout();
            if ( layout == 2) layout = 1
            else layout = 2;
            preferenceManager.setMainLayout(layout)
            updateLayout();
            updateHorizonViewSize();
        }

        /*
        rootLayout.setOnClickListener {
            var layout = preferenceManager.getMainLayout()
            if (layout == 2) setNextLayout()
        }
        */

        followButton.setOnClickListener {
            setFollowMode(!followMode);
            if (followMode) {
                marker?.let {
                    if (map?.initialized() ?: false) {
                        map?.moveCamera(it.position)
                    }
                }
            }
        }

        mapTypeButton.setOnClickListener {
            showMapTypeSelectorDialog()
        }

        menuButton.setOnClickListener {
            val option0 = "复制飞机位置到剪贴板";
            val option1 = "显示到飞机路线";
            val option2 = "重命名日志";
            val option3 = "删除日志";
            val option4 = "导出GPX文件...";
            val option5 = "导出KML文件...";
            val option6 = "设置回放时长...";

            var options = arrayOf(option0, option1, option2, option3, option4, option5, option6)
            if ( this.logPlayer == null) {
                options = arrayOf(option0, option1)
            }

            this.showDialog( AlertDialog.Builder(this)
            .setTitle("日志选项")
            .setItems(options) { dialog: DialogInterface, which: Int ->
                val selectedOption = options[which]
                when (selectedOption) {
                    option0 -> {
                        showAndCopyCurrentGPSLocation()
                    }
                    option1 -> {
                        showDirectionsToCurrentLocation()
                    }
                    option2 -> {
                        showRenameLogDialog()
                    }
                    option3 -> {
                        showDeleteLogDialog()
                    }
                    option4 -> {
                        showExportGPXDialog()
                    }
                    option5 -> {
                        showExportKMLDialog1()
                    }
                    option6 -> {
                        showSetPlaybackDurationDialog()
                    }
                }
                dialog.dismiss()
            }.create())
        }

        playButton.setOnClickListener {
            if ( this.logPlayer != null) {
                if ( this.logPlayer?.isPlaying() == true) {
                    this.logPlayer?.stop()
                } else {
                    this.logPlayer?.startPlayback()
                }
            }
        }

        if (isInReplayMode()) {
            startReplay(
                File(
                    Environment.getExternalStoragePublicDirectory("TelemetryLogs"),
                    replayFileString
                )
            )
        } else {
            switchToIdleState()
        }

        startDataService()

        checkAppInstallDate()
        initMap(false)
        map?.onCreate(savedInstanceState)

        mCameraFragment =
            getFragmentManager().findFragmentById(R.id.cameraFragment) as CameraFragment

        updateWindowFullscreenDecoration()

        updateScreenOrientation()
        updateCompressionQuality()

        this.registerReceiver(this.batInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private fun updateWindowFullscreenDecoration() {
        if (!this.fullscreenWindow) {
            window.decorView.systemUiVisibility = 0
        } else {
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE)
        }
    }

    private fun updateFullscreenState() {
        //user may have brought system ui with a swipe. Update state
        this.fullscreenWindow = window.decorView.systemUiVisibility ==
                (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE)
    }


    private fun initMap(simulateLifecycle: Boolean) {

        headingPolyline?.remove();
        headingPolyline = null;
        polyLine?.remove();
        polyLine = null;
        marker?.remove();
        marker = null;

        if (mapType in GoogleMap.MAP_TYPE_NORMAL..GoogleMap.MAP_TYPE_HYBRID) {
            initGoogleMap(simulateLifecycle)
        } else if (mapType == OsmMapWrapper.MAP_TYPE_DEFAULT) {
            initOSMMap(TileSourceFactory.DEFAULT_TILE_SOURCE)
        } else if (mapType == OsmMapWrapper.MAP_TYPE_TOPO) {
            initOSMMap(TileSourceFactory.OpenTopo)
        } else {
            initAMap()
        }
    }

    private fun initAMap() {
        val mapView = com.amap.api.maps.MapView(this)
        MapsInitializer.updatePrivacyShow(this,true,true);
        MapsInitializer.updatePrivacyAgree(this,true);
        mapView.onCreate(Bundle())
        mapHolder.addView(mapView) // Assuming mapHolder is a ViewGroup in your layout to contain the map

        val amapWrapper = AMapWrapper(this, mapView) {
           initHeadingLine() // You will need to implement this function
        }

        map = amapWrapper // Assuming 'map' is a variable of type MapWrapper

        map?.setOnCameraMoveStartedListener {
            setFollowMode(false) // You will need to implement this method to handle camera movement interaction
        }

        // Assuming preferenceManager.getRouteColor() retrieves the desired color for the polyline
        val polyLine = map?.addPolyline(preferenceManager.getRouteColor())

        // Assuming dataService.points provides the list of positions for the polyline
        val p = dataService?.points
        if (p != null) {
            polyLine?.submitPoints(p) // You will need to define submitPoints or replace it with the correct method call
        }

        // showMyLocation() 
    }

    
    private fun initOSMMap(tileSource: OnlineTileSourceBase) {
        val mapView = org.osmdroid.views.MapView(this)
        mapHolder.addView(mapView)
        map = OsmMapWrapper(applicationContext, mapView, tileSource) {
            initHeadingLine()
        }
        map?.setOnCameraMoveStartedListener {
            setFollowMode(false);
        }
        polyLine = map?.addPolyline(preferenceManager.getRouteColor())
        val p = dataService?.points;
        if (p != null) {
            polyLine?.submitPoints(p)
        }
        showMyLocation()
    }

    private fun showMyLocation() {
        if (checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map?.isMyLocationEnabled = true
            checkSendDataDialogShown()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_LOCATION_PERMISSION
            )
            map?.isMyLocationEnabled = false
        }
    }

    private fun initGoogleMap(simulateLifecycle: Boolean) {
        val mapView = MapView(this)
        mapHolder.addView(mapView)
        map = GoogleMapWrapper(this, mapView) {
            showMyLocation()
            map?.mapType = mapType
            topLayout.measure(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )

            polyLine = map?.addPolyline(preferenceManager.getRouteColor())
            val p = dataService?.points;
            if (p != null) {
                polyLine?.submitPoints(p)
            }
            map?.setOnCameraMoveStartedListener {
                setFollowMode(false);
            }
            map?.setPadding(0, topLayout.measuredHeight, 0, 0)
            initHeadingLine()
        }
        if (simulateLifecycle) {
            map?.onCreate(null)
            map?.onStart()
            map?.onResume()
        }
    }

    private fun initHeadingLine() {
        polyLine?.let { it.color = preferenceManager.getRouteColor() }
        if (!isIdle()) {
            if (preferenceManager.isHeadingLineEnabled() && headingPolyline == null) {
                headingPolyline = createHeadingPolyline()
                updateHeading()
            } else if (!preferenceManager.isHeadingLineEnabled() && headingPolyline != null) {
                headingPolyline?.remove()
                headingPolyline = null
            }
            headingPolyline?.let { it.color = preferenceManager.getHeadLineColor() }
            marker?.setIcon(R.drawable.ic_plane, preferenceManager.getPlaneColor())
        }
    }

    private fun showAndCopyCurrentGPSLocation() {
        marker?.let {
            val posString = "${it.position.lat},${it.position.lon}"
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.primaryClip = ClipData.newPlainText("Location", posString)
            Toast.makeText(
                this,
                "当前飞机位置已复制到剪贴板 ($posString)",
                Toast.LENGTH_LONG
            ).show()
        }
        if ( marker == null ) {
            Toast.makeText(this, "位置无效", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDirectionsToCurrentLocation() {
        marker?.let {
            val posString = "${it.position.lat},${it.position.lon}"
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("http://maps.google.com/maps?daddr=$posString")
            )
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "无法获取路线", Toast.LENGTH_LONG).show()
            }
        }
        if ( marker == null ) {
            Toast.makeText(this, "位置无效", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAppInstallDate() {
        val installTime = packageManager.getPackageInfo(packageName, 0).firstInstallTime
        val delta = System.currentTimeMillis() - installTime

        if (delta / 1000 / 60 / 60 / 24 > 3 && !preferenceManager.isYoutubeChannelShown()) {
            this.showDialog(AlertDialog.Builder(this)
                .setTitle("感谢您使用本应用")
                .setMessage(
                    "感谢使用本应用，此应用完全免费并且不含任何广告" +
                            "如果你觉得有用，可以来B站点个关注，支持一下作者"
                )
                .setPositiveButton("关注") { dialog: DialogInterface?, i: Int ->
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://space.bilibili.com/14593294?spm_id_from=333.1007.0.0")
                        )
                    )
                }
                .setNegativeButton("取消", null)
                .setOnDismissListener { preferenceManager.setYoutubeShown() }
                .create());
        }
    }

    private fun isInReplayMode(): Boolean {
        return replayFileString != null
    }

    private fun isIdle(): Boolean {
        return !isInReplayMode() && !(dataService?.isConnected() ?: false)
    }

    private fun replay() {
        if (dataService?.isConnected() != true) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_DENIED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_READ_PERMISSION
                )
            } else {
                val dir = Environment.getExternalStoragePublicDirectory("TelemetryLogs")
                if (dir.exists()) {
                    val files =
                        dir.listFiles { file -> ((file.extension == "log") || (file.extension == "tlm")) && (file.length() > 0) }
                            .sorted()
                            .reversed()

                    if ( lastFileDialogSelectionIndex >= files.size) {
                        lastFileDialogSelectionIndex = files.size-1;
                    }

                    val dialog = AlertDialog.Builder(this)
                        .setAdapter(
                            ArrayAdapter(
                                this,
                                android.R.layout.simple_list_item_1,
                                files.map { i ->
                                    if ( i.name == lastFileDialogSelection ) {
                                        val b = "${i.nameWithoutExtension} (${ceil(i.length() / 102.4) / 10} Kb)"
                                        val boldOption = SpannableString(b)
                                        boldOption.setSpan(StyleSpan(Typeface.BOLD), 0, b.length, 0)
                                        boldOption
                                    } else {
                                        "${i.nameWithoutExtension} (${ceil(i.length() / 102.4) / 10} Kb)"
                                    }
                                })
                        ) { _, i ->
                            updateWindowFullscreenDecoration()
                            lastFileDialogSelectionIndex = i;
                            lastFileDialogSelection = files[i].name
                            startReplay(files[i])
                        }
                        .create();

                    dialog.setOnShowListener {
                        val alertDialog = it as AlertDialog
                        if ( lastFileDialogSelectionIndex != -1) {
                            val centerY = alertDialog.listView.height / 2 // Calculate the center position vertically
                            alertDialog.listView.smoothScrollToPositionFromTop(lastFileDialogSelectionIndex, centerY)
                        }
                    }

                    this.showDialog(dialog);
                }
            }
        } else {
            Toast.makeText(this, "请先断开连接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startReplay(file: File?) {
        file?.also {
            val progressDialog = ProgressDialog(this)
            progressDialog.setCancelable(false)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            progressDialog.max = 100

            progressDialog.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            );
            progressDialog.show();
            if (!this.fullscreenWindow) {
                progressDialog.getWindow().decorView.systemUiVisibility = 0
            } else {
                progressDialog.getWindow().decorView.systemUiVisibility =
                    (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE)
            }
            progressDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

            switchToReplayMode()

            replayFileString = it.name

            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
            }

            this.logPlayer = LogPlayer(this)

            val context = this;

            this.logPlayer?.load(file, object : LogPlayer.DataReadyListener {
                override fun onUpdate(percent: Int) {
                    progressDialog.progress = percent
                }

                override fun onDataReady(size: Int) {
                    progressDialog.dismiss()
                    seekBar.max = size
                    seekBar.visibility = View.VISIBLE
                    playButton.visibility = View.VISIBLE
                    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekbar: SeekBar,
                            position: Int,
                            fromUser: Boolean
                        ) {
                            logPlayer?.seek(position)
                        }

                        override fun onStartTrackingTouch(p0: SeekBar?) {
                        }

                        override fun onStopTrackingTouch(p0: SeekBar?) {

                        }
                    })

                    //rewind to first gps data to zoom on plane
                    lastGPS = Position(0.0, 0.0);
                    gotHeading = false;
                    for (i in 0..seekBar.max - 1) {
                        logPlayer?.seek(i)
                        if (lastGPS.lat != 0.0 && lastGPS.lon != 0.0 && marker != null && gotHeading) {
                            break;
                        }
                    }

                    logPlayer?.seek(0);
                }

                override fun onPlaybackPositionChange(prevPosition: Int, nextPosition: Int) {
                    runOnUiThread {
                        if ( (logPlayer?.currentPosition ?:0) == prevPosition ) {
                            seekbar.progress = nextPosition
                        }
                    }
                }

                override fun onPlaybackStateChange( isPlaying : Boolean){
                    runOnUiThread {
                        if ( isPlaying) {
                            playButton.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_pause));
                        } else {
                            playButton.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_play));
                        }
                    }
                }

                override fun getTotalPlaybackDurationSec() : Int
                {
                    return preferenceManager.getPlaybackDuration()
                }

                override fun getPlaybackAutostart() : Boolean
                {
                    return preferenceManager.getPlaybackAutostart()
                }

                override fun onProtocolDetected(protocolName: String) {
                    runOnUiThread {
                        Toast.makeText(context, "协议: $protocolName", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    override fun onFlyModeData(
        armed: Boolean,
        heading: Boolean,
        firstFlightMode: DataDecoder.Companion.FlyMode?,
        secondFlightMode: DataDecoder.Companion.FlyMode?
    ) {
        runOnUiThread {
            if (armed) {
                mode.text = "已解锁"
            } else {
                mode.text = "未解锁"
            }

            if (heading) {
                mode.text = mode.text.toString() + " | 朝向"
            }

            decodeMode(firstFlightMode)
            decodeMode(secondFlightMode)
        }
    }

    private fun decodeMode(flyMode: DataDecoder.Companion.FlyMode?) {
        when (flyMode) {
            DataDecoder.Companion.FlyMode.ACRO -> {
                mode.text = mode.text.toString() + " | Acro"
            }
            DataDecoder.Companion.FlyMode.HORIZON -> {
                mode.text = mode.text.toString() + " | Horizon"
            }
            DataDecoder.Companion.FlyMode.ANGLE -> {
                mode.text = mode.text.toString() + " | Angle"
            }
            DataDecoder.Companion.FlyMode.FAILSAFE -> {
                mode.text = mode.text.toString() + " | Failsafe"
            }
            DataDecoder.Companion.FlyMode.RTH -> {
                mode.text = mode.text.toString() + " | RTH"
            }
            DataDecoder.Companion.FlyMode.WAYPOINT -> {
                mode.text = mode.text.toString() + " | Waypoint"
            }
            DataDecoder.Companion.FlyMode.MANUAL -> {
                mode.text = mode.text.toString() + " | Manual"
            }
            DataDecoder.Companion.FlyMode.CRUISE -> {
                mode.text = mode.text.toString() + " | Cruise"
            }
            DataDecoder.Companion.FlyMode.HOLD -> {
                mode.text = mode.text.toString() + " | Hold"
            }
            DataDecoder.Companion.FlyMode.HOME_RESET -> {
                mode.text = mode.text.toString() + " | Home reset"
            }
            DataDecoder.Companion.FlyMode.CRUISE3D -> {
                mode.text = mode.text.toString() + " | 3D Cruise"
            }
            DataDecoder.Companion.FlyMode.ALTHOLD -> {
                mode.text = mode.text.toString() + " | Alt hold"
            }
            DataDecoder.Companion.FlyMode.ERROR -> {
                mode.text = mode.text.toString() + " | !ERROR!"
            }
            DataDecoder.Companion.FlyMode.WAIT -> {
                mode.text = mode.text.toString() + " | GPS wait"
            }
            DataDecoder.Companion.FlyMode.CIRCLE -> {
                mode.text = mode.text.toString() + " | Circle"
            }
            DataDecoder.Companion.FlyMode.STABILIZE -> {
                mode.text = mode.text.toString() + " | Stabilize"
            }
            DataDecoder.Companion.FlyMode.TRAINING -> {
                mode.text = mode.text.toString() + " | Training"
            }
            DataDecoder.Companion.FlyMode.FBWA -> {
                mode.text = mode.text.toString() + " | FBWA"
            }
            DataDecoder.Companion.FlyMode.FBWB -> {
                mode.text = mode.text.toString() + " | FBWB"
            }
            DataDecoder.Companion.FlyMode.AUTOTUNE -> {
                mode.text = mode.text.toString() + " | Autotune"
            }
            DataDecoder.Companion.FlyMode.LOITER -> {
                mode.text = mode.text.toString() + " | Loiter"
            }
            DataDecoder.Companion.FlyMode.TAKEOFF -> {
                mode.text = mode.text.toString() + " | Takeoff"
            }
            DataDecoder.Companion.FlyMode.AVOID_ADSB -> {
                mode.text = mode.text.toString() + " | AVOID_ADSB"
            }
            DataDecoder.Companion.FlyMode.GUIDED -> {
                mode.text = mode.text.toString() + " | Guided"
            }
            DataDecoder.Companion.FlyMode.INITIALISING -> {
                mode.text = mode.text.toString() + " | Initializing"
            }
            DataDecoder.Companion.FlyMode.LANDING -> {
                mode.text = mode.text.toString() + " | Landing"
            }
            DataDecoder.Companion.FlyMode.MISSION -> {
                mode.text = mode.text.toString() + " | Mission"
            }
            DataDecoder.Companion.FlyMode.QSTABILIZE -> {
                mode.text = mode.text.toString() + " | QSTABILIZE"
            }
            DataDecoder.Companion.FlyMode.QHOVER -> {
                mode.text = mode.text.toString() + " | QHOVER"
            }
            DataDecoder.Companion.FlyMode.QLOITER -> {
                mode.text = mode.text.toString() + " | QLOITER"
            }
            DataDecoder.Companion.FlyMode.QLAND -> {
                mode.text = mode.text.toString() + " | QLAND"
            }
            DataDecoder.Companion.FlyMode.QRTL -> {
                mode.text = mode.text.toString() + " | QRTL"
            }
            DataDecoder.Companion.FlyMode.QAUTOTUNE -> {
                mode.text = mode.text.toString() + " | QAUTOTUNE"
            }
            DataDecoder.Companion.FlyMode.QACRO -> {
                mode.text = mode.text.toString() + " | QACRO"
            }
            DataDecoder.Companion.FlyMode.AUTONOMOUS -> {
                mode.text = mode.text.toString() + " | Autonomous"
            }
            DataDecoder.Companion.FlyMode.RATE -> {
                mode.text = mode.text.toString() + " | Rate"
            }
            null -> {
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        map?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        map?.onSaveInstanceState(outState)
        outState?.putBoolean("follow_mode", followMode)
        outState?.putString("replay_file_name", replayFileString)
        preferenceManager.setFullscreenWindow(fullscreenWindow)
    }

    override fun onStart() {
        super.onStart()
        map?.onStart()
        if (preferenceManager.showArtificialHorizonView()) {
            horizonView.visibility = View.VISIBLE
        } else {
            horizonView.visibility = View.GONE
        }

        updateLayout();

        updateHorizonViewSize()

        updateSensorsPlacement()
    }

    private fun updateSensorsPlacement() {
        val sensorsSettings = preferenceManager.getSensorsSettings().sortedBy { it.index }
        topList.removeAllViews()
        bottomList.removeAllViews()
        sensorsSettings.forEach {
            val sensorView = sensorViewMap[it.name]
            sensorView?.visibility = if (it.shown) View.VISIBLE else View.GONE
            if (it.position == "top") {
                topList.addView(sensorView)
            } else {
                bottomList.addView(sensorView)
            }
        }
    }

    private fun connect() {
        lastConnectionType = CONNTYPE_NONE;
        val showcaseView = MaterialShowcaseView.Builder(this)
            .renderOverNavigationBar()
            .setTarget(replayButton)
            .setMaskColour(Color.argb(230, 0, 0, 0))
            .setDismissText("了解")
            .setContentText("点击此按钮回放飞行记录")
            .setListener(
                object : IShowcaseListener {
                    override fun onShowcaseDismissed(showcaseView: MaterialShowcaseView?) {
                        connect();
                    }
                    override fun onShowcaseDisplayed(showcaseView: MaterialShowcaseView?) {
                    }
                })
            .singleUse("replay_guide").build()

        var items = arrayOf(
            "蓝牙",
            "低功耗蓝牙 LE",
            "USB 串口"
        )

        if (showcaseView.hasFired()) {
            this.showDialog(AlertDialog.Builder(this)
                .setAdapter(
                    ArrayAdapter(
                        this,
                        android.R.layout.simple_list_item_1,
                        items.map { i ->
                            if (i == lastSelectedDataPooler) {
                                val boldOption = SpannableString(i)
                                boldOption.setSpan(StyleSpan(Typeface.BOLD), 0, i.length, 0)
                                boldOption
                            } else {
                                i
                            }
                        })
                ) { dialogInterface, i ->
                    lastSelectedDataPooler = items[i]
                    preferenceManager.setLastSelectedDataPooler(lastSelectedDataPooler)
                    when (i) {
                        0 -> connectBluetooth()
                        1 -> connectBluetoothLE()
                        2 -> connectUSB()
                    }
                }
                .setTitle("选择连接方式")
                .create())
        } else {
            showcaseView.show(this)
        }
    }

    private fun connectUSB() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val driver = drivers.firstOrNull()
        if (driver == null) {
            Toast.makeText(this, "未发现有效USB设备", Toast.LENGTH_SHORT).show()
        } else {
            val connection = usbManager.openDevice(driver.device)
            if (connection != null) {
                val port = driver.ports.firstOrNull()
                if (port == null) {
                    Toast.makeText(this, "未发现有效USB端口", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    connectToUSBDevice(port, connection)
                }
            } else {
                val pendingIntent =
                    PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_DEVICE), 0)
                registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (ACTION_USB_DEVICE == intent?.action) {
                            synchronized(this) {
                                val device: UsbDevice? =
                                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                                if (intent.getBooleanExtra(
                                        UsbManager.EXTRA_PERMISSION_GRANTED,
                                        false
                                    )
                                ) {
                                    device?.apply {
                                        connectUSB()
                                    }
                                } else {
                                    Toast.makeText(
                                        this@MapsActivity,
                                        "连接USB需要打开权限",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }

                        unregisterReceiver(this)
                    }
                }, IntentFilter(ACTION_USB_DEVICE))
                usbManager.requestPermission(driver.device, pendingIntent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        map?.onResume()
        this.sensorTimeoutManager.resume();
        updateWindowFullscreenDecoration()
        updateScreenOrientation()
        updateCompressionQuality()
    }

    override fun onPause() {
        super.onPause()
        map?.onPause()
        this.sensorTimeoutManager.pause();
        this.logPlayer?.stop();
        updateFullscreenState()//check if user has brought system ui with swipe
    }

    override fun onStop() {
        super.onStop()
        map?.onStop()
        this.logPlayer?.stop();
        this.sensorTimeoutManager.pause();
    }

    private fun connectBluetooth() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            this.showDialog(
                AlertDialog.Builder(this)
                    .setMessage("似乎您的设备不支持蓝牙")
                    .setPositiveButton("OK", null)
                    .create()
            )
            return
        }

        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return
        }
        if (preferenceManager.isLoggingEnabled()) {
            if (!requestWritePermission(RequestWritePermissionSequenceType.CONNECT)) return;
        }

        val devices = ArrayList<BluetoothDevice>(adapter.bondedDevices)
        var deviceNames = ArrayList<String>(devices.map {
            var result = it.name
            if (result == null) {
                result = it.address
            }
            if (result == null) {
                result = "*noname*"
            }
            result
        })

        deviceNames = augmentNonUniqueDiviceNames(deviceNames, devices.map { i-> i.address })

        var deviceNames1 = deviceNames.mapIndexed { index, i ->
            if ( devices[index].address == lastSelectedBluetoothDeviceAddress ) {
                val boldOption = SpannableString(i)
                boldOption.setSpan(StyleSpan(Typeface.BOLD), 0, i.length, 0)
                boldOption
            } else {
                i
            }
        }.toMutableList()

        val deviceAdapter = ArrayAdapter( this, android.R.layout.simple_list_item_1, deviceNames1)

        var dialog = AlertDialog.Builder(this).setOnDismissListener {
        } .setNeutralButton(R.string.pair_new_device) { dialog, which ->
            showPairDeviceDialog()
        }.setAdapter(deviceAdapter) { _, i ->
            lastSelectedBluetoothDeviceAddress = devices[i].address;
            preferenceManager.setLastSelectedBluetoothDeviceAddress(lastSelectedBluetoothDeviceAddress)
            runOnUiThread {
                connectToBluetoothDevice(devices[i], false)
            }
        }.create()

        dialog.setOnShowListener {
            val alertDialog = it as AlertDialog
            var index = devices.indexOfFirst {i -> i.address == lastSelectedBluetoothDeviceAddress}
            if ( index != -1) {
                val centerY = alertDialog.listView.height / 2 // Calculate the center position vertically
                alertDialog.listView.smoothScrollToPositionFromTop(index, centerY)
            }
        }

        this.showDialog(dialog)
    }

    private fun augmentNonUniqueDiviceNames(deviceNames : ArrayList<String>, deviceAddr : List<String>) : ArrayList<String>
    {
        return ArrayList(deviceNames.mapIndexed { index, i ->
            var i1 = deviceNames.indexOf(i)
            var i2 = deviceNames.lastIndexOf(i)
            if (i1 != i2) {
                "${deviceNames[index]} (${deviceAddr[index]})"
            } else {
                i
            }
        })
    }

    private fun showPairDeviceDialog() {
        val devices = ArrayList<BluetoothDevice>()
        val deviceNames = ArrayList<String>()
        val deviceAdapter =
            ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, deviceNames)
        AlertDialog.Builder(this)
            .setAdapter(deviceAdapter) { _, i ->
                BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                pairDevice(devices[i])
            }.show()
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        unregisterReceiver(this)
                    }
                    BluetoothDevice.ACTION_FOUND -> {
                        val device =
                            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)!!
                        val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                            ?: device.address
                        if (!deviceNames.contains(name) && device.bondState == BluetoothDevice.BOND_NONE) {
                            devices.add(device)
                            deviceNames.add(name)
                            deviceAdapter.notifyDataSetChanged()
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {

                    }
                }
            }
        }
        registerReceiver(listener, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED).apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
        })
        BluetoothAdapter.getDefaultAdapter().startDiscovery()
    }

    private fun pairDevice(bluetoothDevice: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (!bluetoothDevice.createBond()) {
                Toast.makeText(this, "蓝牙设备配对失败", Toast.LENGTH_LONG).show()
            } else {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                            val device =
                                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            val newBondState: Int =
                                intent.getIntExtra(
                                    BluetoothDevice.EXTRA_BOND_STATE,
                                    BluetoothDevice.BOND_NONE
                                )
                            if (newBondState == BluetoothDevice.BOND_BONDED) {
                                device?.let { connectToBluetoothDevice(it, false) }
                                unregisterReceiver(this)
                            } else if (newBondState == BluetoothDevice.BOND_NONE) {
                                Toast.makeText(
                                    this@MapsActivity,
                                    "配对新设备失败",
                                    Toast.LENGTH_LONG
                                ).show()
                                unregisterReceiver(this)
                            }
                        }
                    }
                }

                registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            }
        } else {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.pair_not_supported_message))
                .show()
        }
    }

    private fun connectBluetoothLE() {
        if (!bleCheck()) {
            Toast.makeText(
                this,
                "设备不支持低功耗蓝牙BLE或未获得所需权限",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            this.showDialog(
                AlertDialog.Builder(this)
                    .setMessage("似乎您的设备不支持蓝牙")
                    .setPositiveButton("OK", null)
                    .create()
            )
            return
        }

        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return
        }
        if (preferenceManager.isLoggingEnabled()) {
            if (!requestWritePermission(RequestWritePermissionSequenceType.CONNECT)) return;
        }

        val devices = ArrayList<BluetoothDevice>(adapter.bondedDevices)
        var deviceNames = ArrayList<String>(devices.map {
            var result = it.name
            if (result == null) {
                result = it.address
            }
            if (result == null) {
                result = "*noname*"
            }
            result
        })

        deviceNames = augmentNonUniqueDiviceNames(deviceNames, devices.map {i -> i.address})

        var deviceNames1 = deviceNames.mapIndexed { index, i ->
            if ( devices[index].address == lastSelectedBLEDeviceAddress ) {
                val boldOption = SpannableString(i)
                boldOption.setSpan(StyleSpan(Typeface.BOLD), 0, i.length, 0)
                boldOption
            } else {
                i
            }
        }.toMutableList()

        val deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames1)

        var scrolled = false;
        var dialog: AlertDialog? = null;

        val callback = BluetoothAdapter.LeScanCallback { bluetoothDevice, i, bytes ->
            if (!devices.contains(bluetoothDevice) && bluetoothDevice.name != null) {
                devices.add(bluetoothDevice)
                var name1 = bluetoothDevice.name
                if ( deviceNames.indexOf( name1) >= 0 ) {
                    name1 = "${bluetoothDevice.name} (${bluetoothDevice.address})"
                }
                if ( lastSelectedBLEDeviceAddress == bluetoothDevice.address) {
                    val boldOption = SpannableString(name1)
                    boldOption.setSpan(StyleSpan(Typeface.BOLD), 0, name1.length, 0)
                    deviceNames1.add(boldOption)

                    if ( dialog is AlertDialog && scrolled) {
                        runOnUiThread {
                            var index = devices.indexOfFirst {i -> i.address == lastSelectedBLEDeviceAddress}
                            if ( index != -1) {
                                val alertDialog = dialog as AlertDialog
                                if ( alertDialog != null ) {
                                    val centerY =
                                        alertDialog.listView.height / 2 // Calculate the center position vertically
                                    alertDialog.listView.smoothScrollToPositionFromTop(
                                        index,
                                        centerY
                                    )
                                }
                            }
                        }
                    }
                }
                else {
                    deviceNames1.add(name1)
                }
                deviceAdapter.notifyDataSetChanged()
            }
        }

        if (bleCheck()) {
            adapter.startLeScan(callback)
        }

        dialog = AlertDialog.Builder(this).setOnDismissListener {
            if (bleCheck()) {
                adapter.stopLeScan(callback)
            }
        }.setAdapter(deviceAdapter) { _, i ->
            lastSelectedBLEDeviceAddress = devices[i].address;
            preferenceManager.setLastSelectedBLEDeviceAddress(lastSelectedBLEDeviceAddress)
            if (bleCheck()) {
                adapter.stopLeScan(callback)
            }
            runOnUiThread {
                connectToBluetoothDevice(devices[i], true)
            }
        }.create()

        dialog.setOnShowListener {
            val alertDialog = it as AlertDialog
            var index = devices.indexOfFirst {i -> i.address == lastSelectedBLEDeviceAddress}
            if ( index != -1) {
                val centerY = alertDialog.listView.height / 2 // Calculate the center position vertically
                alertDialog.listView.smoothScrollToPositionFromTop(index, centerY)
            }
            scrolled = true;
        }

        this.showDialog(dialog)
    }

    private fun resetUI() {
        satellites.text = "0"
        rssi.text = "-"
        this.setRSSIIcon(100)
        voltage.text = "-"
        phoneBattery.text = "-"
        current.text = "-"
        fuel.text = "-"
        this.setFuelIcon(-1);
        altitude.text = "-"
        altitude_msl.text = "-"
        speed.text = "-"
        airspeed.text = "-"
        vspeed.text = "-"
        distance.text = "-"
        traveled_distance.text = "0 m"
        this.lastTraveledDistance = 0.0;
        mode.text = "未连接"
        statustext.text = "";
        dnSnr.text = "-"
        upSnr.text = "-"
        dnLq.text = "-"
        elrsRate.text = "-"
        this.setDNLQIcon(100)
        upLq.text = "-"
        this.setUPLQIcon(100)
        ant.text = "-"
        power.text = "-"
        rssiDbm1.text = "-"
        this.setRssiDbm1Icon(0)
        rssiDbm2.text = "-"
        this.setRssiDbm2Icon(0)
        rssiDbmd.text = "-"
        this.setRssiDbmdIcon(0)
        horizonView.setPitch(0f)
        horizonView.setRoll(0f)
        cell_voltage.text = "-"
        this.lastCellVoltage = 0.0f;
        throttle.text = "-"
        tlmRate.text = "0 b/s"
    }

    private fun bleCheck() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED


    private fun connectToBluetoothDevice(device: BluetoothDevice, isBLE: Boolean) {
        if ( isBLE ) {
            lastConnectionType = CONNTYPE_BLE;
        }
        else {
            lastConnectionType = CONNTYPE_BT;
        }
        reconnectionStartTime = 0;
        reconnectOnFailure = false;

        startDataService()
        dataService?.let {
            connectButton.text = getString(R.string.connecting)
            connectButton.isEnabled = false
            lastBluetoothDevice = device;
            it.connect(device, isBLE)
        }
    }

    private fun reconnectToBluetoothDevice() {
        if (
            (lastBluetoothDevice != null) &&
            ( (lastConnectionType == CONNTYPE_BT) || (lastConnectionType == CONNTYPE_BLE))
        ) {
            if ( preferenceManager.getConnectionVoiceMessagesEnabled()) {
                soundPool!!.play(reconnectingSoundId, 1f, 1f, 0, 0, 1f)
            }

        startDataService()
        dataService?.let {
            connectButton.text = getString(R.string.reconnecting)
            connectButton.isEnabled = false
            if ( lastConnectionType == CONNTYPE_BLE) {
                it.connect(lastBluetoothDevice as BluetoothDevice, true)
                } else if (lastConnectionType == CONNTYPE_BT) {
                it.connect(lastBluetoothDevice as BluetoothDevice, false)
            }
        }
    }
    }

    private fun connectToUSBDevice(
        port: UsbSerialPort,
        connection: UsbDeviceConnection
    ) {
        lastConnectionType = CONNTYPE_USB;
        startDataService()
        dataService?.let {
            connectButton.text = getString(R.string.connecting)
            connectButton.isEnabled = false
            it.connect(port, connection)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        headingPolyline = null;
        polyLine = null;
        map?.onDestroy()
        if (!isChangingConfigurations) {
            dataService?.setDataListener(null)
        }
        map = null;
        this.unregisterReceiver(this.batInfoReceiver)
        unbindService(serviceConnection)
    }

    private fun startDataService() {
        val intent = Intent(this, DataService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        startService(intent)
        bindService(intent, serviceConnection, 0)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty()) {
            if (requestCode == REQUEST_LOCATION_PERMISSION) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    map?.isMyLocationEnabled = true
                    checkSendDataDialogShown()
                } else {
                    this.showDialog(AlertDialog.Builder(this)
                        .setMessage("需要获取定位权限以显示当前位置")
                        .setOnDismissListener { checkSendDataDialogShown() }
                        .setPositiveButton("OK", null)
                        .create())
                }
            } else if (requestCode == REQUEST_WRITE_PERMISSION) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    when (requestWritePermissionSequence) {
                        RequestWritePermissionSequenceType.CONNECT -> connect()
                        RequestWritePermissionSequenceType.DELETE -> showDeleteLogDialog()
                        RequestWritePermissionSequenceType.RENAME -> showRenameLogDialog()
                        RequestWritePermissionSequenceType.EXPORT_GPX -> showExportGPXDialog()
                        RequestWritePermissionSequenceType.EXPORT_KML -> showExportKMLDialog1()
                    }
                    requestWritePermissionSequence = RequestWritePermissionSequenceType.NONE;
                } else {
                    this.showDialog(
                        AlertDialog.Builder(this)
                            .setMessage("需要获得写入权限以记录日志")
                            .setPositiveButton("OK", null)
                            .create()
                    )
                }
            } else if (requestCode == REQUEST_READ_PERMISSION) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    replay()
                } else {
                    this.showDialog(
                        AlertDialog.Builder(this)
                            .setMessage("需要获得读取权限以回放日志")
                            .setPositiveButton("OK", null)
                            .create()
                    )
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            connectBluetooth()
        }
    }

    override fun onVSpeedData(vspeed: Float) {
        this.sensorTimeoutManager.onVSpeedData(vspeed);
        runOnUiThread {
            this.vspeed.text = "${"%.1f".format(vspeed)} m/s"
        }
    }

    override fun onThrottleData(throttle: Int) {
        this.sensorTimeoutManager.onThrottleData(throttle);
        runOnUiThread {
            this.throttle.text = throttle.toString();
        }
    }

    private fun formatDistance(v: Float): String {
        if (v < 1000) {
            return "${"%.0f".format(v)} m"
        } else {
            return "${"%.2f".format(v / 1000)} km"
        }
    }

    private fun formatHeight(v: Float): String {
        if (v < 10) {
            return "${"%.2f".format(v)} m"
        } else if (v < 100) {
            return "${"%.1f".format(v)} m"
        } else if (v < 1000) {
            return "${"%.0f".format(v)} m"
        } else {
            return "${"%.2f".format(v / 1000)} km"
        }
    }

    override fun onAltitudeData(altitude: Float) {
        this.sensorTimeoutManager.onAltitudeData(altitude);
        runOnUiThread {
            this.altitude.text = this.formatHeight(altitude);
        }
    }

    override fun onGPSAltitudeData(altitude: Float) {
        this.sensorTimeoutManager.onGPSAltitudeData(altitude);
        runOnUiThread {
            this.altitude_msl.text = this.formatHeight(altitude);
        }
    }

    override fun onDistanceData(distance: Int) {
        this.sensorTimeoutManager.onDistanceData(distance)
        runOnUiThread {
            this.distance.text = this.formatDistance(distance.toFloat());
        }
    }

    override fun onRollData(rollAngle: Float) {
        runOnUiThread {
            horizonView.setRoll(rollAngle)
        }
    }

    override fun onPitchData(pitchAngle: Float) {
        runOnUiThread {
            horizonView.setPitch(pitchAngle)
        }
    }

    override fun onGSpeedData(speed: Float) {
        this.sensorTimeoutManager.onGSpeedData(speed)
        runOnUiThread {
            this.speed.text = "${speed.roundToInt()} km/h"
        }
    }

    override fun onAirSpeedData(speed: Float) {
        this.sensorTimeoutManager.onAirSpeedData(speed)
        runOnUiThread {
            this.airspeed.text = "${speed.roundToInt()} km/h"
        }
    }

    override fun onRCChannels(rcChannels: IntArray) {
        this.sensorTimeoutManager.onRCChannels(rcChannels)
        runOnUiThread {
            this.rc_widget.setChannels(rcChannels)
        }
    }

    override fun onStatusText(message: String) {
        this.sensorTimeoutManager.onStatusText(message)
        runOnUiThread {
            this.statustext.text = message;
        }
    }

    override fun onGPSState(satellites: Int, gpsFix: Boolean) {
        this.sensorTimeoutManager.onGPSState(satellites, gpsFix)
        runOnUiThread {
            this.hasGPSFix = gpsFix
            this.tryCreateMarker()
            this.satellites.text = if (satellites == 99) "ES" else satellites.toString()
        }
    }

    //should be called on ui thread
    fun tryCreateMarker() {
        if (this.hasGPSFix && marker == null && (map?.initialized() ?: false) && lastGPS.lat != 0.0 && lastGPS.lon != 0.0) {
            if (headingPolyline == null && preferenceManager.isHeadingLineEnabled()) {
                headingPolyline = createHeadingPolyline()
                updateHeading()
            }
            marker =
                map?.addMarker(R.drawable.ic_plane, preferenceManager.getPlaneColor(), lastGPS)
            marker?.rotation = lastHeading;
            map?.moveCamera(lastGPS, 15f)
        }
    }

    private fun createHeadingPolyline(): MapLine? {
        return map?.addPolyline(3f, preferenceManager.getHeadLineColor(), lastGPS, lastGPS)
    }

    private fun setRSSIIcon(rssi: Int) {
        when (rssi) {
            in 81..100 -> R.drawable.ic_rssi_5
            in 61..80 -> R.drawable.ic_rssi_4
            in 41..69 -> R.drawable.ic_rssi_3
            in 21..40 -> R.drawable.ic_rssi_2
            in 1..20 -> R.drawable.ic_rssi_1
            0 -> R.drawable.ic_rssi_0
            else -> R.drawable.ic_rssi_5
        }.let {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                this.rssi.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(
                        this,
                        it
                    ), null, null, null
                )
            } else {
                this.rssi.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(this, it),
                    null,
                    null
                )
            }
        }
    }

    private fun setUPLQIcon(lq: Int) {
        when (lq) {
            in 81..100 -> R.drawable.ic_up_lq_5
            in 61..80 -> R.drawable.ic_up_lq_4
            in 41..69 -> R.drawable.ic_up_lq_3
            in 21..40 -> R.drawable.ic_up_lq_2
            in 1..20 -> R.drawable.ic_up_lq_1
            0 -> R.drawable.ic_up_lq_0
            else -> R.drawable.ic_up_lq_5
        }.let {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                this.upLq.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(
                        this,
                        it
                    ), null, null, null
                )
            } else {
                this.upLq.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(this, it),
                    null,
                    null
                )
            }
        }
    }

    private fun setDNLQIcon(lq: Int) {
        when (lq) {
            in 81..100 -> R.drawable.ic_dn_lq_5
            in 61..80 -> R.drawable.ic_dn_lq_4
            in 41..69 -> R.drawable.ic_dn_lq_3
            in 21..40 -> R.drawable.ic_dn_lq_2
            in 1..20 -> R.drawable.ic_dn_lq_1
            0 -> R.drawable.ic_dn_lq_0
            else -> R.drawable.ic_dn_lq_5
        }.let {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                this.dnLq.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(
                        this,
                        it
                    ), null, null, null
                )
            } else {
                this.dnLq.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(this, it),
                    null,
                    null
                )
            }
        }
    }

    private fun setRssiDbm1Icon(rssi: Int) {
        when (rssi) {
            in -31..0 -> R.drawable.ic_rssi_dbm1_5
            in -51..-30 -> R.drawable.ic_rssi_dbm1_4
            in -71..-59 -> R.drawable.ic_rssi_dbm1_3
            in -91..-70 -> R.drawable.ic_rssi_dbm1_2
            in -120..-90 -> R.drawable.ic_rssi_dbm1_1
            0 -> R.drawable.ic_rssi_dbm1_0
            else -> R.drawable.ic_rssi_dbm1_5
        }.let {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                this.rssiDbm1.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(
                        this,
                        it
                    ), null, null, null
                )
            } else {
                this.rssiDbm1.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(this, it),
                    null,
                    null
                )
            }
        }
    }

    private fun setRssiDbm2Icon(rssi: Int) {
        when (rssi) {
            in -31..0 -> R.drawable.ic_rssi_dbm2_5
            in -51..-30 -> R.drawable.ic_rssi_dbm2_4
            in -71..-50 -> R.drawable.ic_rssi_dbm2_3
            in -91..-70 -> R.drawable.ic_rssi_dbm2_2
            in -121..-90 -> R.drawable.ic_rssi_dbm2_1
            0 -> R.drawable.ic_rssi_dbm2_0
            else -> R.drawable.ic_rssi_dbm2_5
        }.let {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                this.rssiDbm2.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(
                        this,
                        it
                    ), null, null, null
                )
            } else {
                this.rssiDbm2.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(this, it),
                    null,
                    null
                )
            }
        }
    }

    private fun setRssiDbmdIcon(rssi: Int) {
        when (rssi) {
            in -31..0 -> R.drawable.ic_rssi_dbmd_5
            in -51..-30 -> R.drawable.ic_rssi_dbmd_4
            in -71..50 -> R.drawable.ic_rssi_dbmd_3
            in -91..-70 -> R.drawable.ic_rssi_dbmd_2
            in -120..-90 -> R.drawable.ic_rssi_dbmd_1
            0 -> R.drawable.ic_rssi_dbmd_0
            else -> R.drawable.ic_rssi_dbmd_5
        }.let {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                this.rssiDbmd.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(
                        this,
                        it
                    ), null, null, null
                )
            } else {
                this.rssiDbmd.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(this, it),
                    null,
                    null
                )
            }
        }
    }

    override fun onRSSIData(rssi: Int) {
        this.sensorTimeoutManager.onRSSIData(rssi);

        runOnUiThread {
            this.rssi.text = if (rssi == -1) "-" else rssi.toString()
            this.setRSSIIcon(rssi);
        }
    }

    override fun onUpLqData(lq: Int) {
        this.sensorTimeoutManager.onUpLqData(lq);

        runOnUiThread {
            this.upLq.text = if (lq == -1) "-" else lq.toString()
            this.setUPLQIcon(lq);
        }
    }

    override fun onDnLqData(lq: Int) {
        this.sensorTimeoutManager.onDnLqData(lq);

        runOnUiThread {
            this.dnLq.text = if (lq == -1) "-" else lq.toString()
            this.setDNLQIcon(lq);
        }
    }

    override fun onRssiDbm1Data(rssi: Int) {
        this.sensorTimeoutManager.onRssiDbm1Data(rssi);

        runOnUiThread {
            this.rssiDbm1.text = if (rssi == 0) "-" else rssi.toString()
            this.setRssiDbm1Icon(rssi);
        }
    }

    override fun onRssiDbm2Data(rssi: Int) {
        this.sensorTimeoutManager.onRssiDbm2Data(rssi);

        runOnUiThread {
            this.rssiDbm2.text = if (rssi == 0) "-" else rssi.toString()
            this.setRssiDbm2Icon(rssi);
        }
    }

    override fun onRssiDbmdData(rssi: Int) {
        this.sensorTimeoutManager.onRssiDbmdData(rssi);

        runOnUiThread {
            this.rssiDbmd.text = if (rssi == 0) "-" else rssi.toString()
            this.setRssiDbmdIcon(rssi);
        }
    }

    override fun onElrsModeModeData(mode: Int) {
        this.sensorTimeoutManager.onElrsModeModeData(mode);
        runOnUiThread {
            when (mode) {
                13 -> this.elrsRate.text = "F1000"
                12 -> this.elrsRate.text = "F500"
                11 -> this.elrsRate.text = "D500"
                10 -> this.elrsRate.text = "D250"
                9 -> this.elrsRate.text = "L500"
                8 -> this.elrsRate.text = "L333c" //8ch
                7 -> this.elrsRate.text = "L250"
                6 -> this.elrsRate.text = "L200"
                5 -> this.elrsRate.text = "L150"
                4 -> this.elrsRate.text = "L100"
                3 -> this.elrsRate.text = "L100c"  //8ch
                2 -> this.elrsRate.text = "L50"
                1 -> this.elrsRate.text = "L25"
                0 -> this.elrsRate.text = "L4"
                else -> this.elrsRate.text = mode.toString();
            }
        }
    }


    private fun checkSendDataDialogShown() {
        if (!preferenceManager.isSendDataDialogShown()) {
            firebaseAnalytics.logEvent("send_data_dialog_shown", null)
            val dialog = AlertDialog.Builder(this)
                .setMessage(
                    Html.fromHtml(
                        "您可以启用数传数据分享，需要在 <a href='https://uavradar.org'>https://uavradar.org</a> 上获取权限"
                    )
                )
                .setPositiveButton("启用") { _, i ->
                    preferenceManager.setTelemetrySendingEnabled(true)
                    firebaseAnalytics.setUserProperty("telemetry_sharing_enable", "true")
                    firebaseAnalytics.logEvent("telemetry_sharing_enabled", null)
                }
                .setNegativeButton("关闭") { _, i ->
                    preferenceManager.setTelemetrySendingEnabled(false)
                    firebaseAnalytics.setUserProperty("telemetry_sharing_enable", "false")
                    firebaseAnalytics.logEvent("telemetry_sharing_disabled", null)
                }
                .setCancelable(false)
                .create()
            this.showDialog(dialog)
            dialog.findViewById<TextView>(android.R.id.message)?.movementMethod =
                LinkMovementMethod.getInstance()
        }
    }

    private fun showMapTypeSelectorDialog() {
        val fDialogTitle = "选择地图类型"
        val builder = AlertDialog.Builder(this)
        builder.setTitle(fDialogTitle)

        val checkItem = preferenceManager.getMapType() - 1

        builder.setSingleChoiceItems(
            MAP_TYPE_ITEMS,
            checkItem
        ) { dialog, item ->
            mapHolder.removeAllViews()
            map = null
            mapType = item + 1
            preferenceManager.setMapType(mapType)
            initMap(true)
            dialog.dismiss()
        }

        val fMapTypeDialog = builder.create()
        fMapTypeDialog.setCanceledOnTouchOutside(true)
        this.showDialog(fMapTypeDialog);
    }

    override fun onVBATData(voltage: Float) {
        this.sensorTimeoutManager.onVBATData(voltage);
        runOnUiThread {
            this.voltage.text = "${"%.2f".format(voltage)} V"
        }
    }

    override fun onCellVoltageData(voltage: Float) {
        this.lastCellVoltage = voltage;
        runOnUiThread {
            this.cell_voltage.text = "${"%.2f".format(voltage)} V"
        }
    }

    override fun onVBATOrCellData(voltage: Float) {
        runOnUiThread {
            val reportVoltage = preferenceManager.getReportVoltage()
            if (reportVoltage == "Battery") {
                this.sensorTimeoutManager.onVBATData(voltage);
                this.voltage.text = "${"%.2f".format(voltage)} V"
            } else {
                this.sensorTimeoutManager.onCellVoltageData(voltage)
                this.cell_voltage.text = "${"%.2f".format(voltage)} V"
                this.lastCellVoltage = voltage;
            }
        }
    }

    override fun onCurrentData(current: Float) {
        this.sensorTimeoutManager.onCurrentData(current)
        runOnUiThread {
            this.current.text = "${"%.2f".format(current)} A"
        }
    }

    override fun onDNSNRData(snr: Int) {
        this.sensorTimeoutManager.onDNSNRData(snr);
        runOnUiThread {
            this.dnSnr.text = snr.toString();
        }
    }

    override fun onUPSNRData(snr: Int) {
        this.sensorTimeoutManager.onUPSNRData(snr);
        runOnUiThread {
            this.upSnr.text = snr.toString();
        }
    }

    override fun onAntData(activeAntena: Int) {
        this.sensorTimeoutManager.onAntData(activeAntena);
        runOnUiThread {
            this.ant.text = (activeAntena + 1).toString();
        }
    }

    override fun onPowerData(power: Int) {
        this.sensorTimeoutManager.onPowerData(power);
        runOnUiThread {
            when (power) {
                1 -> this.power.text = "10mW"
                2 -> this.power.text = "25mW"
                3 -> this.power.text = "100mW"
                4 -> this.power.text = "500mW"
                5 -> this.power.text = "1W"
                6 -> this.power.text = "2W"
                7 -> this.power.text = "250mW"
                8 -> this.power.text = "50mW"
                else -> this.power.text = power.toString();
            }
        }
    }

    override fun onHeadingData(heading: Float) {
        gotHeading = true;
        lastHeading = heading
        runOnUiThread {
            marker?.let {
                it.rotation = heading
                updateHeading()
            }
        }
    }

    private fun updateHeading() {
        if (lastGPS.lat != 0.0 && lastGPS.lon != 0.0) {
            headingPolyline?.let { headingLine ->
                headingLine.setPoint(0, lastGPS)
                val computeOffset =
                    SphericalUtil.computeOffset(lastGPS.toLatLng(), 1000.0, lastHeading.toDouble())
                headingLine.setPoint(1, Position(computeOffset.latitude, computeOffset.longitude))
            }
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            Toast.makeText(this, "未连接", Toast.LENGTH_SHORT).show()
            switchToIdleState()

            if (preferenceManager.getConnectionVoiceMessagesEnabled()) {
                soundPool!!.play(disconnectedSoundId, 1f, 1f, 0, 0, 1f)
            }

            reconnectOnFailure = true;
            tryReconnect()
        }
    }

    fun tryReconnect() {
        if (!preferenceManager.getReconnectionEnabled()) {
            return;
        }

        runOnUiThread {
            if ((lastConnectionType == CONNTYPE_BT) || (lastConnectionType == CONNTYPE_BLE)) {
                if (reconnectionStartTime == 0L) {
                    reconnectionStartTime = System.currentTimeMillis()
                }

                if ((System.currentTimeMillis() - reconnectionStartTime) < 21000) {
                    AsyncTask.execute {
                        Thread.sleep(5000)
                            runOnUiThread {
                                reconnectToBluetoothDevice()
                            }
                        }
                    }
                }
            }
        }

    private fun switchToReplayMode() {
        setFollowMode(true);
        seekBar.setOnSeekBarChangeListener(null)
        seekBar.progress = 0
        menuButton.show()
        connectButton.visibility = View.GONE
        replayButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_close))
        replayButton.setOnClickListener {
            lastConnectionType = CONNTYPE_NONE; //reset last connection type to skip reconnection
            switchToIdleState()
            replayFileString = null
        }
        this.sensorTimeoutManager.disableTimeouts()
        this.tlmRate.setAlpha(0.5f);
        lastGPS = Position(0.0, 0.0);
        hasGPSFix = false;
        marker?.remove()
        marker = null
        headingPolyline?.remove()
        headingPolyline = null
    }

    private fun switchToIdleState() {
        this.logPlayer?.stop();
        this.logPlayer = null;
        resetUI()
        menuButton.hide()
        seekBar.visibility = View.GONE
        playButton.visibility = View.GONE
        connectButton.visibility = View.VISIBLE
        connectButton.text = getString(R.string.connect)
        replayButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_replay))
        replayButton.visibility = View.VISIBLE
        replayButton.setOnClickListener {
            replay()
        }
        connectButton.isEnabled = true
        connectButton.setOnClickListener {
            connect()
        }
        marker?.remove()
        marker = null
        polyLine?.clear()
        headingPolyline?.remove()
        headingPolyline = null;
        this.sensorTimeoutManager.enableTimeouts()
        this.tlmRate.setAlpha(1.0f);
        lastGPS = Position(0.0, 0.0)
    }

    private fun switchToConnectedState() {
        replayButton.visibility = View.GONE
        menuButton.show()
        connectButton.text = getString(R.string.disconnect)
        connectButton.isEnabled = true
        mode.text = "已连接"
        connectButton.setOnClickListener {
            connectButton.isEnabled = false
            connectButton.text = getString(R.string.disconnecting)
            lastConnectionType = CONNTYPE_NONE; //reset last connection type to skip reconnection
            dataService?.disconnect()
        }
    }

    override fun onConnectionFailed() {
        runOnUiThread {
            Toast.makeText(this, "连接失败", Toast.LENGTH_SHORT).show()
            connectButton.text = getString(R.string.connect)
            mode.text = "未连接"
            connectButton.isEnabled = true
            connectButton.setOnClickListener {
                connect()
            }
            if (preferenceManager.getConnectionVoiceMessagesEnabled()) {
                soundPool!!.play(connectionFailedSoundId, 1f, 1f, 0, 0, 1f)
            }

            if ( reconnectOnFailure ) {
                tryReconnect()
            }
        }
    }

    private fun setFuelIcon(percentage: Int) {
        when (percentage) {
            in 91..100 -> R.drawable.ic_battery_full
            in 81..90 -> R.drawable.ic_battery_90
            in 61..80 -> R.drawable.ic_battery_80
            in 51..60 -> R.drawable.ic_battery_60
            in 31..50 -> R.drawable.ic_battery_50
            in 21..30 -> R.drawable.ic_battery_30
            in 0..20 -> R.drawable.ic_battery_alert
            else -> R.drawable.ic_battery_unknown
        }.let {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                this.fuel.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(this, it),
                    null,
                    null,
                    null
                )
            } else {
                this.fuel.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(this, it),
                    null,
                    null
                )
            }
        }
    }

    private fun formatPower(v: Int, suffix: String): String {
        if (v < 1000) {
            return "$v $suffix"
        } else {
            if (suffix == "mAh") {
                return "${"%.2f".format(v / 1000f)} Ah"
            } else {
                return "${"%.2f".format(v / 1000f)} Wh"
            }
        }
    }

    override fun onFuelData(fuel: Int) {
        this.sensorTimeoutManager.onFuelData(fuel)
        runOnUiThread {
            val batteryUnits = preferenceManager.getBatteryUnits()
            var percentage = fuel

            when (batteryUnits) {
                "mAh", "mWh" -> {
                    this.fuel.text = this.formatPower(fuel, batteryUnits)
                    //for icon, calculate percentage from cell voltage if available
                    if ((lastCellVoltage > 0) && (lastCellVoltage <= 4.4)) {
                        percentage = ((1 - (4.2f - lastCellVoltage)).coerceIn(0f, 1f) * 100).toInt()
                    } else {
                        percentage = -1;  //unknnow icon
                    }
                }
                "Percentage" -> {
                    this.fuel.text = "$fuel%"
                }
            }

            this.setFuelIcon(percentage);
        }
    }


    override fun onTelemetryByte() {
        this.sensorTimeoutManager.onTelemetryByte()
    }

    override fun onImageData(buf: ByteArray, imagesReceived: Int, imagesLost: Int) {
        this.sensorTimeoutManager. onImageData( buf, imagesReceived, imagesLost)

        val bitmap = BitmapFactory.decodeByteArray(buf, 0, buf.size)

        if ( bitmap != null)
        {
            runOnUiThread {
                var aspect = bitmap.width*1.0 / bitmap.height
                if ( aspect > 1.777777777 ) aspect = 1.777777777
                videoHolder.setAspectRatio(aspect)

                this.cameraImageView.setImageDrawable(BitmapDrawable(resources, bitmap))
                cameraImageDesc.text = "Frame:" + imagesReceived + " (" + buf.size + "b) Lost:" + imagesLost;
            }
        }
    }

    override fun onSuccessDecode() {
        this.sensorTimeoutManager.onSuccessDecode()
    }

    override fun onDecoderRestart() {
        runOnUiThread {
            this.lastGPS = Position(0.0, 0.0);
            this.hasGPSFix = false;
            this.lastTraveledDistance = 0.0
            this.polyLine?.clear()
        }
    }

    override fun onProtocolDetected( protocolName: String) {
        runOnUiThread {
            Toast.makeText(this, "协议: $protocolName", Toast.LENGTH_SHORT).show()
        }
    }

    override fun commit() {
        runOnUiThread {
            commitRouteLinePoints()
        }
    }

    override fun onGPSData(list: List<Position>, addToEnd: Boolean) {
        this.sensorTimeoutManager.onGPSData(list, addToEnd);
        runOnUiThread {
            if (!addToEnd) {
                polyLine?.clear()
                this.lastTraveledDistance = 0.0;
                lastGPS = Position(0.0,0.0)
            }
            if (hasGPSFix && list.isNotEmpty()) {
                //add all points except last one
                //last one will be fired in onGPSData()
                if ( list.size>=2) {
                    polyLine?.submitPoints(list.dropLast(1))
                }

                for (i in 0..list.size - 2) {
                    if (this.lastGPS.lat != 0.0 && this.lastGPS.lon != 0.0) {
                        this.lastTraveledDistance += SphericalUtil.computeDistanceBetween(
                            this.lastGPS.toLatLng(), LatLng(list[i].lat, list[i].lon)
                        )
                    }
                    lastGPS = Position(list[i].lat, list[i].lon)
                }

                onGPSData(list[list.size - 1].lat, list[list.size - 1].lon)
            }
        }
    }

    override fun onGPSData(latitude: Double, longitude: Double) {
        this.sensorTimeoutManager.onGPSData(latitude, longitude);
        runOnUiThread {
            if (Position(latitude, longitude) != lastGPS) {
                var d = 0.0;
                if (this.lastGPS.lat != 0.0 && this.lastGPS.lon != 0.0) {
                    d = SphericalUtil.computeDistanceBetween(
                        this.lastGPS.toLatLng(),
                        LatLng(latitude, longitude)
                    )
                }
                lastGPS = Position(latitude, longitude)
                marker?.let { it.position = lastGPS }
                updateHeading()
                if (followMode) {
                    if (map?.initialized() ?: false) {
                        map?.moveCamera(lastGPS)
                    }
                }
                if (hasGPSFix) {
                    polyLine?.submitPoints(listOf(lastGPS))
                    this.lastTraveledDistance += d
                    this.traveled_distance.text =
                        this.formatDistance(this.lastTraveledDistance.toFloat());
                }

                if (!followMode) {
                    this.map?.invalidate()
                }
                this.tryCreateMarker()
            }
        }
    }

    override fun onConnected() {
        runOnUiThread {
            reconnectionStartTime = 0L;
            Toast.makeText(this, "已连接!", Toast.LENGTH_SHORT).show()
            switchToConnectedState()
            this.lastTraveledDistance = 0.0;
            this.traveled_distance.text = "-"
            this.lastGPS = Position(0.0, 0.0);
            this.hasGPSFix = false;

            if (preferenceManager.getConnectionVoiceMessagesEnabled()) {
                soundPool!!.play(connectedSoundId, 1f, 1f, 0, 0, 1f)
            }
        }
    }

    private fun setNextLayout() {
        var layout = preferenceManager.getMainLayout();
        layout++;
        if (layout > 1) layout = 0;
        preferenceManager.setMainLayout(layout)
        updateLayout();
        updateHorizonViewSize();
    }


    private fun updateLayout() {
        var layout = preferenceManager.getMainLayout()
        if (layout == 0) {
            mapViewHolder.visibility = View.VISIBLE;
            videoHolder.visibility = View.GONE
            topLayout.visibility = View.VISIBLE;
            bottomLayout.visibility = View.VISIBLE;
            mCameraFragment.onContainerVisibilityChange(false)
        } else if (layout == 1) {
            mapViewHolder.visibility = View.VISIBLE;
            videoHolder.visibility = View.VISIBLE
            topLayout.visibility = View.VISIBLE;
            bottomLayout.visibility = View.VISIBLE;
            mCameraFragment.onContainerVisibilityChange(true)
        } else if (layout == 2) {
            mapViewHolder.visibility = View.GONE;
            videoHolder.visibility = View.VISIBLE
            topLayout.visibility = View.GONE;
            bottomLayout.visibility = View.GONE;
            mCameraFragment.onContainerVisibilityChange(true)
        }

        updateHorizonViewSize()
    }


    private fun updateHorizonViewSize() {
        var size = 96.0f;
        if (preferenceManager.getMainLayout() == 1) {
            size = 64.0f;
        }
        var sizeInt = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            size,
            getResources().getDisplayMetrics()
        )
            .toInt();

        var lp = horizonView.getLayoutParams()
        lp.width = sizeInt;
        lp.height = sizeInt;
        horizonView.setLayoutParams(lp);
    }

    private val batInfoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctxt: Context?, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            lastPhoneBattery = level
            runOnUiThread {
                updatePhoneBattery()
            }
        }
    }

    private fun updatePhoneBattery() {
        this.phoneBattery.text = "$lastPhoneBattery%"
    }

    private fun showDialog(dialog: AlertDialog) {
        dialog.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        );
        dialog.show();
        if (!this.fullscreenWindow) {
            dialog.getWindow().decorView.systemUiVisibility = 0
        } else {
            dialog.getWindow().decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE)
        }
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    protected fun updateScreenOrientation() {
        val screenRotation: String = preferenceManager.getScreenOrientationLock()
        try {
            requestedOrientation = when (screenRotation) {
                "Portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                "Landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                "Reverse Landscape" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        } catch (e: Exception) {
        }
    }

    protected fun updateCompressionQuality() {
        val compressionQuality: String = preferenceManager.getCompressionQuality()
        this.mCameraFragment.setCompressionQuality(if (compressionQuality == "High") 2 else if (compressionQuality == "Normal") 1 else 0);
    }

    //SensorTimeoutListener
    private fun updateSetSensorGrayed(sensorId: Int) {
        var alpha = 1f;
        if (this.sensorTimeoutManager.getSensorTimeout(sensorId)) alpha = 0.5f;
        when (sensorId) {
            SensorTimeoutManager.SENSOR_GPS -> {
                this.satellites.setAlpha(alpha);
                this.traveled_distance.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_DISTANCE -> {
                this.distance.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_ALTITUDE -> {
                this.altitude.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_GPS_ALTITUDE -> {
                this.altitude_msl.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_RSSI -> {
                this.rssi.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_UP_LQ -> {
                this.upLq.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_DN_LQ -> {
                this.dnLq.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_ELRS_MODE -> {
                this.elrsRate.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_VOLTAGE -> {
                this.voltage.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_CELL_VOLTAGE -> {
                this.cell_voltage.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_CURRENT -> {
                this.current.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_SPEED -> {
                this.speed.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_AIRSPEED -> {
                this.airspeed.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_VSPEED -> {
                this.vspeed.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_THROTTLE -> {
                this.throttle.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_FUEL -> {
                this.fuel.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_RC_CHANNELS -> {
                this.rc_widget.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_STATUSTEXT -> {
                if (this.sensorTimeoutManager.getSensorTimeout(sensorId)) {
                    this.statustext.text = "";
                }
            }
            SensorTimeoutManager.SENSOR_DN_SNR -> {
                this.dnSnr.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_UP_SNR -> {
                this.upSnr.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_ANT -> {
                this.ant.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_POWER -> {
                this.power.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_RSSI_DBM_1 -> {
                this.rssiDbm1.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_RSSI_DBM_2 -> {
                this.rssiDbm2.setAlpha(alpha);
            }
            SensorTimeoutManager.SENSOR_RSSI_DBM_D -> {
                this.rssiDbmd.setAlpha(alpha);
            }
        }
    }

    //SensorTimeoutListener
    override fun onSensorTimeout(sensorId: Int) {
        runOnUiThread {
            this.updateSetSensorGrayed(sensorId);
        }
    }

    //SensorTimeoutListener
    override fun onSensorData(sensorId: Int) {
        runOnUiThread {
            this.updateSetSensorGrayed(sensorId);
        }
    }

    override fun onTelemetryRate(rate: Int) {
        runOnUiThread {
            if (rate < 1000) {
                this.tlm_rate.text = "${rate} b/s"
            } else {
                this.tlm_rate.text = "${"%.1f".format(rate / 1000f)} kb/s"
            }
        }
    }

    fun setFollowMode(mode: Boolean) {
        followMode = mode;
        if (mode) {
            this.followButton.imageAlpha = 255
        } else {
            this.followButton.imageAlpha = 128
        }
    }

    fun commitRouteLinePoints() {
        var maxCount = preferenceManager.getMaxRoutePoints()
        if ( maxCount < 0) {
            maxCount = 10000
        }
        polyLine?.commitPoints(maxCount)
    }

    fun showRenameLogDialog() {
        if (!requestWritePermission(RequestWritePermissionSequenceType.RENAME)) return;

        val currentFileName = replayFileString ?: "";
        val editText = EditText(this)
        editText.setText(currentFileName)

        this.showDialog( AlertDialog.Builder(this)
        .setTitle("Rename Log")
        .setView(editText)
        .setPositiveButton("Rename") { dialog: DialogInterface, which: Int ->
            val newFileName = editText.text.toString()
            renameLog(currentFileName, newFileName)
            dialog.dismiss()
        }
        .setNegativeButton("Cancel") { dialog: DialogInterface, which: Int ->
            dialog.dismiss()
        }.create())
    }

    private fun renameLog(currentFileName: String, newFileName: String) {
        val currentFile = File(Environment.getExternalStoragePublicDirectory("TelemetryLogs"), currentFileName)
        val newFile = File(Environment.getExternalStoragePublicDirectory("TelemetryLogs"), newFileName)

        if (currentFile.renameTo(newFile)) {
            Toast.makeText(this, "Log renamed successfully.", Toast.LENGTH_SHORT).show()

            val csvCurrentFileName = replaceExtension( currentFileName, ".csv")
            val csvNewFileName = replaceExtension( newFileName, ".csv")
            val csvCurrentFile = File(Environment.getExternalStoragePublicDirectory("TelemetryLogs"), csvCurrentFileName)
            val csvNewFile = File(Environment.getExternalStoragePublicDirectory("TelemetryLogs"), csvNewFileName)
            csvCurrentFile.renameTo(csvNewFile)

            replayFileString = newFileName;
        } else {
            Toast.makeText(this, "Failed to rename log.", Toast.LENGTH_SHORT).show()
        }
    }


    fun showDeleteLogDialog() {
        if (!requestWritePermission(RequestWritePermissionSequenceType.DELETE)) return;

        this.showDialog( AlertDialog.Builder(this)
        .setTitle("删除日志")
        .setMessage("是否确认删除此日志？")
        .setPositiveButton("删除") { dialog: DialogInterface, which: Int ->
            deleteLog(replayFileString ?: "")
            dialog.dismiss()
        }
        .setNegativeButton("取消") { dialog: DialogInterface, which: Int ->
            dialog.dismiss()
        }.create())
    }

    fun deleteLog(fileName: String)
    {
        val currentFile = File(Environment.getExternalStoragePublicDirectory("TelemetryLogs"), fileName)

        if (currentFile.delete()) {
            Toast.makeText(this, "日志成功删除", Toast.LENGTH_SHORT).show()

            val csvFileName = replaceExtension( fileName, ".csv")
            val currentFileCSV = File(Environment.getExternalStoragePublicDirectory("TelemetryLogs"), csvFileName)
            currentFileCSV.delete();

            switchToIdleState()
            replayFileString = null
        } else {
            Toast.makeText(this, "删除日志失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun replaceExtension(fileName: String, newExtension : String): String {
        val extensionSeparatorIndex = fileName.lastIndexOf(".")
        if (extensionSeparatorIndex != -1) {
            val nameWithoutExtension = fileName.substring(0, extensionSeparatorIndex)
            return nameWithoutExtension + newExtension
        }
        return fileName
    }

    fun showExportGPXDialog() {
        this.logPlayer?.stop();
        if (!requestWritePermission(RequestWritePermissionSequenceType.EXPORT_GPX)) return;

        val editText = EditText(this)
        editText.setText(this.logPlayer?.launchPointMSLAltitude.toString())
        editText.inputType = InputType.TYPE_CLASS_NUMBER
        editText.filters = arrayOf(InputFilter.LengthFilter(10)) // Set maximum input length, if needed
        editText.setSelection(editText.text.length)

        this.showDialog(AlertDialog.Builder(this)
        .setTitle("输入起飞点MSL高度, m:")
        .setView(editText)
        .setPositiveButton("OK") { dialog: DialogInterface, which: Int ->
            val enteredNumber = editText.text.toString().toFloatOrNull()
            if (enteredNumber != null) {
                val fileName = replaceExtension( replayFileString?:"", ".gpx")
                this.logPlayer?.exportGPX(fileName, enteredNumber)
                Toast.makeText(this, fileName + " saved", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        .setNegativeButton("Cancel") { dialog: DialogInterface, which: Int ->
            dialog.dismiss()
        }.create())
    }

    fun showExportKMLDialog1() {
        this.logPlayer?.stop();
        if (!requestWritePermission(RequestWritePermissionSequenceType.EXPORT_KML)) return;

        val option1 = "Clamp to ground";
        val option2 = "Relative to ground";
        val option3 = "MSL";
        val options = arrayOf(option1, option2, option3)

        val fileName = replaceExtension( replayFileString?:"", ".kml")

        this.showDialog( AlertDialog.Builder(this)
        .setTitle("Select altitude mode:")
        .setItems(options) { dialog: DialogInterface, which: Int ->
            val selectedOption = options[which]
            when (selectedOption) {
                option1 -> {
                    this.logPlayer?.exportKML(fileName, 0.0f, "clampToGround" )
                    Toast.makeText(this, fileName + " saved", Toast.LENGTH_SHORT).show()
                }
                option2 -> {
                    this.showExportKMLDialog2(fileName, "relativeToGround","Adjust track altitude, m:", 0)
                }
                option3 -> {
                    this.showExportKMLDialog2(fileName, "absolute","Enter launch point MSL altitude, m:", this.logPlayer?.launchPointMSLAltitude?:0)
                }
            }
            dialog.dismiss()
        }.create())
    }

    fun showExportKMLDialog2(fileName: String, altitudeMode: String, requestText: String, defaultValue: Int) {
        val editText = EditText(this)
        editText.setText(defaultValue.toString())
        editText.inputType = InputType.TYPE_CLASS_NUMBER
        editText.filters = arrayOf(InputFilter.LengthFilter(10)) // Set maximum input length, if needed
        editText.setSelection(editText.text.length)

        this.showDialog( AlertDialog.Builder(this)
        .setTitle(requestText)
        .setView(editText)
        .setPositiveButton("OK") { dialog: DialogInterface, which: Int ->
            val enteredNumber = editText.text.toString().toFloatOrNull()
            if (enteredNumber != null) {
                this.logPlayer?.exportKML(fileName, enteredNumber,altitudeMode)
                Toast.makeText(this, fileName + " saved", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        .setNegativeButton("Cancel") { dialog: DialogInterface, which: Int ->
            dialog.dismiss()
        }.create())
    }


    fun showSetPlaybackDurationDialog() {
        val options = resources.getStringArray(R.array.playback_durations)
        val options_values = resources.getStringArray(R.array.playback_durations_values)

        this.showDialog( AlertDialog.Builder(this)
        .setTitle("Set playback duration:")
        .setItems(options) { dialog: DialogInterface, which: Int ->
            val v = options_values[which].toInt();
            preferenceManager.setPlaybackDuration(v)
            if ( this.logPlayer?.isPlaying() ?: false ) {
                this.logPlayer?.stop();
                this.logPlayer?.startPlayback();
            }
            Toast.makeText(this, "Duration changed", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }.create())
    }

    fun requestWritePermission(seq: RequestWritePermissionSequenceType): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_DENIED
        ) {
            requestWritePermissionSequence = seq;
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_PERMISSION
            )
            return false;
        }
        return true;
    }

    override fun onUsbDeviceAttached() {
    }

    override fun onCameraConnecting(){
    }

    override fun onCameraConnected(){
        /*
        message is not sent because cameraFragment is invisible and did not created surface yet
        var layout = preferenceManager.getMainLayout()
        if (layout == 0) {
            setNextLayout()
        }
        */
    }


}
