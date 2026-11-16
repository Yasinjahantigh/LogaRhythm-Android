package com.yasinjahantigh.logarhythm.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lightweight 16-bit PCM stereo processor supporting L/R channel balance and Mono downmix.
 * Passes audio straight through (inactive) when centered stereo is active.
 */
@OptIn(UnstableApi::class)
@Singleton
class StereoBalanceAudioProcessor @Inject constructor() : BaseAudioProcessor() {

    @Volatile
    var balance: Float = 0f
        private set

    @Volatile
    var mono: Boolean = false
        private set

    fun setStereoBalance(value: Float) {
        balance = value.coerceIn(-1f, 1f)
    }

    fun setMonoAudio(enabled: Boolean) {
        mono = enabled
    }

    override fun isActive(): Boolean =
        super.isActive() && (mono || abs(balance) > 0.01f)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val bal = balance
        val monoOn = mono
        if (!monoOn && abs(bal) <= 0.01f) {
            val out = replaceOutputBuffer(remaining)
            out.put(inputBuffer)
            out.flip()
            return
        }

        // Process whole stereo 16-bit frames (4 bytes per frame: L_lo L_hi R_lo R_hi).
        val frameBytes = remaining - (remaining % 4)
        if (frameBytes <= 0) return
        val out = replaceOutputBuffer(frameBytes)
        processStereo16BitBuffer(inputBuffer, out, frameBytes, bal, monoOn)
        out.flip()
    }

    companion object {
        fun gainsForBalance(balance: Float): Pair<Float, Float> {
            val b = balance.coerceIn(-1f, 1f)
            val leftGain = if (b > 0f) 1f - b else 1f
            val rightGain = if (b < 0f) 1f + b else 1f
            return leftGain to rightGain
        }

        fun processStereo16BitBuffer(
            input: ByteBuffer,
            output: ByteBuffer,
            byteCount: Int,
            balance: Float,
            mono: Boolean
        ) {
            val (leftGain, rightGain) = gainsForBalance(balance)
            val end = input.position() + byteCount
            while (input.position() < end) {
                val left = input.getShort().toInt()
                val right = input.getShort().toInt()
                val baseL: Float
                val baseR: Float
                if (mono) {
                    val mid = (left + right) * 0.5f
                    baseL = mid
                    baseR = mid
                } else {
                    baseL = left.toFloat()
                    baseR = right.toFloat()
                }
                val outL = (baseL * leftGain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                val outR = (baseR * rightGain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                output.putShort(outL)
                output.putShort(outR)
            }
        }
    }
}
