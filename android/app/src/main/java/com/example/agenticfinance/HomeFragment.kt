package com.example.agenticfinance

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var inputMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var txtResult: TextView

    private lateinit var inputChat: EditText
    private lateinit var btnChatSend: Button
    private lateinit var txtChatLog: TextView

    private val chatHistory = mutableListOf<ChatTurnDto>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        inputMessage = view.findViewById(R.id.inputMessage)
        btnSend = view.findViewById(R.id.btnSend)
        txtResult = view.findViewById(R.id.txtResult)

        inputChat = view.findViewById(R.id.inputChat)
        btnChatSend = view.findViewById(R.id.btnChatSend)
        txtChatLog = view.findViewById(R.id.txtChatLog)
        txtChatLog.movementMethod = ScrollingMovementMethod()

        btnSend.setOnClickListener {
            val text = inputMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendToBackend(text)
            } else {
                txtResult.text = "Please paste an SMS message."
            }
        }

        btnChatSend.setOnClickListener {
            val question = inputChat.text.toString().trim()
            if (question.isNotEmpty()) {
                askAgentC(question)
            } else {
                txtChatLog.text = "Type a question for Agent C."
            }
        }
    }

    private fun sendToBackend(raw: String) {
        txtResult.text = "Sending..."
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val body = ParseMessageRequestDto(raw_message = raw)
                val tx = ApiClient.api.parseMessage(body)

                val result = buildString {
                    appendLine("Parsed transaction:")
                    appendLine("Amount: ${tx.amount}")
                    appendLine("Merchant: ${tx.merchant ?: "-"}")
                    appendLine("Category: ${tx.category}")
                    appendLine("Time: ${tx.timestamp}")
                }
                txtResult.text = result
            } catch (e: Exception) {
                txtResult.text = "Error: ${e.message}"
            }
        }
    }

    private fun askAgentC(question: String) {
        // Append the user's question to local history and send to backend.
        chatHistory.add(ChatTurnDto(role = "user", content = question))

        // Show a simple local echo while waiting.
        updateChatLog(pending = true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Build non-generic context from the actual conversation.
                val userTurnsText = chatHistory
                    .filter { it.role == "user" }
                    .joinToString("; ") { it.content }

                val request = SynthesizeRequestDto(
                    finance_insight = "User-described personal finance situation: $userTurnsText",
                    news_insight = "User-described news / market view: $userTurnsText",
                    user_question = question,
                    history = chatHistory.toList()
                )

                val response = ApiClient.api.synthesize(request)

                // Add assistant reply to history and refresh UI.
                chatHistory.add(ChatTurnDto(role = "assistant", content = response.recommendation))
                updateChatLog(pending = false)
            } catch (e: Exception) {
                chatHistory.add(ChatTurnDto(role = "assistant", content = "Error talking to Agent C: ${e.message}"))
                updateChatLog(pending = false)
            } finally {
                inputChat.text.clear()
            }
        }
    }

    private fun updateChatLog(pending: Boolean) {
        val builder = StringBuilder()
        for (turn in chatHistory) {
            when (turn.role) {
                "user" -> builder.append("You: ")
                "assistant" -> builder.append("Agent C: ")
                else -> builder.append("System: ")
            }
            builder.append(turn.content).append("\n\n")
        }
        if (pending) {
            builder.append("Agent C is thinking...\n")
        }
        txtChatLog.text = builder.toString().trim()
    }
}
