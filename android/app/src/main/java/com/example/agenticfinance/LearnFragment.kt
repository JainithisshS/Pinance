package com.example.agenticfinance

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LearnFragment : Fragment(R.layout.fragment_learn) {

    private lateinit var btnStartLearning: Button
    private lateinit var layoutLearningCard: LinearLayout
    private lateinit var txtConceptTitle: TextView
    private lateinit var txtCardContent: TextView
    private lateinit var txtQuizQuestion: TextView
    private lateinit var radioGroupOptions: RadioGroup
    private lateinit var btnSubmitAnswer: Button
    private lateinit var txtFeedback: TextView
    private lateinit var btnNextCard: Button

    private var currentCardId: String? = null
    private val optionsMap = mutableMapOf<Int, Int>() // RadioButton ID -> Option Index

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Learning UI
        btnStartLearning = view.findViewById(R.id.btnStartLearning)
        layoutLearningCard = view.findViewById(R.id.layoutLearningCard)
        txtConceptTitle = view.findViewById(R.id.txtConceptTitle)
        txtCardContent = view.findViewById(R.id.txtCardContent)
        txtQuizQuestion = view.findViewById(R.id.txtQuizQuestion)
        radioGroupOptions = view.findViewById(R.id.radioGroupOptions)
        btnSubmitAnswer = view.findViewById(R.id.btnSubmitAnswer)
        txtFeedback = view.findViewById(R.id.txtFeedback)
        btnNextCard = view.findViewById(R.id.btnNextCard)

        btnStartLearning.setOnClickListener {
            startLearningSession()
        }

        btnSubmitAnswer.setOnClickListener {
            submitAnswer()
        }

        btnNextCard.setOnClickListener {
            loadNextCard()
        }
    }

    private fun startLearningSession() {
        btnStartLearning.visibility = View.GONE
        layoutLearningCard.visibility = View.VISIBLE
        loadNextCard()
    }

    private fun loadNextCard() {
        // Reset UI for loading
        txtConceptTitle.text = "Loading..."
        txtCardContent.text = "Fetching personalized content..."
        txtQuizQuestion.text = ""
        radioGroupOptions.removeAllViews()
        txtFeedback.text = ""
        btnSubmitAnswer.visibility = View.GONE
        btnNextCard.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Hardcoded user for demo
                val response = ApiClient.api.getNextCard(userId = "android_user")
                displayCard(response.card)
            } catch (e: Exception) {
                txtConceptTitle.text = "Error"
                txtCardContent.text = "Failed to load card: ${e.message}"
                btnStartLearning.visibility = View.VISIBLE // Retry
            }
        }
    }

    private fun displayCard(card: LearningCardDto) {
        currentCardId = card.id
        txtConceptTitle.text = "Concept: ${card.concept_id}" // Ideally map to name
        txtCardContent.text = card.content
        txtQuizQuestion.text = card.quiz.question

        // Populate options
        radioGroupOptions.removeAllViews()
        optionsMap.clear()
        
        card.quiz.options.forEachIndexed { index, optionText ->
            val radioButton = RadioButton(requireContext())
            radioButton.text = optionText
            radioButton.id = View.generateViewId()
            radioGroupOptions.addView(radioButton)
            optionsMap[radioButton.id] = index
        }

        btnSubmitAnswer.visibility = View.VISIBLE
    }

    private fun submitAnswer() {
        val selectedId = radioGroupOptions.checkedRadioButtonId
        if (selectedId == -1) {
            txtFeedback.text = "Please select an answer."
            return
        }

        val answerIndex = optionsMap[selectedId] ?: return
        val cardId = currentCardId ?: return

        btnSubmitAnswer.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = SubmitAnswerRequestDto(
                    card_id = cardId,
                    answer_index = answerIndex,
                    time_spent_seconds = 30 // Placeholder
                )
                val response = ApiClient.api.submitAnswer(request)
                
                // Show feedback
                txtFeedback.text = if (response.is_correct) {
                    "Correct! ${response.explanation}\nMastery: ${response.belief_update.mastery_level}"
                } else {
                    "Incorrect. ${response.explanation}"
                }
                txtFeedback.setTextColor(if (response.is_correct) 
                    Color.GREEN else Color.RED)

                btnSubmitAnswer.visibility = View.GONE
                btnNextCard.visibility = View.VISIBLE
            } catch (e: Exception) {
                txtFeedback.text = "Error submitting: ${e.message}"
            } finally {
                btnSubmitAnswer.isEnabled = true
            }
        }
    }
}
