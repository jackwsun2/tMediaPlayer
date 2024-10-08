package com.tans.tmediaplayer.player.renderer

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.SystemClock
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.model.ImageRawType
import com.tans.tmediaplayer.player.model.SYNC_FRAMEDUP_THRESHOLD
import com.tans.tmediaplayer.player.model.SYNC_THRESHOLD_MAX
import com.tans.tmediaplayer.player.model.SYNC_THRESHOLD_MIN
import com.tans.tmediaplayer.player.model.SyncType
import com.tans.tmediaplayer.player.model.VIDEO_EOF_MAX_CHECK_TIMES
import com.tans.tmediaplayer.player.model.VIDEO_REFRESH_RATE
import com.tans.tmediaplayer.player.playerview.tMediaPlayerView
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import com.tans.tmediaplayer.player.rwqueue.VideoFrame
import com.tans.tmediaplayer.player.rwqueue.VideoFrameQueue
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

internal class VideoRenderer(
    private val videoFrameQueue: VideoFrameQueue,
    private val videoPacketQueue: PacketQueue,
    private val player: tMediaPlayer
) {
    private val playerView: AtomicReference<tMediaPlayerView?> = AtomicReference()

    private val state: AtomicReference<RendererState> = AtomicReference(RendererState.NotInit)

    // Is read thread ready?
    private val isLooperPrepared: AtomicBoolean by lazy { AtomicBoolean(false) }

    // Video renderer thread.
    private val videoRendererThread: HandlerThread by lazy {
        object : HandlerThread("tMP_VideoRenderer", Thread.MAX_PRIORITY) {
            override fun onLooperPrepared() {
                super.onLooperPrepared()
                isLooperPrepared.set(true)
            }
        }.apply { start() }
    }

    private val canRenderStates = arrayOf(
        RendererState.Playing,
        RendererState.Eof,
        RendererState.WaitingReadableFrameBuffer
    )

    private val renderForce: AtomicBoolean = AtomicBoolean(false)

    private val waitingRenderFrames: LinkedBlockingDeque<VideoFrame> by lazy {
        LinkedBlockingDeque()
    }

    private val videoRendererHandler: Handler by lazy {
        object : Handler(videoRendererThread.looper) {

            var lastRenderFrame: LastRenderFrame = LastRenderFrame(duration = 0L, pts = 0L, serial = -1)
            var frameTimer: Long = 0

            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                synchronized(this@VideoRenderer) {
                    when (msg.what) {
                        RendererHandlerMsg.RequestRender.ordinal -> {
                            if (renderForce.get()) {
                                val frame = videoFrameQueue.dequeueReadable()
                                if (frame != null) {
                                    // lastRenderFrame = LastRenderFrame(frame)
                                    if (frame.serial != videoPacketQueue.getSerial()) {
                                        enqueueWriteableFrame(frame)
                                        MediaLog.e(TAG, "Serial changed, skip force render.")
                                        requestRender()
                                        return@synchronized
                                    }
                                    renderForce.set(false)
                                    if (!frame.isEof) {
                                        player.videoClock.setClock(frame.pts, frame.serial)
                                        player.externalClock.syncToClock(player.videoClock)
                                        renderVideoFrame(frame)
                                        MediaLog.d(TAG, "Force render video success.")
                                    } else {
                                        this@VideoRenderer.state.set(RendererState.Eof)
                                        enqueueWriteableFrame(frame)
                                        MediaLog.d(TAG, "Force render video frame eof.")
                                    }
                                } else {
                                    MediaLog.d(TAG, "Force render waiting readable video frame")
                                }
                            } else {
                                val state = getState()
                                val mediaInfo = player.getMediaInfo()
                                if (mediaInfo != null && state in canRenderStates) {
                                    val frame = videoFrameQueue.peekReadable()
                                    if (frame != null) {
                                        if (frame.serial != videoPacketQueue.getSerial()) {
                                            lastRenderFrame = LastRenderFrame(frame)
                                            val frameToCheck = videoFrameQueue.dequeueReadable()
                                            if (frameToCheck === frame) {
                                                enqueueWriteableFrame(frame)
                                            } else {
                                                MediaLog.e(TAG, "Wrong render frame: $frame")
                                            }
                                            MediaLog.d(TAG, "Serial changed, skip render.")
                                            requestRender()
                                            return@synchronized
                                        }
                                        if (!frame.isEof) {
                                            if (state == RendererState.WaitingReadableFrameBuffer || state == RendererState.Eof) {
                                                this@VideoRenderer.state.set(RendererState.Playing)
                                            }
                                            val lastFrame = lastRenderFrame
                                            if (frame.serial != lastFrame.serial) {
                                                MediaLog.d(TAG, "Serial changed, reset frame timer.")
                                                frameTimer = SystemClock.uptimeMillis()
                                            }
                                            val lastDuration = frameDuration(lastFrame, frame)
                                            val delay = computeTargetDelay(lastDuration)
                                            val time = SystemClock.uptimeMillis()
                                            if (time < frameTimer + delay) {
                                                val remainingTime = min(frameTimer + delay - time, VIDEO_REFRESH_RATE)
                                                MediaLog.d(TAG, "Frame=${frame.pts}, need delay ${remainingTime}ms to display.")
                                                requestRender(remainingTime)
                                                return@synchronized
                                            }
                                            val frameToCheck = videoFrameQueue.dequeueReadable()
                                            if (frame !== frameToCheck) {
                                                MediaLog.e(TAG, "Wrong render frame: $frame")
                                                requestRender()
                                                if (frameToCheck != null) {
                                                    enqueueWriteableFrame(frameToCheck)
                                                }
                                                return@synchronized
                                            }
                                            lastRenderFrame = LastRenderFrame(frame)
                                            frameTimer += delay
                                            if (delay > 0 && time - frameTimer > SYNC_THRESHOLD_MAX) {
                                                MediaLog.e(TAG, "Behind time ${time - frameTimer}ms reset frame timer.")
                                                frameTimer = time
                                            }
                                            player.videoClock.setClock(frame.pts, frame.serial)
                                            player.externalClock.syncToClock(player.videoClock)

                                            val nextFrame = videoFrameQueue.peekReadable()
                                            if (nextFrame != null && !nextFrame.isEof) {
                                                val duration = frameDuration(lastRenderFrame, nextFrame)
                                                if (player.getSyncType() != SyncType.VideoMaster && time > frameTimer + duration) {
                                                    MediaLog.e(TAG, "Drop next frame: ${nextFrame.pts}")
                                                    val nextFrameToCheck = videoFrameQueue.dequeueReadable()
                                                    if (nextFrameToCheck === nextFrame) {
                                                        enqueueWriteableFrame(nextFrame)
                                                    } else {
                                                        if (nextFrameToCheck != null) {
                                                            enqueueWriteableFrame(nextFrameToCheck)
                                                        }
                                                        MediaLog.e(TAG, "Wrong render frame: $nextFrame")
                                                    }
                                                }
                                            }

                                            renderVideoFrame(frame)
                                            requestRender()
                                        } else {
                                            val frameToCheck = videoFrameQueue.dequeueReadable()
                                            if (frameToCheck === frame) {
                                                var checkTimes = 0
                                                while (waitingRenderFrames.isNotEmpty()) {
                                                    MediaLog.d(TAG, "Waiting video finish, bufferCount=${waitingRenderFrames.size}, checkTimes=${checkTimes++}")
                                                    try {
                                                        Thread.sleep(10)
                                                    } catch (e: Throwable) {
                                                        MediaLog.e(TAG, "Sleep error: ${e.message}", e)
                                                        break
                                                    }
                                                    if (checkTimes >= VIDEO_EOF_MAX_CHECK_TIMES) {
                                                        MediaLog.e(TAG, "Waiting video renderer max times $VIDEO_EOF_MAX_CHECK_TIMES, bufferCount=${waitingRenderFrames.size}")
                                                        break
                                                    }
                                                }
                                                while (waitingRenderFrames.isNotEmpty()) {
                                                    val b = waitingRenderFrames.pollFirst()
                                                    if (b != null) {
                                                        enqueueWriteableFrame(b)
                                                    }
                                                }
                                                this@VideoRenderer.state.set(RendererState.Eof)
                                                enqueueWriteableFrame(frame)
                                                MediaLog.d(TAG, "Render video frame eof.")
                                            } else {
                                                if (frameToCheck != null) {
                                                    enqueueWriteableFrame(frameToCheck)
                                                }
                                                requestRender()
                                            }
                                        }
                                    } else {
                                        if (state == RendererState.Playing) {
                                            this@VideoRenderer.state.set(RendererState.WaitingReadableFrameBuffer)
                                        }
                                        MediaLog.d(TAG, "Waiting readable video frame.")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            fun renderVideoFrame(frame: VideoFrame) {
                val playerView = this@VideoRenderer.playerView.get()
                if (playerView != null) {
                    when (frame.imageType) {
                        ImageRawType.Yuv420p -> {
                            val y = frame.yBuffer
                            val u = frame.uBuffer
                            val v = frame.vBuffer
                            if (y != null && u != null && v != null) {
                                waitingRenderFrames.addLast(frame)
                                playerView.requestRenderYuv420pFrame(
                                    width = frame.width,
                                    height = frame.height,
                                    yBytes = y,
                                    uBytes = u,
                                    vBytes = v
                                ) {
                                    renderViewCallback()
                                }
                            } else {
                                MediaLog.e(TAG, "Wrong ${frame.imageType} image.")
                                enqueueWriteableFrame(frame)
                            }
                        }
                        ImageRawType.Nv12 -> {
                            val y = frame.yBuffer
                            val uv = frame.uvBuffer
                            if (y != null && uv != null) {
                                waitingRenderFrames.addLast(frame)
                                playerView.requestRenderNv12Frame(
                                    width = frame.width,
                                    height = frame.height,
                                    yBytes = y,
                                    uvBytes = uv
                                ) {
                                    renderViewCallback()
                                }
                            } else {
                                MediaLog.e(TAG, "Wrong ${frame.imageType} image.")
                                enqueueWriteableFrame(frame)
                            }
                        }
                        ImageRawType.Nv21 -> {
                            val y = frame.yBuffer
                            val vu = frame.uvBuffer
                            if (y != null && vu != null) {
                                waitingRenderFrames.addLast(frame)
                                playerView.requestRenderNv21Frame(
                                    width = frame.width,
                                    height = frame.height,
                                    yBytes = y,
                                    vuBytes = vu) {
                                    renderViewCallback()
                                }
                            } else {
                                MediaLog.e(TAG, "Wrong ${frame.imageType} image.")
                                enqueueWriteableFrame(frame)
                            }
                        }
                        ImageRawType.Rgba -> {
                            val rgba = frame.rgbaBuffer
                            if (rgba != null) {
                                waitingRenderFrames.addLast(frame)
                                playerView.requestRenderRgbaFrame(
                                    width = frame.width,
                                    height = frame.height,
                                    imageBytes = rgba
                                ) {
                                    renderViewCallback()
                                }
                            } else {
                                MediaLog.e(TAG, "Wrong ${frame.imageType} image.")
                                enqueueWriteableFrame(frame)
                            }
                        }
                        ImageRawType.Unknown -> {
                            enqueueWriteableFrame(frame)
                        }
                    }
                } else {
                    enqueueWriteableFrame(frame)
                }
            }

            fun frameDuration(current: LastRenderFrame, next: VideoFrame): Long {
                return if (current.serial == next.serial) {
                    val duration = next.pts - current.pts
                    if (duration <= 0) {
                        current.duration
                    } else {
                        duration
                    }
                } else {
                    0L
                }
            }

            fun computeTargetDelay(delay: Long): Long {
                val syncType = player.getSyncType()
                return if (syncType != SyncType.VideoMaster) {
                    val diff = player.videoClock.getClock() - player.getMasterClock()
                    val threshold: Long = max(min(delay, SYNC_THRESHOLD_MAX), SYNC_THRESHOLD_MIN)
                    if (diff <= - threshold) {
                        max(0L, delay + diff)
                    } else if (diff >= threshold && delay >= SYNC_FRAMEDUP_THRESHOLD) {
                        delay + diff
                    } else if (diff >= threshold) {
                        delay * 2L
                    } else {
                        delay
                    }
                } else {
                    delay
                }
            }
        }
    }

    init {
        videoRendererThread
        while (!isLooperPrepared.get()) {}
        videoRendererHandler
        state.set(RendererState.Paused)
        MediaLog.d(TAG, "Video renderer inited.")
    }

    fun play() {
        synchronized(this) {
            val state = getState()
            if (state == RendererState.Paused ||
                state == RendererState.Eof
            ) {
                this.state.set(RendererState.Playing)
                requestRender()
            } else {
                MediaLog.e(TAG, "Play error, because of state: $state")
            }
        }
    }

    fun pause() {
        synchronized(this) {
            val state = getState()
            if (state in canRenderStates) {
                this.state.set(RendererState.Paused)
            } else {
                MediaLog.e(TAG, "Pause error, because of state: $state")
            }
        }
    }

    fun readableFrameReady() {
        val state = getState()
        if (state == RendererState.WaitingReadableFrameBuffer || renderForce.get()) {
            requestRender()
        } else {
            MediaLog.d(TAG, "Skip handle readable video frame ready, because of state: $state")
        }
    }

    fun requestRenderForce() {
        val state = getState()
        if (state != RendererState.NotInit && state != RendererState.Released) {
            if (renderForce.compareAndSet(false, true)) {
                requestRender()
            } else {
                MediaLog.e(TAG, "Force render error, already have a force render task.")
            }
        } else {
            MediaLog.e(TAG, "Force render error, because of state: $state")
        }
    }

    fun release() {
        synchronized(this) {
            val state = getState()
            if (state != RendererState.NotInit && state != RendererState.Released) {
                this.state.set(RendererState.Released)
                this.playerView.set(null)
                while (waitingRenderFrames.isNotEmpty()) {
                    val b = waitingRenderFrames.pollFirst()
                    if (b != null) {
                        videoFrameQueue.enqueueWritable(b)
                    }
                }
                videoRendererThread.quit()
                videoRendererThread.quitSafely()
                MediaLog.d(TAG, "Video renderer released.")
            } else {
                MediaLog.e(TAG, "Release error, because of state: $state")
            }
        }
    }

    fun attachPlayerView(view: tMediaPlayerView?) {
        this.playerView.set(view)
    }

    fun getState(): RendererState = state.get()

    private fun requestRender(delay: Long = 0) {
        val state = getState()
        if (state in canRenderStates || renderForce.get()) {
            videoRendererHandler.removeMessages(RendererHandlerMsg.RequestRender.ordinal)
            videoRendererHandler.sendEmptyMessageDelayed(RendererHandlerMsg.RequestRender.ordinal, delay)
        } else {
            MediaLog.e(TAG, "Request render error, because of state: $state")
        }
    }

    private fun enqueueWriteableFrame(f: VideoFrame) {
        videoFrameQueue.enqueueWritable(f)
        player.writeableVideoFrameReady()
    }

    private fun renderViewCallback() {
        renderCallbackExecutor.execute {
            val f = waitingRenderFrames.pollFirst()
            if (f != null) {
                enqueueWriteableFrame(f)
            }
        }
    }

    companion object {
        private const val TAG = "VideoRenderer"

       private class LastRenderFrame {
           val pts: Long
           val serial: Int
           val duration: Long

           constructor(frame: VideoFrame) {
               pts = frame.pts
               serial = frame.serial
               duration = frame.duration
           }

           constructor(pts: Long, serial: Int, duration: Long) {
               this.pts = pts
               this.serial = serial
               this.duration = duration
           }
       }
    }
}