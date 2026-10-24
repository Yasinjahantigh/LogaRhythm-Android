package com.yasinjahantigh.logarhythm.domain.repository

import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.smart.SmartMix
import com.yasinjahantigh.logarhythm.domain.smart.SmartMixType
import kotlinx.coroutines.flow.Flow

interface SmartMixRepository {
    fun getSmartMixes(): Flow<List<SmartMix>>
    fun getSmartMix(type: SmartMixType): Flow<SmartMix?>
    fun getSmartMixTracks(type: SmartMixType): Flow<List<Track>>
}
