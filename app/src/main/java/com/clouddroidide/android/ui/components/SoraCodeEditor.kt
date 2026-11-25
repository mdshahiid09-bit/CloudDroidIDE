package com.clouddroidide.android.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource

object SoraTextMateInitializer {
    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            runCatching {
                FileProviderRegistry.getInstance().addFileProvider(
                    AssetsFileResolver(context.applicationContext.assets)
                )
                val themePath = "textmate/darcula.json"
                val themeInput = FileProviderRegistry.getInstance().tryGetInputStream(themePath)
                if (themeInput != null) {
                    val source = IThemeSource.fromInputStream(themeInput, themePath, null)
                    ThemeRegistry.getInstance().loadTheme(ThemeModel(source, "darcula"))
                    ThemeRegistry.getInstance().setTheme("darcula")
                }
                GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
                initialized = true
            }
        }
    }

    fun scopeForFileName(fileName: String): String? {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".kt") || lower.endsWith(".kts") -> "source.kotlin"
            lower.endsWith(".java") || lower.endsWith(".gradle") -> "source.java"
            lower.endsWith(".xml") -> "text.xml"
            lower.endsWith(".yml") || lower.endsWith(".yaml") -> "source.yaml"
            lower.endsWith(".json") -> "source.json"
            lower.endsWith(".sh") || lower.endsWith(".bash") -> "source.shell"
            lower.endsWith(".py") -> "source.python"
            lower.endsWith(".js") -> "source.js"
            lower.endsWith(".md") -> "text.html.markdown"
            else -> null
        }
    }
}

class SoraEditorController {
    internal var editor: CodeEditor? = null

    fun insertText(text: String) {
        editor?.insertText(text, text.length)
    }

    fun undo() {
        editor?.undo()
    }

    fun redo() {
        editor?.redo()
    }

    fun jumpToLine(line: Int, column: Int = 1) {
        val ed = editor ?: return
        val targetLine = (line - 1).coerceIn(0, (ed.lineCount - 1).coerceAtLeast(0))
        val lineLen = ed.text.getColumnCount(targetLine)
        val targetCol = (column - 1).coerceIn(0, lineLen)
        ed.setSelection(targetLine, targetCol)
        ed.ensureSelectionVisible()
    }

    fun findNext(query: String, caseSensitive: Boolean = false) {
        val ed = editor ?: return
        if (query.isEmpty()) return
        ed.searcher.search(
            query,
            io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions(caseSensitive, false)
        )
        ed.searcher.gotoNext()
    }

    fun findPrevious() {
        editor?.searcher?.gotoPrevious()
    }

    fun replaceCurrent(replacement: String) {
        editor?.searcher?.replaceThis(replacement)
    }

    fun replaceAll(replacement: String) {
        editor?.searcher?.replaceAll(replacement)
    }

    fun stopSearch() {
        editor?.searcher?.stopSearch()
    }
}

@Composable
fun SoraCodeEditor(
    filePath: String,
    fileName: String,
    text: String,
    controller: SoraEditorController,
    onTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editorInstance by remember { mutableStateOf<CodeEditor?>(null) }
    var ignoreNextCallback by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            controller.editor = null
            editorInstance?.release()
            editorInstance = null
        }
    }

    LaunchedEffect(filePath, fileName, editorInstance) {
        val ed = editorInstance ?: return@LaunchedEffect
        val scope = SoraTextMateInitializer.scopeForFileName(fileName)
        runCatching {
            if (scope != null) {
                ed.setEditorLanguage(TextMateLanguage.create(scope, true))
            } else {
                ed.setEditorLanguage(null)
            }
        }
        if (ed.text.toString() != text) {
            ignoreNextCallback = true
            ed.setText(text)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SoraTextMateInitializer.ensureInitialized(ctx)
            CodeEditor(ctx).apply {
                typefaceText = Typeface.MONOSPACE
                typefaceLineNumber = Typeface.MONOSPACE
                setTextSize(13.5f)
                isLineNumberEnabled = true
                isWordwrap = false
                tabWidth = 4
                props.autoIndent = true
                props.symbolPairAutoCompletion = true

                runCatching {
                    colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                }.onFailure {
                    colorScheme = EditorColorScheme()
                }
                colorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.parseColor("#0E1116"))
                colorScheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, Color.parseColor("#0E1116"))
                colorScheme.setColor(EditorColorScheme.LINE_NUMBER, Color.parseColor("#6E7681"))
                colorScheme.setColor(EditorColorScheme.CURRENT_LINE, Color.parseColor("#161B22"))
                colorScheme.setColor(EditorColorScheme.SELECTION_INSERT, Color.parseColor("#58A6FF"))
                colorScheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, Color.parseColor("#264F78"))

                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    if (ignoreNextCallback) {
                        ignoreNextCallback = false
                    } else {
                        onTextChanged(this.text.toString())
                    }
                }

                ignoreNextCallback = true
                setText(text)
                controller.editor = this
                editorInstance = this
            }
        }
    )
}
