/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.SparseBooleanArray
import android.view.*
import android.widget.Filter
import android.widget.Filterable
import androidx.activity.OnBackPressedCallback
import androidx.annotation.UiThread
import androidx.appcompat.widget.SearchView
import androidx.core.util.contains
import androidx.core.util.set
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutAppsBinding
import io.nekohasekai.sagernet.databinding.LayoutAppsItemBinding
import io.nekohasekai.sagernet.ktx.crossFadeFrom
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class AppManagerActivity : ThemedActivity() {

    override val onBackPressedCallback = object : OnBackPressedCallback(enabled = false) {
        override fun handleOnBackPressed() {
            searchView.onActionViewCollapsed()
            searchView.clearFocus()
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: AppManagerActivity? = null
        private const val SWITCH = "switch"

        private val cachedApps
            get() = PackageCache.installedPackages.toMutableMap().apply {
                remove(BuildConfig.APPLICATION_ID)
            }
    }

    private class ProxiedApp(
        private val pm: PackageManager, private val appInfo: ApplicationInfo,
        val packageName: String,
    ) {
        val name: CharSequence = appInfo.loadLabel(pm)    // cached for sorting
        val icon: Drawable get() = appInfo.loadIcon(pm)
        val uid get() = appInfo.uid
    }

    private inner class AppViewHolder(val binding: LayoutAppsItemBinding) : RecyclerView.ViewHolder(
        binding.root
    ),
        View.OnClickListener {
        private lateinit var item: ProxiedApp

        init {
            binding.root.setOnClickListener(this)
        }

        fun bind(app: ProxiedApp) {
            item = app
            binding.itemicon.setImageDrawable(app.icon)
            binding.title.text = app.name
            binding.desc.text = "${app.packageName} (${app.uid})"
            binding.itemcheck.isChecked = isProxiedApp(app)
        }

        fun handlePayload(payloads: List<String>) {
            if (payloads.contains(SWITCH)) binding.itemcheck.isChecked = isProxiedApp(item)
        }

        override fun onClick(v: View?) {
            if (isProxiedApp(item)) proxiedUids.delete(item.uid) else proxiedUids[item.uid] = true
            DataStore.individual = apps.filter { isProxiedApp(it) }
                .joinToString("\n") { it.packageName }

            appsAdapter.notifyItemRangeChanged(0, appsAdapter.itemCount, SWITCH)
        }
    }

    private inner class AppsAdapter : RecyclerView.Adapter<AppViewHolder>(),
        Filterable,
        FastScrollRecyclerView.SectionedAdapter {
        var filteredApps = apps

        suspend fun reload() {
            apps = cachedApps.map { (packageName, packageInfo) ->
                coroutineContext[Job]!!.ensureActive()
                ProxiedApp(packageManager, packageInfo.applicationInfo!!, packageName)
            }.sortedWith(compareBy({ !isProxiedApp(it) }, { it.name.toString() }))
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) =
            holder.bind(filteredApps[position])

        override fun onBindViewHolder(holder: AppViewHolder, position: Int, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST") holder.handlePayload(payloads as List<String>)
                return
            }

            onBindViewHolder(holder, position)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder =
            AppViewHolder(LayoutAppsItemBinding.inflate(layoutInflater, parent, false))

        override fun getItemCount(): Int = filteredApps.size

        private val filterImpl = object : Filter() {
            override fun performFiltering(constraint: CharSequence) = FilterResults().apply {
                var filteredApps = if (constraint.isEmpty()) apps else apps.filter {
                    it.name.contains(constraint, true) || it.packageName.contains(
                        constraint, true
                    ) || it.uid.toString().contains(constraint)
                }
                count = filteredApps.size
                values = filteredApps
            }

            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                @Suppress("UNCHECKED_CAST") filteredApps = results.values as List<ProxiedApp>
                notifyDataSetChanged()
            }
        }

        override fun getFilter(): Filter = filterImpl

        override fun getSectionName(position: Int): String {
            return filteredApps[position].name.firstOrNull()?.toString() ?: ""
        }

    }

    private val loading by lazy { findViewById<View>(R.id.loading) }

    private lateinit var binding: LayoutAppsBinding
    private val proxiedUids = SparseBooleanArray()
    private var loader: Job? = null
    private var apps = emptyList<ProxiedApp>()
    private val appsAdapter = AppsAdapter()
    private lateinit var searchView: SearchView

    private fun initProxiedUids(str: String = DataStore.individual) {
        proxiedUids.clear()
        PackageCache.awaitLoadSync()
        val apps = cachedApps
        for (line in str.lineSequence()) proxiedUids[(apps[line]
            ?: continue).applicationInfo!!.uid] = true
    }

    private fun isProxiedApp(app: ProxiedApp) = proxiedUids[app.uid]

    /**
     * 更新Chip的选中状态
     */
    private fun updateChipSelection(selectedId: Int) {
        binding.appProxyModeDisable.isChecked = (selectedId == R.id.appProxyModeDisable)
        binding.appProxyModeOn.isChecked = (selectedId == R.id.appProxyModeOn)
        binding.appProxyModeBypass.isChecked = (selectedId == R.id.appProxyModeBypass)
        binding.appProxyModeAuto.isChecked = (selectedId == R.id.appProxyModeAuto)
    }

    /**
     * 自动选择需要代理的应用
     * 基于预定义的需要代理的应用包名列表
     */
    private fun autoSelectProxyApps() {
        runOnDefaultDispatcher {
            // 需要代理的应用包名列表
            val proxyPackages = setOf(
                // Google services
                "com.google.android.gms",
                "com.google.android.gsf",
                "com.google.android.gsf.login",
                "com.google.android.backup",
                "com.google.android.backuptransport",
                "com.google.android.configupdater",
                "com.google.android.syncadapters.contacts",
                "com.google.android.syncadapters.calendar",
                "com.google.android.apps.docs",
                "com.google.android.apps.docs.editors.docs",
                "com.google.android.apps.docs.editors.sheets",
                "com.google.android.apps.docs.editors.slides",
                "com.google.android.apps.photos",
                "com.google.android.apps.photosgo",
                "com.google.android.videos",
                "com.google.android.music",
                "com.google.android.videos",
                "com.google.android.youtube",
                "com.google.android.youtube.tv",
                "com.google.android.apps.youtube.kids",
                "com.google.android.apps.youtube.music",
                "com.google.android.apps.youtube.creator",
                "com.google.android.apps.youtube.vr",
                // Google Play
                "com.android.vending",
                "com.google.android.feedback",
                // Google Chrome
                "com.android.chrome",
                "com.chrome.beta",
                "com.chrome.dev",
                "com.chrome.canary",
                // Gmail
                "com.google.android.gm",
                "com.google.android.gm.lite",
                // Google Maps
                "com.google.android.apps.maps",
                "com.google.android.apps.mapslite",
                // Google Drive
                "com.google.android.apps.docs",
                // Google Translate
                "com.google.android.apps.translate",
                // Google Assistant
                "com.google.android.apps.googleassistant",
                // Google Calendar
                "com.google.android.calendar",
                // Google Contacts
                "com.google.android.contacts",
                // Google Dialer
                "com.google.android.dialer",
                // Google Messages
                "com.google.android.apps.messaging",
                // Google Keep
                "com.google.android.keep",
                // Google Earth
                "com.google.earth",
                // Google Fit
                "com.google.android.apps.fitness",
                // Google News
                "com.google.android.apps.magazines",
                "com.google.android.apps.genie.geniewidget",
                // Google Podcasts
                "com.google.android.apps.podcasts",
                // Google Tasks
                "com.google.android.apps.tasks",
                // Google Lens
                "com.google.ar.lens",
                // Google Home
                "com.google.android.apps.chromecast.app",
                // Android TV
                "com.google.android.tv",
                "com.google.android.tv.remote",
                // Wear OS
                "com.google.android.wearable.app",
                "com.google.android.apps.wearable.companion",
                // Social Media
                "com.facebook.katana",
                "com.facebook.orca",
                "com.facebook.mlite",
                "com.facebook.lite",
                "com.instagram.android",
                "com.instagram.lite",
                "com.twitter.android",
                "com.twitter.android.lite",
                "com.whatsapp",
                "com.whatsapp.w4b",
                "com.telegram.messenger",
                "org.telegram.messenger",
                "com.discord",
                "com.reddit.frontpage",
                "com.linkedin.android",
                "com.pinterest",
                "com.tumblr",
                "com.snapchat.android",
                "com.tencent.mm",
                "com.tencent.mobileqq",
                "com.tencent.tim",
                "com.tencent.qqlite",
                "com.sina.weibo",
                "com.zhihu.android",
                "com.xiaomi.smarthome",
                "com.douyin.app",
                "com.ss.android.ugc.aweme",
                "com.smile.gifmaker",
                "com.kuaishou.nebula",
                "com.ss.android.article.news",
                "com.ss.android.article.video",
                // Streaming
                "com.netflix.mediaclient",
                "com.spotify.music",
                "com.spotify.lite",
                "com.amazon.avod.thirdpartyclient",
                "com.hulu.plus",
                "com.disney.disneyplus",
                "com.hbo.hbonow",
                "com.apple.android.music",
                "com.twitch.app",
                "com.duolingo",
                // Cloud Storage
                "com.dropbox.android",
                "com.microsoft.skydrive",
                "com.box.android",
                // Microsoft
                "com.microsoft.office.outlook",
                "com.microsoft.office.word",
                "com.microsoft.office.excel",
                "com.microsoft.office.powerpoint",
                "com.microsoft.teams",
                "com.microsoft.skype.teams",
                "com.skype.raider",
                "com.microsoft.bing",
                "com.microsoft.cortana",
                "com.microsoft.launcher",
                // Amazon
                "com.amazon.mShop.android.shopping",
                "com.amazon.kindle",
                "com.amazon.mp3",
                "com.amazon.cloud9",
                "com.amazon.dee.app",
                // Other common apps that need proxy
                "com.udemy.android",
                "com.coursera.android",
                "org.khanacademy.android",
                "com.quora.android",
                "com.medium.reader",
                "com.notion.id",
                "com.trello",
                "com.slack",
                "com.atlassian.android.jira.core",
                "com.github.android",
                "com.stackoverflow.stackoverflow",
                "com.adobe.lrmobile",
                "com.adobe.photoshopmix",
                "com.adobe.photoshopexpress",
                "com.adobe.premiererush.videoeditor",
                "com.adobe.scan.android",
                "com.adobe.acrobat.mobile",
                "com.jetbrains.kotlin",
                "com.ubisoft.uplay",
                "com.ea.games.nfs13_row",
                "com.ea.game.pvz2_row",
                "com.ea.game.simcitymobile_row",
                "com.rockstargames.gtasa",
                "com.rockstargames.gtalcs",
                "com.take2games.gta3",
                "com.square_enix.android_googleplay.FFIV_GP",
                "com.square_enix.android_googleplay.FFVI_GP",
                "com.mojang.minecraftpe",
                // VPN and Network tools
                "com.cloudflare.onedotonedotonedotone",
                "org.mozilla.firefox",
                "org.mozilla.firefox_beta",
                "org.mozilla.focus",
                "org.mozilla.klar",
                "com.brave.browser",
                "com.opera.browser",
                "com.opera.mini.native",
                "com.opera.browser.beta",
                "com.UCMobile.intl",
                "com.UCMobile",
                "com.cmcm.browser",
                "com.apusapps.browser",
                // News and Reading
                "com.nytimes.android",
                "com.washingtonpost.android",
                "com.tribune.android",
                "com.economist.gre",
                "com.economist.droid",
                "com.ft.android",
                "com.bloomberg.android.plus",
                "com.wsj.android",
                "com.bbc.mobile.android.ww",
                "com.cnn.mobile.android.phone",
                "com.nbc.android.nbcnews",
                "com.abc.abcnews",
                "com.theguardian",
                "com.telegraph.mobile",
                "com.medium.reader",
                // Gaming platforms
                "com.valvesoftware.android.steam.friendsui",
                "com.valvesoftware.android.steam.community",
                "com.epicgames.portal",
                "com.epicgames.fortnite",
                // AI and Chat
                "com.openai.chatgpt",
                "com.microsoft.copilot",
                "com.google.ai.assistant",
                // Other
                "com.slickdeals.android",
                "com.ebay.mobile",
                "com.etsy.android",
                "com.alibaba.aliexpresshd",
                "com.alibaba.intl.android.apps.poseidon",
                "com.shopee.th",
                "com.lazada.android",
                "com.binance.dev",
                "com.coinbase.android",
                "com.kraken.trade",
                "com.bitstamp.net",
                "com.gemini.android.app"
            )
            
            // 清空当前选择
            proxiedUids.clear()
            
            // 获取已安装的应用
            val installedApps = cachedApps
            
            // 选择需要代理的应用
            for ((packageName, _) in installedApps) {
                if (packageName in proxyPackages) {
                    val appInfo = installedApps[packageName]?.applicationInfo
                    if (appInfo != null) {
                        proxiedUids[appInfo.uid] = true
                    }
                }
            }
            
            // 保存选择
            DataStore.individual = apps.filter { isProxiedApp(it) }
                .joinToString("\n") { it.packageName }
            
            // 重新排序应用列表
            apps = apps.sortedWith(compareBy({ !isProxiedApp(it) }, { it.name.toString() }))
            
            onMainDispatcher {
                appsAdapter.filter.filter("")
            }
        }
    }

    @UiThread
    private fun loadApps() {
        loader?.cancel()
        loader = lifecycleScope.launch {
            loading.crossFadeFrom(binding.list)
            val adapter = binding.list.adapter as AppsAdapter
            withContext(Dispatchers.IO) { adapter.reload() }
            adapter.filter.filter( "")
            binding.list.crossFadeFrom(loading)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bypassGroup)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left + dp2px(4),
                right = bars.right + dp2px(4),
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.list)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom,
            )
            insets
        }
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.proxied_apps)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }

        if (!DataStore.proxyApps) {
            DataStore.proxyApps = true
        }

        // 设置初始选中状态
        updateChipSelection(if (DataStore.bypass) R.id.appProxyModeBypass else R.id.appProxyModeOn)

        // 设置按钮点击监听器
        binding.appProxyModeDisable.setOnClickListener {
            updateChipSelection(R.id.appProxyModeDisable)
            DataStore.proxyApps = false
            finish()
        }

        binding.appProxyModeOn.setOnClickListener {
            updateChipSelection(R.id.appProxyModeOn)
            DataStore.bypass = false
        }

        binding.appProxyModeBypass.setOnClickListener {
            updateChipSelection(R.id.appProxyModeBypass)
            DataStore.bypass = true
        }

        binding.appProxyModeAuto.setOnClickListener {
            updateChipSelection(R.id.appProxyModeAuto)
            DataStore.bypass = false
            autoSelectProxyApps()
        }

        initProxiedUids()
        binding.list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.list.itemAnimator = DefaultItemAnimator()
        binding.list.adapter = appsAdapter

        instance = this
        loadApps()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.per_app_proxy_menu, menu)
        searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            onBackPressedCallback.isEnabled = hasFocus
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?) = true.also { appsAdapter.filter.filter(newText) }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_invert_selections -> {
                runOnDefaultDispatcher {
                    for (app in apps) {
                        if (proxiedUids.contains(app.uid)) {
                            proxiedUids.delete(app.uid)
                        } else {
                            proxiedUids[app.uid] = true
                        }
                    }
                    DataStore.individual = apps.filter { isProxiedApp(it) }
                        .joinToString("\n") { it.packageName }
                    apps = apps.sortedWith(compareBy({ !isProxiedApp(it) }, { it.name.toString() }))
                    onMainDispatcher {
                        appsAdapter.filter.filter("")
                    }
                }

                return true
            }
            R.id.action_clear_selections -> {
                runOnDefaultDispatcher {
                    proxiedUids.clear()
                    DataStore.individual = ""
                    apps = apps.sortedWith(compareBy({ !isProxiedApp(it) }, { it.name.toString() }))
                    onMainDispatcher {
                        appsAdapter.filter.filter("")
                    }
                }
            }
            R.id.action_export_clipboard -> {
                val success = SagerNet.trySetPrimaryClip("${DataStore.bypass}\n${DataStore.individual}")
                Snackbar.make(
                    binding.list,
                    if (success) R.string.action_export_msg else R.string.action_export_err,
                    Snackbar.LENGTH_LONG
                ).show()
                return true
            }
            R.id.action_import_clipboard -> {
                val proxiedAppString = SagerNet.clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!proxiedAppString.isNullOrEmpty()) {
                    val i = proxiedAppString.indexOf('\n')
                    try {
                        val (enabled, apps) = if (i < 0) {
                            proxiedAppString to ""
                        } else proxiedAppString.substring(
                            0, i
                        ) to proxiedAppString.substring(i + 1)
                        updateChipSelection(if (enabled.toBoolean()) R.id.appProxyModeBypass else R.id.appProxyModeOn)
                        DataStore.individual = apps
                        Snackbar.make(
                            binding.list, R.string.action_import_msg, Snackbar.LENGTH_LONG
                        ).show()
                        initProxiedUids(apps)
                        appsAdapter.notifyItemRangeChanged(0, appsAdapter.itemCount, SWITCH)
                        return true
                    } catch (_: IllegalArgumentException) {
                    }
                }
                Snackbar.make(binding.list, R.string.action_import_err, Snackbar.LENGTH_LONG).show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun supportNavigateUpTo(upIntent: Intent) =
        super.supportNavigateUpTo(upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    override fun onDestroy() {
        instance = null
        loader?.cancel()
        super.onDestroy()
    }
}
