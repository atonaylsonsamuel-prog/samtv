package com.samdevelop.samtv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.samdevelop.samtv.core.model.StreamSource
import com.samdevelop.samtv.data.remote.OkHttpHttpClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Cria instâncias de ExoPlayer configuradas para IPTV:
 *  - rede via OkHttp (mesma pilha TLS do restante do app);
 *  - fontes HTTPS não seguem redirecionamento para HTTP (sem rebaixamento silencioso);
 *  - cabeçalhos por stream (User-Agent/Referer declarados na playlist);
 *  - extrator TS tolerante (muitas fontes ao vivo usam MPEG-TS progressivo).
 */
@OptIn(UnstableApi::class)
class PlayerFactory(private val context: Context, baseClient: OkHttpClient) {

    private val streamClientHttps = baseClient.newBuilder()
        .followRedirects(true)
        .followSslRedirects(false) // https → http bloqueado
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS) // streams ao vivo não têm duração definida
        .build()

    private val streamClientHttp = streamClientHttps.newBuilder()
        .followSslRedirects(true) // fonte já é HTTP consentido; permitir subir para HTTPS
        .build()

    fun create(): PlayerHandle {
        val httpsFactory = OkHttpDataSource.Factory(streamClientHttps).setUserAgent(OkHttpHttpClient.USER_AGENT)
        val httpFactory = OkHttpDataSource.Factory(streamClientHttp).setUserAgent(OkHttpHttpClient.USER_AGENT)
        val switching = SwitchingDataSourceFactory(httpsFactory)

        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
            .setConstantBitrateSeekingEnabled(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 2_000, 4_000)
            .build()

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(switching, extractors))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        return PlayerHandle(player) { source ->
            val target = if (source.url.startsWith("https://", ignoreCase = true)) httpsFactory else httpFactory
            target.setDefaultRequestProperties(source.httpHeaders)
            switching.delegate = target
        }
    }
}

/** Player + função que prepara a pilha de rede para um stream específico. */
class PlayerHandle(val player: ExoPlayer, val configureFor: (StreamSource) -> Unit)

@OptIn(UnstableApi::class)
private class SwitchingDataSourceFactory(@Volatile var delegate: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = delegate.createDataSource()
}
