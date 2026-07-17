// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 善物科技
package com.ichi2.anki.wordstudy

import androidx.core.view.isVisible
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.databinding.IncludeWordStudyCardBinding
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.settings.Prefs
import timber.log.Timber

/**
 * 管理单词学习模式的UI显示和交互
 *
 * 复用现有的DeckPickerViewModel统计数据，无需新建ViewModel
 */
class WordStudyModeManager(
    private val deckPicker: DeckPicker,
    private val viewModel: DeckPickerViewModel,
) {
    private var wordStudyCardBinding: IncludeWordStudyCardBinding? = null
    private var isWordStudyMode = false

    /**
     * 初始化单词学习卡片视图
     */
    fun initializeWordStudyCard() {
        // 先隐藏全局进度条（必须在 addView 之前）
        deckPicker.hideProgressBar()

        // 动态加载词书卡片布局
        val cardView =
            deckPicker.layoutInflater.inflate(
                R.layout.include_word_study_card,
                deckPicker.deckPickerBinding.root,
                false,
            )

        wordStudyCardBinding = IncludeWordStudyCardBinding.bind(cardView)

        // 将卡片添加到deck_picker_content中
        deckPicker.deckPickerBinding.deckPickerContent.addView(cardView, 0)

        // 初始隐藏
        wordStudyCardBinding?.root?.isVisible = false

        setupClickListeners()
        Timber.d("WordStudyModeManager initialized")
    }

    /**
     * 设置点击事件监听器
     */
    private fun setupClickListeners() {
        val binding = wordStudyCardBinding ?: return

        // 修改词书按钮
        binding.btnModifyBook.setOnClickListener {
            Timber.i("Modify book button clicked")
            // TODO: 跳转到词书选择页面
            // deckPicker.navigateToBookSelector()
        }

        // 开始学习按钮
        binding.btnStartStudy.setOnClickListener {
            Timber.i("Start study button clicked")
            // TODO: 启动学习会话
            // deckPicker.startWordStudySession()
        }

        // 调整计划入口
        binding.tvAdjustPlan.setOnClickListener {
            Timber.i("Adjust plan clicked")
            // TODO: 打开设置页面
            // deckPicker.navigateToSettings()
        }
    }

    /**
     * 切换到单词学习模式
     */
    fun switchToWordStudyMode() {
        if (isWordStudyMode) return

        isWordStudyMode = true

        // 隐藏原有的牌组列表
        deckPicker.deckPickerBinding.decks.isVisible = false
        deckPicker.deckPickerBinding.noDecksPlaceholder.isVisible = false

        // 显示单词学习卡片
        wordStudyCardBinding?.root?.isVisible = true

        // 隐藏浮动操作按钮
        deckPicker.floatingActionMenu.hideFloatingActionButton()

        // 更新卡片数据
        updateWordStudyCardData()

        Timber.d("Switched to Word Study Mode")
    }

    /**
     * 切换回普通牌组模式
     */
    fun switchToNormalMode() {
        if (!isWordStudyMode) return

        isWordStudyMode = false

        // 显示原有的牌组列表
        deckPicker.deckPickerBinding.decks.isVisible = true

        // 隐藏单词学习卡片
        wordStudyCardBinding?.root?.isVisible = false

        // 显示浮动操作按钮
        deckPicker.floatingActionMenu.showFloatingActionButton()

        Timber.d("Switched to Normal Mode")
    }

    /**
     * 更新单词学习卡片的数据
     * 复用DeckPickerViewModel的flowOfDeckDueTree统计数据
     */
    private fun updateWordStudyCardData() {
        val binding = wordStudyCardBinding ?: return
        val dueTree = viewModel.dueTree

        if (dueTree == null) {
            Timber.w("dueTree is null, cannot update word study card")
            return
        }

        // 获取当前选中的词书（deck）
        val currentDeckName = dueTree.fullDeckName.ifEmpty { "默认词书" }
        val totalCards = dueTree.newCount + dueTree.lrnCount + dueTree.revCount
        val learnedCards = totalCards - dueTree.newCount // 简化计算

        // 更新词书信息
        binding.tvBookName.text = currentDeckName
        binding.tvBookTag.text = "共${totalCards}词"

        // 更新进度条
        val progressPercent =
            if (totalCards > 0) {
                (learnedCards.toFloat() / totalCards * 100).toInt()
            } else {
                0
            }
        binding.progressBar.progress = progressPercent
        binding.tvProgressText.text =
            deckPicker.getString(
                R.string.progress_completed,
                learnedCards,
                totalCards,
            )

        // 更新今日计划（从Prefs读取）
        val dailyNewLimit = Prefs.getInt(R.string.daily_new_limit_key, 15)
        val dailyReviewLimit = Prefs.getInt(R.string.daily_review_limit_key, 50)

        binding.tvNewCards.text = deckPicker.getString(R.string.new_cards, dailyNewLimit)
        binding.tvReviewCards.text = deckPicker.getString(R.string.review_cards, dailyReviewLimit)

        // 计算预计用时：新词×30秒 + 复习×10秒
        val estimatedMinutes = (dailyNewLimit * 30 + dailyReviewLimit * 10) / 60
        binding.tvEstimatedTime.text = deckPicker.getString(R.string.estimated_time, estimatedMinutes)

        // 检查是否今日已完成
        val hasPendingCards = dueTree.newCount > 0 || dueTree.lrnCount > 0 || dueTree.revCount > 0
        if (!hasPendingCards) {
            binding.btnStartStudy.isEnabled = false
            binding.btnStartStudy.text = deckPicker.getString(R.string.study_complete_today)
        } else {
            binding.btnStartStudy.isEnabled = true
            binding.btnStartStudy.text = deckPicker.getString(R.string.start_study)
        }

        Timber.d("Word study card updated: $currentDeckName, progress=$progressPercent%")
    }

    /**
     * 刷新卡片数据（当统计数据变化时调用）
     */
    fun refreshCardData() {
        if (isWordStudyMode) {
            updateWordStudyCardData()
        }
    }
}
