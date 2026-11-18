package kr.kw.libri.stretch

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * 효과음 — 음원 파일 없이 짧은 음을 직접 합성해 들려준다 (카운트다운, 통과, 부딪힘, 완료).
 * 소리가 안 나도 게임은 계속되어야 하므로 오디오 오류는 기록만 하고 넘어간다.
 */
class StretchSfx {
    var enabled = true

    private class Note(val freq: Float, val startSec: Float, val durSec: Float, val gain: Float = 1f)

    private val tracks = HashMap<String, AudioTrack>()

    fun tick() = play("tick") { tones(Note(784f, 0f, 0.11f, 0.7f)) }
    fun go() = play("go") { tones(Note(1047f, 0f, 0.26f, 0.8f), Note(1568f, 0f, 0.26f, 0.35f)) }
    fun good() = play("good") { tones(Note(659f, 0f, 0.16f, 0.7f), Note(880f, 0.09f, 0.24f, 0.7f)) }
    fun perfect() = play("perfect") {
        tones(Note(784f, 0f, 0.16f, 0.65f), Note(988f, 0.08f, 0.16f, 0.65f), Note(1319f, 0.16f, 0.34f, 0.7f), Note(2637f, 0.16f, 0.3f, 0.18f))
    }
    fun miss() = play("miss") { thud() }
    fun finish() = play("finish") {
        tones(
            Note(523f, 0f, 0.2f, 0.6f), Note(659f, 0.13f, 0.2f, 0.6f), Note(784f, 0.26f, 0.2f, 0.6f),
            Note(1047f, 0.39f, 0.55f, 0.7f), Note(1319f, 0.39f, 0.55f, 0.3f),
        )
    }

    fun release() {
        for (t in tracks.values) runCatching { t.release() }
        tracks.clear()
    }

    private fun play(name: String, synth: () -> ShortArray) {
        if (!enabled) return
        try {
            val track = tracks.getOrPut(name) { trackOf(synth()) }
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        } catch (e: Exception) {
            Log.w(TAG, "sound '$name' failed", e)
        }
    }

    private fun trackOf(samples: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }

    /** 맑은 종소리 느낌의 음 — 사인파 + 약한 배음, 빠르게 올라와 서서히 사라진다 */
    private fun tones(vararg notes: Note): ShortArray {
        val total = notes.maxOf { it.startSec + it.durSec } + 0.03f
        val buf = FloatArray((total * RATE).toInt())
        for (n in notes) {
            val start = (n.startSec * RATE).toInt()
            val len = (n.durSec * RATE).toInt()
            for (i in 0 until len) {
                val t = i / RATE.toFloat()
                val attack = (t / 0.006f).coerceAtMost(1f)
                val env = attack * exp(-t / (n.durSec * 0.34f)) * (1f - i / len.toFloat())
                val ph = 2.0 * PI * n.freq * t
                buf[start + i] += (sin(ph) + 0.28 * sin(2 * ph) + 0.1 * sin(3 * ph)).toFloat() * env * n.gain
            }
        }
        return pcm(buf)
    }

    /** 책장에 부딪히는 둔탁한 소리 — 낮게 떨어지는 음 + 짧은 잡음 */
    private fun thud(): ShortArray {
        val rnd = Random(3)
        val len = (0.34f * RATE).toInt()
        val buf = FloatArray(len)
        var phase = 0.0
        var noise = 0f
        for (i in 0 until len) {
            val t = i / RATE.toFloat()
            val freq = 150f * exp(-t * 7f) + 48f
            phase += 2.0 * PI * freq / RATE
            noise += ((rnd.nextFloat() * 2f - 1f) - noise) * 0.25f        // 고음을 깎은 잡음
            buf[i] = (sin(phase).toFloat() * exp(-t * 9f) + noise * exp(-t * 26f) * 0.8f) * 0.95f
        }
        return pcm(buf)
    }

    private fun pcm(buf: FloatArray): ShortArray {
        val peak = buf.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1f)
        return ShortArray(buf.size) { (buf[it] / peak * VOLUME * Short.MAX_VALUE).toInt().toShort() }
    }

    private companion object {
        const val TAG = "StretchSfx"
        const val RATE = 22050
        const val VOLUME = 0.55f
    }
}
