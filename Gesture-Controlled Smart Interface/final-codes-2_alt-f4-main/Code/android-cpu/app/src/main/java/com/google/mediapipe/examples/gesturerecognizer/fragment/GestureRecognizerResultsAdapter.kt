/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.gesturerecognizer.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.gesturerecognizer.databinding.ItemGestureRecognizerResultBinding
import java.util.Locale

class GestureRecognizerResultsAdapter :
    RecyclerView.Adapter<GestureRecognizerResultsAdapter.ViewHolder>() {
    companion object {
        private const val NO_VALUE = "--"
    }

    private var correctedCategories: List<Pair<String, Float>> = emptyList()
    private var adapterSize: Int = 0

    fun updateResults(correctedGestures: List<Pair<String, Float>>) {
        correctedCategories = correctedGestures
        adapterSize = correctedCategories.size
        notifyDataSetChanged()
    }

    // This function doesn't seem necessary if you call notifyDataSetChanged()
    // but I'll leave it in case you use it elsewhere.
    fun updateAdapterSize(size: Int) {
        adapterSize = size
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemGestureRecognizerResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Pass the Pair object directly to the bind function
        holder.bind(correctedCategories[position])
    }

    // Corrected to use adapterSize, which you set in updateResults
    override fun getItemCount(): Int = adapterSize

    inner class ViewHolder(private val binding: ItemGestureRecognizerResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Updated bind function to accept the Pair
        fun bind(category: Pair<String, Float>) {
            val label = category.first
            val score = category.second

            with(binding) {
                tvLabel.text = label ?: NO_VALUE
                tvScore.text = String.format(
                    Locale.US,
                    "%.2f",
                    score
                )
            }
        }
    }
}