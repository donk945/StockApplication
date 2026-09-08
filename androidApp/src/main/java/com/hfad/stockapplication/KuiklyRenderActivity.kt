package com.hfad.stockapplication

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.tencent.kuikly.core.render.android.IKuiklyRenderExport
import com.tencent.kuikly.core.render.android.adapter.KuiklyRenderAdapterManager
import com.tencent.kuikly.core.render.android.css.ktx.toMap
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegatorDelegate
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegator
import com.hfad.stockapplication.adapter.KRColorParserAdapter
import com.hfad.stockapplication.adapter.KRFontAdapter
import com.hfad.stockapplication.adapter.KRImageAdapter
import com.hfad.stockapplication.adapter.KRLogAdapter
import com.hfad.stockapplication.adapter.KRRouterAdapter
import com.hfad.stockapplication.adapter.KRThreadAdapter
import com.hfad.stockapplication.adapter.KRUncaughtExceptionHandlerAdapter
import com.hfad.stockapplication.module.KRBridgeModule
import com.hfad.stockapplication.module.KRShareModule
import com.hfad.stockapplication.module.KRSseModule
import org.json.JSONObject

class KuiklyRenderActivity : AppCompatActivity(), KuiklyRenderViewBaseDelegatorDelegate {

    private lateinit var hrContainerView: ViewGroup
    private lateinit var loadingView: View
    private lateinit var errorView: View

    private val kuiklyRenderViewDelegator = KuiklyRenderViewBaseDelegator(this)

    private val pageName: String
        get() {
            val pn = intent.getStringExtra(KEY_PAGE_NAME) ?: ""
            return if (pn.isNotEmpty()) {
                return pn
            } else {
                "stock_chat"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hr)
        setupImmersiveMode()
        hrContainerView = findViewById(R.id.hr_container)
        loadingView = findViewById(R.id.hr_loading)
        errorView = findViewById(R.id.hr_error)
        applyNightBars(resolveStartNight())
        kuiklyRenderViewDelegator.onAttach(hrContainerView, "", pageName, createPageData())
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val consumed = kuiklyRenderViewDelegator.onBackPressed()
                if (!consumed) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        kuiklyRenderViewDelegator.onDetach()
    }

    override fun onPause() {
        super.onPause()
        kuiklyRenderViewDelegator.onPause()
    }

    override fun onResume() {
        super.onResume()
        kuiklyRenderViewDelegator.onResume()
    }

    override fun registerExternalModule(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalModule(kuiklyRenderExport)
        with(kuiklyRenderExport) {
            moduleExport(KRBridgeModule.MODULE_NAME) {
                KRBridgeModule()
            }
            moduleExport(KRShareModule.MODULE_NAME) {
                KRShareModule()
            }
            moduleExport(KRSseModule.MODULE_NAME) {
                KRSseModule()
            }
        }
    }

    override fun registerExternalRenderView(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalRenderView(kuiklyRenderExport)
        with(kuiklyRenderExport) {

        }
    }

    private fun createPageData(): Map<String, Any> {
        val param = argsToMap()
        param["appId"] = 1
        param[KEY_DEEPSEEK_API_KEY] = BuildConfig.DEEPSEEK_API_KEY
        param["isNightMode"] = if (isSystemNight()) 1 else 0
        return param
    }

    private fun isSystemNight(): Boolean {
        val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun resolveStartNight(): Boolean {
        val mode = getSharedPreferences("KRSharedPreferencesModule", MODE_PRIVATE)
            .getString("user_theme_mode_v1", "system")
            .orEmpty()
        return when (mode) {
            "dark" -> true
            "light" -> false
            else -> isSystemNight()
        }
    }

    fun applyNightBars(night: Boolean) {
        val window = window ?: return
        setSystemBarLightIcons(window, light = !night)
        val bg = if (night) 0xFF111113.toInt() else Color.WHITE
        window.decorView.setBackgroundColor(bg)
        if (::hrContainerView.isInitialized) {
            hrContainerView.setBackgroundColor(bg)
        }
    }

    private fun setSystemBarLightIcons(window: Window, light: Boolean) {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
        }
        var vis = window.decorView.systemUiVisibility
        val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        vis = if (light) vis or flags else vis and flags.inv()
        window.decorView.systemUiVisibility = vis
    }

    private fun argsToMap(): MutableMap<String, Any> {
        val jsonStr = intent.getStringExtra(KEY_PAGE_DATA) ?: return mutableMapOf()
        return JSONObject(jsonStr).toMap()
    }

    /**
     * 内容铺到状态栏 / 底部导航栏（小白条）后面，由页面用 safeAreaInsets 自己让开。
     * 对齐 Kuikly 官方 Demo 的沉浸式窗口配置。
     */
    private fun setupImmersiveMode() {
        val window = window ?: return
        setDecorFitsSystemWindows(window)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = if (Build.VERSION.SDK_INT >= 26) Color.TRANSPARENT else 0x66000000
        if (Build.VERSION.SDK_INT >= 28) {
            val newMode = if (Build.VERSION.SDK_INT >= 30) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            val attrs = window.attributes
            if (attrs.layoutInDisplayCutoutMode != newMode) {
                attrs.layoutInDisplayCutoutMode = newMode
                window.attributes = attrs
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        setAppearanceLightStatusBars(window)
        setAppearanceLightNavigationBars(window)
    }

    private fun setDecorFitsSystemWindows(window: Window) {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < 35) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        }
    }

    private fun setAppearanceLightStatusBars(window: Window) {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 30) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            )
        } else if (Build.VERSION.SDK_INT >= 23) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    private fun setAppearanceLightNavigationBars(window: Window) {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 30) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        } else if (Build.VERSION.SDK_INT >= 26) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    companion object {

        private const val KEY_PAGE_NAME = "pageName"
        private const val KEY_PAGE_DATA = "pageData"
        const val KEY_DEEPSEEK_API_KEY = "deepseekApiKey"

        init {
            initKuiklyAdapter()
        }

        fun start(context: Context, pageName: String, pageData: JSONObject) {
            val starter = Intent(context, KuiklyRenderActivity::class.java)
            starter.putExtra(KEY_PAGE_NAME, pageName)
            starter.putExtra(KEY_PAGE_DATA, pageData.toString())
            context.startActivity(starter)
        }

        private fun initKuiklyAdapter() {
            with(KuiklyRenderAdapterManager) {
                krImageAdapter = KRImageAdapter(KRApplication.application)
                krLogAdapter = KRLogAdapter
                krUncaughtExceptionHandlerAdapter = KRUncaughtExceptionHandlerAdapter
                krFontAdapter = KRFontAdapter
                krColorParseAdapter = KRColorParserAdapter(KRApplication.application)
                krRouterAdapter = KRRouterAdapter
                krThreadAdapter = KRThreadAdapter()
            }
        }
    }
}
