package ai.meetcord.audio

import java.util.concurrent.ArrayBlockingQueue

/**
 * A memory-safe object pool for audio buffers.
 * To prevent the Android Low Memory Killer Daemon (LMKD) from terminating the app,
 * we strictly avoid allocating new short arrays for every audio chunk.
 * Instead, we recycle them.
 */
object AudioBufferPool {
    // 16kHz * 100ms = 1600 samples. Since it's 16-bit mono, 1 sample = 1 short.
    const val BUFFER_SIZE = 1600 
    
    // Pool size determines how many buffers can be 'in flight' (e.g., being processed by JNI)
    private const val POOL_SIZE = 32
    
    private val pool = ArrayBlockingQueue<ShortArray>(POOL_SIZE)

    init {
        // Pre-allocate the buffers
        for (i in 0 until POOL_SIZE) {
            pool.add(ShortArray(BUFFER_SIZE))
        }
    }

    /**
     * Obtains a buffer from the pool. If the pool is empty, it allocates a new one,
     * but this should rarely happen if consumer speed > producer speed.
     */
    fun obtain(): ShortArray {
        return pool.poll() ?: ShortArray(BUFFER_SIZE)
    }

    /**
     * Recycles a buffer back to the pool. MUST be called by the consumer (JNI wrapper)
     * once it's done copying the PCM data to whisper.cpp.
     */
    fun recycle(buffer: ShortArray) {
        if (buffer.size == BUFFER_SIZE) {
            pool.offer(buffer) // Will be rejected safely if pool is full
        }
    }
}
