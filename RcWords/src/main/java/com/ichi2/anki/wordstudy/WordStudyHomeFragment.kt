// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 善物科技
package com.ichi2.anki.wordstudy

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import anki.decks.Deck
import anki.decks.DeckKt.FilteredKt.searchTerm
import anki.decks.DeckKt.filtered
import anki.decks.filteredDeckForUpdate
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentWordStudyHomeBinding
import com.ichi2.anki.databinding.IncludeWordStudyCardBinding
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 单词学习首页 Fragment
 *
 * 基于标签筛选机制实现词书切换：
 * - 所有单词在主 Deck 中，通过标签区分不同词书
 * - 用户选择词书后，创建筛选牌组进行学习
 * - 进度统计基于标签过滤
 */
class WordStudyHomeFragment : Fragment(R.layout.fragment_word_study_home) {
    private var wordStudyHomeBinding: FragmentWordStudyHomeBinding? = null
    private val binding get() = wordStudyHomeBinding!!

    // 通过 include 标签获取内部视图的 binding
    private lateinit var cardBinding: IncludeWordStudyCardBinding

    // 复用 DeckPicker 的 ViewModel（通过 activityViewModels 共享）
    private val viewModel: DeckPickerViewModel by activityViewModels()

    // 当前选中的词书
    private var currentWordBook: WordBookDefinition? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        wordStudyHomeBinding = FragmentWordStudyHomeBinding.bind(view)

        // 获取 include 布局中的视图引用
        cardBinding = IncludeWordStudyCardBinding.bind(binding.root.findViewById(R.id.word_study_card_include))
        setupClickListeners()
        observeData()
        setupCustomStudyResultListener()
        loadSavedWordBook()

        Timber.d("WordStudyHomeFragment created")
    }

    /**
     * 加载上次选择的词书
     */
    private fun loadSavedWordBook() {
        val savedBookId = Prefs.getString(R.string.current_word_book_id_key, "") ?: ""
        if (savedBookId.isNotEmpty()) {
            currentWordBook = WordBookCatalog.getBookById(savedBookId)
            currentWordBook?.let { updateUIForWordBook(it) }
        } else {
            // 默认选择第一个词书
            if (WordBookCatalog.allBooks.isNotEmpty()) {
                selectWordBook(WordBookCatalog.allBooks[0])
            }
        }
    }

    /**
     * 设置点击事件监听器
     */
    private fun setupClickListeners() {
        // 修改词书按钮
        cardBinding.btnModifyBook.setOnClickListener {
            Timber.i("Modify book button clicked")
            showWordBookSelectionDialog()
        }

        // 开始学习按钮
        cardBinding.btnStartStudy.setOnClickListener {
            Timber.i("Start study button clicked")
            startWordStudySession()
        }

        // 调整计划入口
        cardBinding.tvAdjustPlan.setOnClickListener {
            Timber.i("Adjust plan clicked")
            showCustomStudyDialog()
        }
    }

    /**
     * 显示词书选择对话框
     */
    private fun showWordBookSelectionDialog() {
        val dialog = WordBookSelectionDialog()
        dialog.show(parentFragmentManager, "word_book_selection")

        parentFragmentManager.setFragmentResultListener(
            WordBookSelectionDialog.RESULT_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val bookId = bundle.getString(WordBookSelectionDialog.BOOK_ID_KEY) ?: return@setFragmentResultListener
            val bookName = bundle.getString(WordBookSelectionDialog.BOOK_NAME_KEY) ?: return@setFragmentResultListener
            val tagFilter = bundle.getString(WordBookSelectionDialog.TAG_FILTER_KEY) ?: return@setFragmentResultListener
            val totalWords = bundle.getInt(WordBookSelectionDialog.TOTAL_WORDS_KEY, 0)

            val book = WordBookDefinition(bookId, bookName, tagFilter, totalWords)
            selectWordBook(book)
        }
    }

    /**
     * 选择词书
     */
    private fun selectWordBook(book: WordBookDefinition) {
        currentWordBook = book

        // 保存选择
        Prefs.putString(R.string.current_word_book_id_key, book.bookId)

        // 更新 UI
        updateUIForWordBook(book)

        // 创建筛选牌组并刷新数据
        lifecycleScope.launch {
            try {
                val filteredDeckId = createFilteredDeckForBook(book)
                viewModel.selectDeck(filteredDeckId)

                // 显示提示
                showSnackbar(
                    "已切换到《${book.bookName}》",
                    Snackbar.LENGTH_SHORT,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to create filtered deck for book: ${book.bookId}")
                showSnackbar(
                    "切换词书失败：${e.message}",
                    Snackbar.LENGTH_LONG,
                )
            }
        }
    }

    /**
     * 更新 UI 显示词书信息
     */
    private fun updateUIForWordBook(book: WordBookDefinition) {
        cardBinding.tvBookName.text = book.bookName
        cardBinding.tvBookTag.text = "${book.category} · 共${book.totalWords}词"
    }

    /**
     * 根据词书定义创建筛选牌组
     */
    private suspend fun createFilteredDeckForBook(book: WordBookDefinition): com.ichi2.anki.libanki.DeckId =
        withCol {
            // 删除旧的筛选牌组（如果存在）
            val oldDeckName = "${book.bookName} (今日)"
            val oldDeck = decks.byName(oldDeckName)
            if (oldDeck != null && oldDeck.getLong("dyn") != 0L) {
                decks.remove(listOf(oldDeck.getLong("id")))
            }

            // 创建新的筛选牌组
            val dailyLimit = book.getRecommendedDailyLimit()

            // 使用 sched.addOrUpdateFilteredDeck 创建筛选牌组
            val filteredDeckData =
                filteredDeckForUpdate {
                    id = 0 // 0 表示创建新牌组
                    name = oldDeckName
                    allowEmpty = false

                    config =
                        filtered {
                            reschedule = true

                            // 添加搜索条件
                            searchTerms.add(
                                searchTerm {
                                    search = book.tagFilter
                                    limit = dailyLimit
                                    order =
                                        Deck.Filtered.SearchTerm.Order
                                            .forNumber(0) // 0 = DUE (最久未学习优先)
                                },
                            )

                            // 预览间隔设置
                            previewAgainSecs = 60
                            previewHardSecs = 600
                            previewGoodSecs = 0
                        }
                }

            val result = sched.addOrUpdateFilteredDeck(filteredDeckData)
            result.id
        }

    /**
     * 观察 ViewModel 数据变化
     */
    private fun observeData() {
        // 使用 dueTree 属性获取当前数据
        val currentDueTree = viewModel.dueTree
        if (currentDueTree != null && currentWordBook != null) {
            updateCardData(currentDueTree)
        }

        // 监听 deck list 刷新事件
        lifecycleScope.launch {
            viewModel.flowOfDecksReloaded.collect {
                val updatedTree = viewModel.dueTree
                if (updatedTree != null && currentWordBook != null) {
                    updateCardData(updatedTree)
                }
            }
        }
    }

    /**
     * 更新卡片数据（复用 ViewModel 的统计数据）
     */
    private fun updateCardData(dueTree: com.ichi2.anki.libanki.sched.DeckNode) {
        val book = currentWordBook ?: return

        // 今日任务量
        val todayNewCards = dueTree.newCount
        val todayReviewCards = dueTree.revCount
        val todayLearningCards = dueTree.lrnCount

        // 更新词书信息
        cardBinding.tvBookName.text = book.bookName
        cardBinding.tvBookTag.text = "今日新词 $todayNewCards · 复习 $todayReviewCards"

        // 计算今日总任务量
        val totalTodayTasks = todayNewCards + todayReviewCards + todayLearningCards

        // TODO: 获取今日已完成数量（需要从 sched.studiedToday() 解析）
        val completedToday = 0 // 临时值

        // 更新进度条（显示今日完成度）
        val progressPercent =
            if (totalTodayTasks > 0) {
                (completedToday.toFloat() / totalTodayTasks * 100).toInt()
            } else {
                0
            }
        cardBinding.progressBar.progress = progressPercent
        cardBinding.tvProgressText.text = "今日已完成 $completedToday / $totalTodayTasks"

        // 更新今日计划（从Prefs读取）
        val dailyNewLimit = Prefs.getInt(R.string.daily_new_limit_key, 15)
        val dailyReviewLimit = Prefs.getInt(R.string.daily_review_limit_key, 50)

        cardBinding.tvNewCards.text = getString(R.string.new_cards, dailyNewLimit)
        cardBinding.tvReviewCards.text = getString(R.string.review_cards, dailyReviewLimit)

        // 计算预计用时：新词×30秒 + 复习×10秒
        val estimatedMinutes = (dailyNewLimit * 30 + dailyReviewLimit * 10) / 60
        cardBinding.tvEstimatedTime.text = getString(R.string.estimated_time, estimatedMinutes)

        // 检查是否今日已完成
        val hasPendingCards = todayNewCards > 0 || todayLearningCards > 0 || todayReviewCards > 0
        if (!hasPendingCards) {
            cardBinding.btnStartStudy.isEnabled = false
            cardBinding.btnStartStudy.text = getString(R.string.study_complete_today)
        } else {
            cardBinding.btnStartStudy.isEnabled = true
            cardBinding.btnStartStudy.text = getString(R.string.start_study)
        }

        Timber.d("Word study card updated: ${book.bookName}, today tasks=$totalTodayTasks")
    }

    /**
     * 显示自定义学习对话框（调整计划）
     *
     * 复用 CustomStudyDialog 来调整每日学习限额
     */
    private fun showCustomStudyDialog() {
        // 获取当前选中的词书 ID
        val currentDeckId = viewModel.dueTree?.did

        if (currentDeckId == null) {
            Timber.w("No deck selected, cannot show custom study dialog")
            return
        }

        // 创建 CustomStudyDialog
        val dialog = CustomStudyDialog.createInstance(currentDeckId)
        dialog.show(parentFragmentManager, "custom_study")
    }

    /**
     * 设置 CustomStudy 结果监听器
     */
    private fun setupCustomStudyResultListener() {
        setFragmentResultListener(CustomStudyDialog.CustomStudyAction.REQUEST_KEY) { _, bundle ->
            when (CustomStudyDialog.CustomStudyAction.fromBundle(bundle)) {
                CustomStudyDialog.CustomStudyAction.CUSTOM_STUDY_SESSION -> {
                    Timber.d("Custom study session created")
                    // 刷新数据
                    viewModel.updateDeckList()
                }
                CustomStudyDialog.CustomStudyAction.EXTEND_STUDY_LIMITS -> {
                    Timber.d("Study limits extended")
                    // 刷新数据
                    viewModel.updateDeckList()
                }
            }
        }
    }

    /**
     * 启动学习会话
     */
    private fun startWordStudySession() {
        val currentBook =
            currentWordBook ?: run {
                showSnackbar("请先选择一个词书", Snackbar.LENGTH_SHORT)
                return
            }

        Timber.i("Starting word study session for: ${currentBook.bookName}")

        // 启动 WordStudyActivity
        val intent =
            WordStudyActivity.createIntent(
                context = requireContext(),
                bookId = currentBook.bookId,
            )

        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        wordStudyHomeBinding = null
    }
}
