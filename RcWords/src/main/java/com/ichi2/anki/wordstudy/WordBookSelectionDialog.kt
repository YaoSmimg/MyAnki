// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 善物科技
package com.ichi2.anki.wordstudy

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import timber.log.Timber

/**
 * 词书选择对话框
 *
 * 展示所有可用词书，用户可以选择一个词书开始学习
 *
 * ## 使用方式
 *

 */
class WordBookSelectionDialog : AnalyticsDialogFragment() {
    companion object {
        const val RESULT_KEY = "word_book_selection_result"
        const val BOOK_ID_KEY = "book_id"
        const val BOOK_NAME_KEY = "book_name"
        const val TAG_FILTER_KEY = "tag_filter"
        const val TOTAL_WORDS_KEY = "total_words"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view =
            LayoutInflater
                .from(requireContext())
                .inflate(R.layout.dialog_word_book_selection, null)

        val recyclerView = view.findViewById<RecyclerView>(R.id.word_book_list)
        val adapter =
            WordBookAdapter { selectedBook ->
                onWordBookSelected(selectedBook)
                dismiss()
            }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // 按分类分组展示
        adapter.submitList(WordBookCatalog.allBooks)

        return AlertDialog
            .Builder(requireContext())
            .setTitle("选择词书")
            .setView(view)
            .setNegativeButton("取消", null)
            .create()
    }

    private fun onWordBookSelected(book: WordBookDefinition) {
        Timber.i("Word book selected: ${book.bookName}")

        setFragmentResult(
            RESULT_KEY,
            bundleOf(
                BOOK_ID_KEY to book.bookId,
                BOOK_NAME_KEY to book.bookName,
                TAG_FILTER_KEY to book.tagFilter,
                TOTAL_WORDS_KEY to book.totalWords,
            ),
        )
    }
}

/**
 * 词书列表适配器
 */
private class WordBookAdapter(
    private val onItemClick: (WordBookDefinition) -> Unit,
) : RecyclerView.Adapter<WordBookAdapter.ViewHolder>() {
    private var books: List<WordBookDefinition> = emptyList()

    fun submitList(newBooks: List<WordBookDefinition>) {
        books = newBooks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_word_book, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(books[position], onItemClick)
    }

    override fun getItemCount(): Int = books.size

    class ViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val tvBookName: TextView = itemView.findViewById(R.id.tv_book_name)
        private val tvBookInfo: TextView = itemView.findViewById(R.id.tv_book_info)
        private val tvBookDesc: TextView = itemView.findViewById(R.id.tv_book_desc)

        fun bind(
            book: WordBookDefinition,
            onClick: (WordBookDefinition) -> Unit,
        ) {
            tvBookName.text = book.bookName
            tvBookInfo.text = "${book.category} · ${book.totalWords}词"
            tvBookDesc.text = book.description.ifEmpty { "暂无描述" }

            cardView.setOnClickListener {
                onClick(book)
            }
        }
    }
}
