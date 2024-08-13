package com.tans.tmediaplayer.demo

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.tans.tmediaplayer.demo.databinding.MainActivityBinding
import com.tans.tuiutils.activity.BaseCoroutineStateActivity
import com.tans.tuiutils.permission.permissionsRequestSuspend
import com.tans.tuiutils.systembar.annotation.SystemBarStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SystemBarStyle(statusBarThemeStyle = 1, navigationBarThemeStyle = 1)
class MainActivity : BaseCoroutineStateActivity<MainActivity.Companion.State>(State()) {

    override val layoutId: Int = R.layout.main_activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        startActivity(PlayerActivity.createIntent(this, ""))
    }

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {  }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        onBackPressedDispatcher.addCallback {
            finish()
        }
        val permissionsNeed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeed.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionsNeed.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissionsNeed.add(Manifest.permission.READ_MEDIA_VIDEO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                permissionsNeed.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
        } else {
            permissionsNeed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        launch {
            runCatching {
                permissionsRequestSuspend(*permissionsNeed.toTypedArray())
            }
        }
    }

    companion object {
        enum class TabType { Videos, Audios }

        data class State(
            val selectedTab: TabType = TabType.Videos
        )
    }
}