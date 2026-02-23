# Problem Statement – Agentic Finance System

Most retail users struggle to:
- Track their actual spending patterns from SMS/UPI notifications
- Understand their personal financial risk (EMI burden, savings, fixed vs variable expenses)
- Interpret daily financial news and market signals in the context of their own profile

Existing finance apps either:
- Only track expenses
- Or only provide generic market news and tips

They rarely combine **personal transaction data**, **news sentiment**, and **domain knowledge** into a single, explainable recommendation.

## Objective

Build an **Agentic Finance System** that:
1. Ingests and structures personal finance data from SMS/notifications.
2. Computes a personal risk and spending profile.
3. Understands financial news and trends using NLP.
4. Uses a RAG + multi-agent LLM setup to generate **grounded, explainable recommendations** tailored to the user.

## Scope

- Android app for data capture and interaction.
- FastAPI backend for ML/LLM workflows.
- RAG over curated finance knowledge.
- Evaluation and research-style analysis of agentic vs baseline approaches.
