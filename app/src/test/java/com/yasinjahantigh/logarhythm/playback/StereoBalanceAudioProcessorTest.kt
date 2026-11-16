package com.yasinjahantigh.logarhythm.playback

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StereoBalanceAudioProcessorTest {

    @Test
    fun balancePansChannelsCorrectly() {
        val (fullLeftL, fullLeftR) = StereoBalanceAudioProcessor.gainsForBalance(-1.0f)
        assertEquals(1.0f, fullLeftL, 0.001f)
        assertEquals(0.0f, fullLeftR, 0.001f)

        val (fullRightL, fullRightR) = StereoBalanceAudioProcessor.gainsForBalance(1.0f)
        assertEquals(0.0f, fullRightL, 0.001f)
        assertEquals(1.0f, fullRightR, 0.001f)

        val (centerL, centerR) = StereoBalanceAudioProcessor.gainsForBalance(0f)
        assertEquals(1.0f, centerL, 0.001f)
        assertEquals(1.0f, centerR, 0.001f)
    }

    @Test
    fun monoDownmixAveragesLeftAndRightSamples() {
        val input = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(1000)
            putShort(-400)
            flip()
        }
        val output = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        StereoBalanceAudioProcessor.processStereo16BitBuffer(
            input = input,
            output = output,
            byteCount = 4,
            balance = 0f,
            mono = true
        )
        output.flip()
        assertEquals(300.toShort(), output.getShort())
        assertEquals(300.toShort(), output.getShort())
    }
}
