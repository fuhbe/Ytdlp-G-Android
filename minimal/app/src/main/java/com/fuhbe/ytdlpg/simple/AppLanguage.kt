package com.fuhbe.ytdlpg.simple

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.util.Locale

/** UI-only preference. Does not recreate the Activity, jobs or download model. */
internal enum class AppLanguage(val tag:String,val label:String) {
    ENGLISH("en","English"),PORTUGUESE("pt-BR","Português (Brasil)"),SPANISH("es","Español");
    companion object { fun from(tag:String?)=entries.firstOrNull { it.tag==tag } ?: ENGLISH }
}
internal class UiLanguageState(context:Context) {
    private val preferences=context.applicationContext.getSharedPreferences("ytdlpg-ui",Context.MODE_PRIVATE)
    var language by mutableStateOf(AppLanguage.from(preferences.getString("language","en")));private set
    fun select(value:AppLanguage) { if(language!=value) { preferences.edit().putString("language",value.tag).apply();language=value } }
}
private val LocalUiLanguage=staticCompositionLocalOf<UiLanguageState?> { null }
private val LocalUiResources=staticCompositionLocalOf<Resources?> { null }
@Composable internal fun AppLanguageProvider(content:@Composable ()->Unit) {
    val context=LocalContext.current
    val state=remember(context.applicationContext) { UiLanguageState(context) }
    val resources=remember(context.resources.configuration,state.language) {
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(state.language.tag)) }).resources
    }
    CompositionLocalProvider(LocalUiLanguage provides state,LocalUiResources provides resources,content=content)
}
private val uiStringIds=mapOf(
    "Language" to R.string.ui_000,
    "App language" to R.string.ui_001,
    "Changes apply immediately. Downloads are not restarted." to R.string.ui_002,
    "Home" to R.string.ui_003,
    "Back" to R.string.ui_004,
    "Downloads" to R.string.ui_005,
    "Settings" to R.string.ui_006,
    "Video link" to R.string.ui_007,
    "Paste one or more YouTube links" to R.string.ui_008,
    "Paste link" to R.string.ui_009,
    "Analyzing…" to R.string.ui_010,
    "Analyze link" to R.string.ui_011,
    "Cancel analysis" to R.string.ui_012,
    "Playlist detected" to R.string.ui_013,
    "Video preview" to R.string.ui_014,
    "Playlist videos" to R.string.ui_015,
    "Clear selection" to R.string.ui_016,
    "Select all" to R.string.ui_017,
    "Show less" to R.string.ui_018,
    "Format" to R.string.ui_019,
    "Converts the best audio source to high-quality VBR MP3. Quality depends on the source." to R.string.ui_020,
    "Download mode" to R.string.ui_021,
    "Normal" to R.string.ui_022,
    "ZIP combines the selected files. Temporary originals remain until completed copies are cleared." to R.string.ui_023,
    "Starting…" to R.string.ui_024,
    "Download playlist" to R.string.ui_025,
    "Download" to R.string.ui_026,
    "Unavailable" to R.string.ui_027,
    "Video quality" to R.string.ui_028,
    "Resolutions found in the reference video. Items use the best available resolution up to your choice, without upscaling." to R.string.ui_029,
    "Available resolutions only. Video and audio are combined; incompatible codecs are converted to MP4." to R.string.ui_030,
    "Destination folder" to R.string.ui_031,
    "Save to" to R.string.ui_032,
    "Change folder" to R.string.ui_033,
    "Completed" to R.string.ui_034,
    "No completed downloads." to R.string.ui_035,
    "Your downloads will appear here." to R.string.ui_036,
    "Completed with warnings" to R.string.ui_037,
    "Paused" to R.string.ui_038,
    "Cancelled" to R.string.ui_039,
    "Error" to R.string.ui_040,
    "Queued" to R.string.ui_041,
    "Downloading" to R.string.ui_042,
    "Can be retried" to R.string.ui_043,
    "Close" to R.string.ui_044,
    "Completed files" to R.string.ui_045,
    "Appearance" to R.string.ui_046,
    "System default" to R.string.ui_047,
    "Light" to R.string.ui_048,
    "Dark" to R.string.ui_049,
    "Download engine" to R.string.ui_050,
    "Updating…" to R.string.ui_051,
    "Check and update yt-dlp" to R.string.ui_052,
    "Copy diagnostics" to R.string.ui_053,
    "Temporary storage" to R.string.ui_054,
    "Paused downloads retain partial data. ZIP originals remain in the app. Clearing removes only temporary copies of completed, exported downloads." to R.string.ui_055,
    "Clear completed temporary files" to R.string.ui_056,
    "A simple interface for videos, music and playlists. Execution core from YTDLnis by Deniscerri and contributors, under GPLv3." to R.string.ui_057,
    "Licenses and source code" to R.string.ui_058,
    "Open source" to R.string.ui_059,
    "Ytdlp-G source" to R.string.ui_060,
    "YTDLnis project" to R.string.ui_061,
    "Best quality" to R.string.ui_062,
    "Up to 4K, when available" to R.string.ui_063,
    "Up to the selected resolution" to R.string.ui_064,
    "Choose quality" to R.string.ui_065,
    "%1\$d videos • %2\$s" to R.string.ui_066,
    "%1\$d of %2\$d selected" to R.string.ui_067,
    "Show all %1\$d videos" to R.string.ui_068,
    "Downloads (%1\$d)" to R.string.ui_069,
    "MP3 • best source" to R.string.ui_070,
    " • %1\$d of %2\$d" to R.string.ui_071,
    " • %1\$s at this stage" to R.string.ui_072,
    "Select %1\$s" to R.string.ui_073,
    "Pause %1\$s" to R.string.ui_074,
    "Resume %1\$s" to R.string.ui_075,
    "Open files for %1\$s" to R.string.ui_076,
    "Cancel %1\$s" to R.string.ui_077,
    "Review unsuccessful items (%1\$d)" to R.string.ui_078,
    "Unsuccessful items (%1\$d)" to R.string.ui_079,
    "Retry (%1\$d)" to R.string.ui_080,
    "yt-dlp %1\$s\nPython, FFmpeg and QuickJS included." to R.string.ui_081,
    "Videos and playlists as MP3 or MP4. Paste multiple links, one per line.\n\nDownload only content you have permission to save." to R.string.ui_082,
)
/** Unmapped technical/license text and user content are preserved verbatim. */
@Composable internal fun uiText(english:String,vararg arguments:Any):String {
    val resources=LocalUiResources.current ?: LocalContext.current.resources
    val id=uiStringIds[english] ?: return english
    return if(arguments.isEmpty())resources.getString(id) else resources.getString(id,*arguments)
}
@Composable internal fun LanguageSettingsCard() {
    val state=LocalUiLanguage.current ?: return
    GlassCard {
        Text(uiText("App language"),style=MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        AppLanguage.entries.forEach { language ->
            GlassButton(onClick={state.select(language)},modifier=Modifier.fillMaxWidth().testTag("ui-language-"+language.tag)
                .semantics { selected=state.language==language;stateDescription=language.label },emphasized=state.language==language) {
                RadioButton(state.language==language,onClick=null)
                Spacer(Modifier.width(10.dp));Text(language.label,modifier=Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(uiText("Changes apply immediately. Downloads are not restarted."),style=MaterialTheme.typography.bodySmall)
    }
}
