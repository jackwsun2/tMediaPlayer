//
// Created by pengcheng.tan on 2023/7/13.
//
#include <tmediaplayer.h>
#include <jni.h>
#include <string>
extern "C" {
#include "libavcodec/jni.h"
}


extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_createPlayerNative(
        JNIEnv * env,
        jobject j_player) {
    JavaVM * jvm = nullptr;
    env->GetJavaVM(&jvm);
    auto player = new tMediaPlayerContext;
    player->jplayer = j_player;
    player->jvm = jvm;
    player->jniEnv = env;
    return reinterpret_cast<jlong>(player);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_prepareNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player,
        jstring file_path,
        jboolean requestHw,
        jint targetAudioChannels) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    if (player == nullptr) {
        return OptFail;
    }
    av_jni_set_java_vm(player->jvm, nullptr);
    const char * file_path_chars = env->GetStringUTFChars(file_path, 0);
    return player->prepare(file_path_chars, requestHw, targetAudioChannels);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_durationNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->duration;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_videoWidthNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_width;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_videoHeightNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_height;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_videoFpsNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_fps;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_videoDurationNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->video_duration;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_audioChannelsNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_channels;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_audioPreSampleBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_pre_sample_bytes;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_audioSampleRateNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_simple_rate;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_audioDurationNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->audio_duration;
}


extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_allocDecodeDataNative(
        JNIEnv * env,
        jobject j_player) {
    auto buffer = allocDecodeBuffer();
    return reinterpret_cast<jlong>(buffer);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_isVideoBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    return buffer->type == BufferTypeVideo;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_isLastFrameBufferNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    return buffer->is_last_frame;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_getVideoWidthNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        return buffer->videoBuffer->width;
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_getVideoHeightNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        return buffer->videoBuffer->height;
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_getPtsNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    return buffer->pts;
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_getVideoFrameRgbaBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        if (buffer->videoBuffer->type == Rgba) {
            env->SetByteArrayRegion(j_bytes, 0, buffer->videoBuffer->rgbaSize,
                                    reinterpret_cast<const jbyte *>(buffer->videoBuffer->rgbaBuffer));
        }
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_getVideoFrameRgbaSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeVideo) {
        if (buffer->videoBuffer->type == Rgba) {
            return buffer->videoBuffer->rgbaSize;
        } else {
            return 0;
        }
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_getAudioFrameBytesNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l,
        jbyteArray j_bytes) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeAudio) {
        env->SetByteArrayRegion(j_bytes, 0, buffer->audioBuffer->size,
                                reinterpret_cast<const jbyte *>(buffer->audioBuffer->pcmBuffer));
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_getAudioFrameSizeNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    if (buffer->type == BufferTypeAudio) {
        return buffer->audioBuffer->size;
    } else {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_freeDecodeDataNative(
        JNIEnv * env,
        jobject j_player,
        jlong buffer_l) {
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    freeDecodeBuffer(buffer);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_seekToNative(
        JNIEnv * env,
        jobject j_player,
        jlong player_l,
        jlong buffer_l,
        jlong targetPtsInMillis) {
    auto player = reinterpret_cast<tMediaPlayerContext *>(player_l);
    tMediaDecodeBuffer* buffer = nullptr;
    if (buffer_l != 0) {
        buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    }
    return player->seekTo((long) targetPtsInMillis, buffer, true);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_decodeNative(
        JNIEnv * env,
        jobject j_player,
        jlong player_l,
        jlong buffer_l) {
    auto player = reinterpret_cast<tMediaPlayerContext *>(player_l);
    auto buffer = reinterpret_cast<tMediaDecodeBuffer *>(buffer_l);
    return player->decode(buffer);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_resetNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    return player->resetDecodeProgress();
}

extern "C" JNIEXPORT void JNICALL
Java_com_tans_tmediaplayer_tMediaPlayer_releaseNative(
        JNIEnv * env,
        jobject j_player,
        jlong native_player) {
    auto *player = reinterpret_cast<tMediaPlayerContext *>(native_player);
    player->release();
}
