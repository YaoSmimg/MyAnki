// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 善物科技
package com.ichi2.anki.wordstudy

/**
 * 词书定义
 *
 * @param bookId 词书唯一标识
 * @param bookName 词书显示名称
 * @param tagFilter Anki 标签筛选条件（如 "tag:cet4"）
 * @param totalWords 词书总词数（预统计）
 * @param category 词书分类（考试/出国/日常等）
 * @param description 词书描述
 */
data class WordBookDefinition(
    val bookId: String,
    val bookName: String,
    val tagFilter: String,
    val totalWords: Int,
    val category: String = "",
    val description: String = "",
    val customDailyLimit: Int? = null, // 可选的自定义每日限制（用于测试）
) {
    /**
     * 获取每日学习量建议
     */
    fun getRecommendedDailyLimit(): Int {
        // 如果有自定义限制，优先使用
        customDailyLimit?.let { return it }

        return when {
            totalWords < 1000 -> 20
            totalWords < 3000 -> 30
            totalWords < 5000 -> 50
            else -> 60
        }
    }
}

/**
 * 词书目录
 *
 * 管理所有可用的词书定义
 */
object WordBookCatalog {
    /**
     * 所有可用词书列表
     *
     * 注意：这里的 totalWords 需要根据实际导入的卡片数量进行调整
     */
    val allBooks =
        listOf(
            // ========== 测试专用词书 ==========
            WordBookDefinition(
                bookId = "test_empty",
                bookName = "测试空牌组",
                tagFilter = "", // 空字符串匹配所有卡片（包括没有标签的）
                totalWords = 0,
                category = "测试",
                description = "用于测试的空牌组，匹配所有卡片",
            ),
            // ========== 正式词书 ==========
            WordBookDefinition(
                bookId = "cet4",
                bookName = "四级词汇",
                tagFilter = "tag:cet4",
                totalWords = 4500,
                category = "考试",
                description = "大学英语四级考试核心词汇",
            ),
            WordBookDefinition(
                bookId = "cet6",
                bookName = "六级词汇",
                tagFilter = "tag:cet6",
                totalWords = 6000,
                category = "考试",
                description = "大学英语六级考试核心词汇",
            ),
            WordBookDefinition(
                bookId = "ielts",
                bookName = "雅思词汇",
                tagFilter = "tag:ielts",
                totalWords = 8000,
                category = "出国",
                description = "雅思考试高频核心词汇",
            ),
            WordBookDefinition(
                bookId = "toefl",
                bookName = "托福词汇",
                tagFilter = "tag:toefl",
                totalWords = 8000,
                category = "出国",
                description = "托福考试必备词汇",
            ),
            WordBookDefinition(
                bookId = "gre",
                bookName = "GRE词汇",
                tagFilter = "tag:gre",
                totalWords = 12000,
                category = "出国",
                description = "GRE考试高级词汇",
            ),
            WordBookDefinition(
                bookId = "daily",
                bookName = "日常词汇",
                tagFilter = "tag:daily",
                totalWords = 3000,
                category = "日常",
                description = "日常生活常用词汇",
            ),
        )

    /**
     * 根据 ID 获取词书
     */
    fun getBookById(bookId: String): WordBookDefinition? = allBooks.find { it.bookId == bookId }

    /**
     * 根据分类获取词书列表
     */
    fun getBooksByCategory(category: String): List<WordBookDefinition> = allBooks.filter { it.category == category }

    /**
     * 获取所有分类
     */
    fun getAllCategories(): List<String> = allBooks.map { it.category }.distinct()
}
