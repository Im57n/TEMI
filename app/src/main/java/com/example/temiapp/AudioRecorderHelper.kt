package com.example.temiapp

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.abs

class AudioRecorderHelper(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false
    private var outputFile: File? = null
    private var recordingThread: Thread? = null

    // 開始錄音，並在偵測到靜音(說完話)時，透過 callback 回傳生成的 WAV 檔案
    @SuppressLint("MissingPermission")
    fun startRecording(onSilenceDetected: (File?) -> Unit) {
        stopRecording() // 先確保前一次已關閉

        val file = File(context.cacheDir, "hospital_asr_${System.currentTimeMillis()}.wav")
        outputFile = file

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread {
                processAudioData(file, bufferSize, sampleRate, onSilenceDetected)
            }
            recordingThread?.start()
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord 初始化失敗", e)
            onSilenceDetected(null)
        }
    }

    private fun processAudioData(
        file: File,
        bufferSize: Int,
        sampleRate: Int,
        onSilenceDetected: (File?) -> Unit
    ) {
        val data = ShortArray(bufferSize / 2) // 讀取 16-bit PCM
        val rawFile = File(file.absolutePath + ".raw")

        var hasSpoken = false
        var silenceStartTime = 0L
        val recordingStartTime = System.currentTimeMillis()

        try {
            FileOutputStream(rawFile).use { fos ->
                while (isRecording) {
                    val readCount = audioRecord?.read(data, 0, data.size) ?: 0
                    if (readCount > 0) {
                        var maxAmplitude = 0
                        for (i in 0 until readCount) {
                            val absVal = abs(data[i].toInt())
                            if (absVal > maxAmplitude) maxAmplitude = absVal

                            // 寫入 PCM 數據 (Little Endian)
                            fos.write(data[i].toInt().and(0xFF))
                            fos.write(data[i].toInt().shr(8).and(0xFF))
                        }

                        val currentTime = System.currentTimeMillis()

                        // 靜音檢測 (VAD) 邏輯
                        if (maxAmplitude > HospitalAsrConfig.VAD_SILENCE_THRESHOLD) {
                            hasSpoken = true
                            silenceStartTime = 0L // 有聲音，重置靜音計時器
                        } else {
                            if (hasSpoken) {
                                if (silenceStartTime == 0L) {
                                    silenceStartTime = currentTime
                                } else if (currentTime - silenceStartTime > HospitalAsrConfig.VAD_MAX_SILENCE_MS) {
                                    Log.d(TAG, "偵測到靜音，結束錄音")
                                    isRecording = false
                                    break
                                }
                            }
                        }

                        // 最大時間防呆保護
                        if (currentTime - recordingStartTime > HospitalAsrConfig.VAD_MAX_RECORDING_MS) {
                            Log.d(TAG, "達到最長錄製時間")
                            isRecording = false
                            break
                        }
                    }
                }
            }

            // 將 RAW 檔加上 WAV 檔頭轉換成合格的 WAV 檔
            rawToWave(rawFile, file, sampleRate)

            // 觸發完成事件
            onSilenceDetected(file)

        } catch (e: Exception) {
            Log.e(TAG, "錄音處理失敗", e)
            onSilenceDetected(null)
        } finally {
            runCatching { rawFile.delete() }
            cleanupAudioRecord()
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingThread?.join(500) // 等待線程結束
        cleanupAudioRecord()
    }

    private fun cleanupAudioRecord() {
        try {
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "釋放資源失敗", e)
        } finally {
            audioRecord = null
        }
    }

    private fun rawToWave(rawFile: File, waveFile: File, sampleRate: Int) {
        val rawData = ByteArray(rawFile.length().toInt())
        FileInputStream(rawFile).use { it.read(rawData) }
        FileOutputStream(waveFile).use { out ->
            val totalAudioLen = rawData.size.toLong()
            val totalDataLen = totalAudioLen + 36
            val byteRate = (sampleRate * 2).toLong() // 16-bit = 2 bytes, mono = 1 channel

            val header = ByteArray(44)
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = (totalDataLen shr 8 and 0xff).toByte()
            header[6] = (totalDataLen shr 16 and 0xff).toByte()
            header[7] = (totalDataLen shr 24 and 0xff).toByte()
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
            header[20] = 1; header[21] = 0
            header[22] = 1; header[23] = 0 // 1 channel
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = (sampleRate shr 8 and 0xff).toByte()
            header[26] = (sampleRate shr 16 and 0xff).toByte()
            header[27] = (sampleRate shr 24 and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = (byteRate shr 8 and 0xff).toByte()
            header[30] = (byteRate shr 16 and 0xff).toByte()
            header[31] = (byteRate shr 24 and 0xff).toByte()
            header[32] = 2; header[33] = 0 // block align
            header[34] = 16; header[35] = 0 // bits per sample
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = (totalAudioLen shr 8 and 0xff).toByte()
            header[42] = (totalAudioLen shr 16 and 0xff).toByte()
            header[43] = (totalAudioLen shr 24 and 0xff).toByte()

            out.write(header, 0, 44)
            out.write(rawData)
        }
    }

    fun cancelRecording() {
        stopRecording()
        runCatching { outputFile?.delete() }
    }

    companion object {
        private const val TAG = "AudioRecorderHelper"
    }
}