package com.forge.hypertrophy.data.weather

import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class WeatherReading(
    val tempC: Double?,
    val uvIndex: Double?,
)

interface WeatherRepository {
    suspend fun current(latitude: Double, longitude: Double): WeatherReading?
}

/**
 * Open-Meteo forecast for the run's last fix. This is the only network call
 * in the app. A timeout or any other failure returns null.
 */
@Singleton
class OpenMeteoWeatherRepository @Inject constructor(
    clock: Clock,
) : WeatherRepository by CachingWeatherRepository(clock, HttpWeatherFetch())

internal class CachingWeatherRepository(
    private val clock: Clock,
    private val fetch: WeatherFetch,
) : WeatherRepository {
    private val cache = mutableMapOf<CacheKey, WeatherReading>()

    override suspend fun current(latitude: Double, longitude: Double): WeatherReading? = withContext(Dispatchers.IO) {
        val key = CacheKey(round2(latitude), round2(longitude), clock.instant().epochSecond / 3600)
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        val reading = runCatching { fetch.fetch(latitude, longitude) }.getOrNull() ?: return@withContext null
        synchronized(cache) { cache[key] = reading }
        reading
    }

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0

    private data class CacheKey(val latitude: Double, val longitude: Double, val hour: Long)
}

fun interface WeatherFetch {
    fun fetch(latitude: Double, longitude: Double): WeatherReading?
}

class HttpWeatherFetch : WeatherFetch {
    override fun fetch(latitude: Double, longitude: Double): WeatherReading? {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,uv_index",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.requestMethod = "GET"
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseOpenMeteo(body)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val TIMEOUT_MS = 5_000
    }
}

private val weatherJson = Json { ignoreUnknownKeys = true }

internal fun parseOpenMeteo(body: String): WeatherReading? {
    val current = weatherJson.decodeFromString<ForecastResponse>(body).current ?: return null
    if (current.temperature == null && current.uv == null) return null
    return WeatherReading(tempC = current.temperature, uvIndex = current.uv)
}

@Serializable
private data class ForecastResponse(
    val current: CurrentBlock? = null,
)

@Serializable
private data class CurrentBlock(
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("uv_index") val uv: Double? = null,
)
