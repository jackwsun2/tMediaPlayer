package com.tans.tmediaplayer.demo.glide

import android.graphics.Bitmap
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import java.lang.Exception

class MediaImageModelLoader : ModelLoader<MediaImageModel, Bitmap> {
    override fun buildLoadData(
        model: MediaImageModel,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(model, MediaImageDataFetcher(model))
    }

    override fun handles(model: MediaImageModel): Boolean {
        return true
    }

    class MediaImageDataFetcher(private val model: MediaImageModel) : DataFetcher<Bitmap> {
        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
            // TODO: not impl
            println("Load model=$model")
            callback.onLoadFailed(Exception("Not impl"))
        }

        override fun cleanup() {  }

        override fun cancel() {  }

        override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

        override fun getDataSource(): DataSource = DataSource.REMOTE
    }

    companion object {
        class Factory : ModelLoaderFactory<MediaImageModel, Bitmap> {
            override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<MediaImageModel, Bitmap> {
                return MediaImageModelLoader()
            }

            override fun teardown() {}
        }
    }
}