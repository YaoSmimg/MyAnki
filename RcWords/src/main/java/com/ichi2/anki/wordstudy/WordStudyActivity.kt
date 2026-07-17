// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 善物科技
package com.ichi2.anki.wordstudy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import androidx.appcompat.app.AlertDialog
import anki.decks.Deck
import anki.decks.DeckKt.FilteredKt.searchTerm
import anki.decks.DeckKt.filtered
import anki.decks.filteredDeckForUpdate
import anki.scheduler.CardAnswer.Rating
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.Reviewer
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber

/**
 * 单词学习专用 Activity
 *
 * ## 架构说明
 * - 继承自 Reviewer，复用所有卡片学习核心功能
 * - 通过筛选牌组（Filtered Deck）实现词书专属学习会话
 * - 通过 JavaScript 注入三选一按钮到卡片正面
 * - 支持 TypeAnswer 拼写输入和反馈区域
 *
 * ## 学习流程
 * 1. 用户看到卡片正面（单词）
 * 2. 点击三选一按钮（简单/模糊/不认识）
 * 3. 根据不同选择展示不同内容
 * 4. 翻转到背面，显示四个确认按钮
 * 5. 点击确认按钮进入下一词
 */
class WordStudyActivity : Reviewer() {
    companion object {
        private const val EXTRA_BOOK_ID = "book_id"
        private const val EXTRA_FILTERED_DECK_ID = "filtered_deck_id"

        fun createIntent(
            context: Context,
            bookId: String,
            filteredDeckId: Long? = null,
        ): Intent =
            Intent(context, WordStudyActivity::class.java).apply {
                putExtra(EXTRA_BOOK_ID, bookId)
                filteredDeckId?.let { putExtra(EXTRA_FILTERED_DECK_ID, it) }
            }
    }

    // 当前学习的词书
    private var currentBook: WordBookDefinition? = null

    // 筛选牌组 ID
    private var filteredDeckId: Long? = null

    // 状态标记
    private var currentChoice: UserChoice? = null

    enum class UserChoice {
        UNKNOWN, // 不认识
        VAGUE, // 模糊
        SIMPLE, // 简单
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("WordStudyActivity onCreate")

        extractIntentParams()

        super.onCreate(savedInstanceState)

        // 注册 JavaScript 接口
        setupJavaScriptInterface()

        // 如果还没有筛选牌组，先创建
        if (filteredDeckId == null && currentBook != null) {
            launchCatchingTask {
                filteredDeckId = createFilteredDeckForBook(currentBook!!)
                getColUnsafe.decks.select(filteredDeckId!!)
                Timber.i("Created and selected filtered deck: $filteredDeckId")
            }
        } else if (filteredDeckId != null) {
            launchCatchingTask {
                getColUnsafe.decks.select(filteredDeckId!!)
                Timber.i("Selected existing filtered deck: $filteredDeckId")
            }
        }
    }

    /**
     * 重写 recreateWebView，在创建时注册 JavaScript 接口
     */
    override fun recreateWebView() {
        super.recreateWebView()

        // 注册 JavaScript 接口
        setupJavaScriptInterface()
        Timber.i("JavaScript interface registered in recreateWebView")
    }

    /**
     * 注册 JavaScript 接口
     */
    private fun setupJavaScriptInterface() {
        webView?.settings?.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowContentAccess = true
        }
        webView?.addJavascriptInterface(WordStudyJavaScriptInterface(), "WordStudyAndroid")
        Timber.i("Registered JavaScript interface: WordStudyAndroid")
        Timber.i("JavaScript enabled: ${webView?.settings?.javaScriptEnabled}")
    }

    /**
     * 卡片加载完成后，注入 JavaScript
     */
    override fun fillFlashcard() {
        super.fillFlashcard()

        Timber.i("fillFlashcard called, displayAnswer=$displayAnswer")
        Timber.i("webView is null: ${webView == null}")

        // 只在正面注入三选一按钮
        if (!displayAnswer) {
            // 延迟注入，确保 DOM 已经加载完成
            webView?.postDelayed({
                injectThreeChoiceButtons()
            }, 300)
        }
    }

    /**
     * 注入三选一按钮到卡片正面
     */
    private fun injectThreeChoiceButtons() {
        Timber.i("injectThreeChoiceButtons called")

        if (webView == null) {
            Timber.e("webView is null, cannot inject buttons")
            return
        }

        val jsCode =
            """
            (function() {
                console.log('=== Starting button injection ===');
                
                try {
                    // 检查是否已经注入过
                    if (document.getElementById('three-choice-buttons')) {
                        console.log('Buttons already injected, skipping');
                        return 'already_injected';
                    }
                    
                    // 创建按钮容器
                    var container = document.createElement('div');
                    container.id = 'three-choice-buttons';
                    container.style.cssText = 'display: flex !important; justify-content: center; gap: 8px; margin: 16px 0; padding: 0 16px; z-index: 9999;';
                    
                    // 创建三个按钮
                    var buttonsConfig = [
                        { text: '不认识', color: '#EF4444', action: 'handleUnknown' },
                        { text: '模糊', color: '#F59E0B', action: 'handleVague' },
                        { text: '简单', color: '#22C55E', action: 'handleSimple' }
                    ];
                    
                    console.log('Creating buttons...');
                    buttonsConfig.forEach(function(btnConfig) {
                        var button = document.createElement('button');
                        button.textContent = btnConfig.text;
                        button.id = 'btn-' + btnConfig.action;
                        
                        // 设置明显的按钮样式
                        button.style.flex = '1';
                        button.style.padding = '16px 20px';
                        button.style.backgroundColor = btnConfig.color;
                        button.style.color = 'white';
                        button.style.border = '3px solid white';
                        button.style.borderRadius = '12px';
                        button.style.fontSize = '18px';
                        button.style.fontWeight = 'bold';
                        button.style.cursor = 'pointer';
                        button.style.minHeight = '56px';
                        button.style.boxShadow = '0 4px 6px rgba(0, 0, 0, 0.3)';
                        button.style.opacity = '0.95';
                        
                        button.onclick = function() {
                            console.log('=== Button clicked: ' + btnConfig.action + ' ===');
                            console.log('window.WordStudyAndroid exists:', !!window.WordStudyAndroid);
                            
                            if (window.WordStudyAndroid) {
                                console.log('Available methods:', Object.keys(window.WordStudyAndroid));
                                console.log('Calling handle' + btnConfig.action.charAt(0).toUpperCase() + btnConfig.action.slice(1) + '()');
                                
                                try {
                                    window.WordStudyAndroid[btnConfig.action]();
                                    console.log('Call succeeded');
                                } catch(e) {
                                    console.error('Call failed:', e.message);
                                    alert('Error: ' + e.message);
                                }
                            } else {
                                console.error('WordStudyAndroid not found!');
                                alert('WordStudyAndroid interface not available!');
                            }
                        };
                        
                        container.appendChild(button);
                        console.log('Created button: ' + btnConfig.text);
                    });
                    
                    // 隐藏原有的答案按钮（在正面时）
                    var answerButtons = document.getElementById('answernumbers');
                    if (answerButtons) {
                        answerButtons.style.display = 'none';
                        console.log('Hidden answer buttons');
                    }
                    
                    // 插入到卡片内容后面
                    var cardContent = document.querySelector('.card');
                    if (cardContent) {
                        cardContent.appendChild(container);
                        console.log('Buttons injected to .card successfully');
                        return 'success_card';
                    } else {
                        console.warn('.card not found, trying body');
                        var body = document.body;
                        if (body) {
                            body.appendChild(container);
                            console.log('Buttons injected to body as fallback');
                            return 'success_body';
                        } else {
                            console.error('Neither .card nor body found');
                            return 'error_no_container';
                        }
                    }
                } catch(e) {
                    console.error('Error injecting buttons: ' + e.message);
                    console.error('Stack: ' + e.stack);
                    return 'error: ' + e.message;
                }
            })();
            """.trimIndent()

        // 执行 JavaScript
        webView?.evaluateJavascript(jsCode) { result ->
            Timber.i("JavaScript execution result: $result")
            when {
                result == null -> Timber.w("JS returned null - possible syntax error")
                result.contains("error", ignoreCase = true) -> Timber.e("JS error: $result")
                result.contains("success") -> Timber.i("✅ Buttons injected successfully!")
                else -> Timber.d("JS result: $result")
            }
        }

        Timber.i("Inject three-choice buttons command sent")
    }

    /**
     * JavaScript 接口类
     */
    inner class WordStudyJavaScriptInterface {
        @JavascriptInterface
        fun handleSimple() {
            Timber.i("User clicked: Simple")
            runOnUiThread {
                handleSimpleChoice()
            }
        }

        @JavascriptInterface
        fun handleVague() {
            Timber.i("User clicked: Vague")
            runOnUiThread {
                handleVagueChoice()
            }
        }

        @JavascriptInterface
        fun handleUnknown() {
            Timber.i("User clicked: Unknown")
            runOnUiThread {
                handleUnknownChoice()
            }
        }
    }

    /**
     * 处理【简单】选择
     */
    private fun handleSimpleChoice() {
        currentChoice = UserChoice.SIMPLE

        // 直接翻转显示答案
        flipOrAnswerCard(Rating.GOOD)
    }

    /**
     * 处理【模糊】选择
     */
    private fun handleVagueChoice() {
        currentChoice = UserChoice.VAGUE

        // TODO: 显示拼写输入对话框
        // 暂时直接翻转
        flipOrAnswerCard(Rating.GOOD)
    }

    /**
     * 处理【不认识】选择
     */
    private fun handleUnknownChoice() {
        currentChoice = UserChoice.UNKNOWN

        // TODO: 展开反馈区域
        // 暂时直接翻转
        flipOrAnswerCard(Rating.AGAIN)
    }

    override fun onCollectionLoaded(col: com.ichi2.anki.libanki.Collection) {
        super.onCollectionLoaded(col)

        supportActionBar?.title = currentBook?.let { book ->
            "${book.bookName} (学习中)"
        } ?: "单词学习"

        Timber.i("Word study session started for: ${currentBook?.bookName}")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val result = super.onCreateOptionsMenu(menu)

        // TODO: Phase 2 隐藏不需要的菜单项

        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Timber.i("Back button pressed, showing exit confirmation")
                showExitConfirmationDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showExitConfirmationDialog() {
        AlertDialog
            .Builder(this)
            .setTitle("确认退出")
            .setMessage("确定退出学习吗？进度将自动保存")
            .setPositiveButton("确认退出") { _, _ ->
                finish()
            }.setNegativeButton("继续学习", null)
            .show()
    }

    override fun closeReviewer(result: Int) {
        saveStudyProgress()
        super.closeReviewer(result)
    }

    private fun saveStudyProgress() {
        Timber.i("Study progress saved")
    }

    private fun extractIntentParams() {
        currentBook =
            intent.getStringExtra(EXTRA_BOOK_ID)?.let { bookId ->
                WordBookCatalog.allBooks.find { it.bookId == bookId }
            }

        filteredDeckId = intent.getLongExtra(EXTRA_FILTERED_DECK_ID, 0).takeIf { it != 0L }

        Timber.i("WordStudyActivity params: bookId=${currentBook?.bookId}, deckId=$filteredDeckId")
    }

    private suspend fun createFilteredDeckForBook(book: WordBookDefinition): Long =
        withCol {
            val deckName = "${book.bookName} (学习中)"

            decks.byName(deckName)?.let { oldDeck ->
                if (oldDeck.getLong("dyn") != 0L) {
                    decks.remove(listOf(oldDeck.getLong("id")))
                    Timber.i("Removed old filtered deck: $deckName")
                }
            }

            val dailyLimit = book.getRecommendedDailyLimit()
            val filteredDeckData =
                filteredDeckForUpdate {
                    id = 0
                    name = deckName
                    allowEmpty = true // 允许空牌组，避免"没有符合条件的卡片"错误

                    config =
                        filtered {
                            reschedule = true

                            searchTerms.add(
                                searchTerm {
                                    search = book.tagFilter
                                    limit = dailyLimit
                                    order =
                                        Deck.Filtered.SearchTerm.Order
                                            .forNumber(0)
                                },
                            )

                            previewAgainSecs = 60
                            previewHardSecs = 600
                            previewGoodSecs = 0
                        }
                }

            val result = sched.addOrUpdateFilteredDeck(filteredDeckData)
            Timber.i("Created filtered deck: ${result.id}, name: $deckName")

            // 检查是否有卡片
            val cardCount = decks.cardCount(result.id, includeSubdecks = false)
            if (cardCount == 0) {
                Timber.w("⚠️ Warning: No cards found with filter: ${book.tagFilter}")

                // 判断是否是测试空牌组
                if (book.bookId == "test_empty") {
                    Timber.i("This is expected for test_empty book - used for UI testing")
                } else {
                    Timber.w("Please ensure cards with tag '${book.tagFilter}' exist in the collection")

                    // 显示提示给用户（仅对正式词书）
                    launchCatchingTask {
                        showSnackbar(
                            "未找到带标签 '${book.tagFilter}' 的卡片\n请先导入词书或检查标签设置",
                            Snackbar.LENGTH_LONG,
                        )
                    }
                }
            } else {
                Timber.i("✅ Found $cardCount cards matching filter: ${book.tagFilter}")
            }

            result.id
        }
}
