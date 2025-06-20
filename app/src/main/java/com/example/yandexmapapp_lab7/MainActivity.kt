package com.example.yandexmapapp_lab7

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.DrivingOptions
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.directions.driving.DrivingRouter
import com.yandex.mapkit.directions.driving.DrivingSession
import com.yandex.mapkit.layers.ObjectEvent
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.map.PolylineMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.user_location.UserLocationLayer
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider

class MainActivity : AppCompatActivity(), UserLocationObjectListener {

    private lateinit var mapView: MapView
    private lateinit var userLocationLayer: UserLocationLayer
    private val LOCATION_PERMISSION_REQUEST_CODE = 123
    private var firstPoint: Point? = null
    private var secondPoint: Point? = null
    private var drivingRouter: DrivingRouter? = null
    private var drivingSession: DrivingSession? = null
    private var mapObjects: MapObjectCollection? = null
    private var routePolyline: PolylineMapObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapKitFactory.initialize(this)
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapview)
        mapView.map.move(
            CameraPosition(Point(55.751574, 37.573856), 11.0f, 0.0f, 0.0f)
        )

        val zoomToKemerovoButton: Button = findViewById(R.id.zoomToKemerovoButton)
        zoomToKemerovoButton.setOnClickListener {
            zoomToKemerovo()
        }

        val zoomToUserLocationButton: Button = findViewById(R.id.zoomToUserLocationButton)
        zoomToUserLocationButton.setOnClickListener {
            zoomToUserLocation()
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            setupUserLocationLayer()
        }

        mapView.map.addInputListener(object : InputListener {
            override fun onMapTap(map: Map, point: Point) {
                // Обработка короткого нажатия (не требуется в данном случае)
            }

            override fun onMapLongTap(map: Map, point: Point) {
                handleLongTap(point)
            }
        })

        val clearRouteButton: Button = findViewById(R.id.clearRouteButton)
        clearRouteButton.setOnClickListener {
            clearRoute()
        }
    }

    private fun zoomToKemerovo() {
        val kemerovoPoint = Point(55.354993, 86.085805)
        mapView.map.move(
            CameraPosition(kemerovoPoint, 15.0f, 0.0f, 0.0f)
        )
    }

    private fun zoomToUserLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
            val location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            if (location != null) {
                val userPoint = Point(location.latitude, location.longitude)
                mapView.map.move(
                    CameraPosition(userPoint, 15.0f, 0.0f, 0.0f)
                )
            } else {
                Toast.makeText(this, "Не удалось получить местоположение", Toast.LENGTH_SHORT).show()
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun setupUserLocationLayer() {
        val mapKit = MapKitFactory.getInstance()
        userLocationLayer = mapKit.createUserLocationLayer(mapView.mapWindow)
        userLocationLayer.isVisible = true
        userLocationLayer.isHeadingEnabled = true
        userLocationLayer.setObjectListener(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupUserLocationLayer()
                zoomToUserLocation()
            } else {
                Toast.makeText(
                    this,
                    "Разрешение на местоположение отклонено",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handleLongTap(point: Point) {
        if (firstPoint == null) {
            firstPoint = point
            // Отобразите первую точку на карте
            mapView.map.mapObjects.addPlacemark(point)
            Toast.makeText(this, "Первая точка выбрана", Toast.LENGTH_SHORT).show()
        } else if (secondPoint == null) {
            secondPoint = point
            // Отобразите вторую точку на карте
            mapView.map.mapObjects.addPlacemark(point)
            Toast.makeText(this, "Вторая точка выбрана", Toast.LENGTH_SHORT).show()
            buildRoute()
        } else {
            Toast.makeText(this, "Уже выбраны две точки. Очистите маршрут, чтобы выбрать новые.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildRoute() {
        if (firstPoint != null && secondPoint != null) {
            if (drivingRouter == null) {
                drivingRouter = DirectionsFactory.getInstance().createDrivingRouter()
                mapObjects = mapView.map.mapObjects.addCollection()
            }

            val drivingOptions = DrivingOptions().apply {
                avoidTolls = true
            }

            val requestPoints = arrayListOf(
                RequestPoint(
                    firstPoint!!,
                    RequestPointType.WAYPOINT,
                    null
                ),
                RequestPoint(
                    secondPoint!!,
                    RequestPointType.WAYPOINT,
                    null
                )
            )

            drivingSession = drivingRouter!!.requestRoutes(
                requestPoints,
                drivingOptions,
                drivingRouteListener
            )
            Toast.makeText(this, "Построение маршрута...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Выберите две точки для построения маршрута", Toast.LENGTH_SHORT).show()
        }
    }

    private val drivingRouteListener = object : DrivingSession.DrivingRouteListener {
        override fun onDrivingRoutes(drivingRoutes: MutableList<DrivingRoute>) {
            if (drivingRoutes.isNotEmpty()) {
                val route = drivingRoutes[0]
                routePolyline?.let { mapObjects?.remove(it) }

                routePolyline = mapObjects?.addPolyline(route.geometry)
                routePolyline?.strokeColor = Color.BLUE
                routePolyline?.strokeWidth = 5f
            } else {
                Toast.makeText(this@MainActivity, "Маршрут не найден", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onDrivingRoutesError(error: Error) {
            val errorMessage = when (error.remoteError?.message) {
                else -> "Неизвестная ошибка"
            }
            Toast.makeText(this@MainActivity, "Ошибка: $errorMessage", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearRoute() {
        firstPoint = null
        secondPoint = null
        mapObjects?.clear()
        mapObjects = mapView.map.mapObjects.addCollection()
        Toast.makeText(this, "Маршрут очищен", Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        MapKitFactory.getInstance().onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
    }

    override fun onObjectAdded(userLocationView: UserLocationView) {
        userLocationView.pin.setIcon(ImageProvider.fromResource(this, R.drawable.user_location))
        userLocationView.arrow.setIcon(ImageProvider.fromResource(this, R.drawable.user_arrow))
    }

    override fun onObjectRemoved(p0: UserLocationView) {}

    override fun onObjectUpdated(p0: UserLocationView, p1: ObjectEvent) {}
}