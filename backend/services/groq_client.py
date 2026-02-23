"""Groq API client for content and quiz generation."""
import os
import json
from pathlib import Path
import httpx
from typing import Optional
from dotenv import load_dotenv
from backend.models.learning import Concept, Quiz

# Load .env so GROQ_API_KEY is available
_env_path = Path(__file__).resolve().parent.parent / ".env"
load_dotenv(dotenv_path=_env_path)


class GroqClient:
    """Client for Groq API (fast LLM inference)."""
    
    def __init__(self, api_key: Optional[str] = None):
        self.api_key = api_key or os.getenv("GROQ_API_KEY")
        if not self.api_key:
            raise ValueError("GROQ_API_KEY environment variable not set")
        
        self.base_url = "https://api.groq.com/openai/v1"
        self.model = "llama-3.3-70b-versatile"  # Fast and capable
    
    async def chat(
        self,
        system_prompt: str,
        user_prompt: str,
        temperature: float = 0.7,
        max_tokens: int = 800,
    ) -> str | None:
        """General-purpose LLM call. Used by all 3 agents.

        Returns the LLM response text, or None if anything fails
        (network error, API error, timeout) so callers can fall back
        to their existing deterministic logic.
        """
        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                response = await client.post(
                    f"{self.base_url}/chat/completions",
                    headers={
                        "Authorization": f"Bearer {self.api_key}",
                        "Content-Type": "application/json",
                    },
                    json={
                        "model": self.model,
                        "messages": [
                            {"role": "system", "content": system_prompt},
                            {"role": "user", "content": user_prompt},
                        ],
                        "temperature": temperature,
                        "max_tokens": max_tokens,
                    },
                )
                response.raise_for_status()
                result = response.json()
                return result["choices"][0]["message"]["content"].strip()
        except Exception as exc:
            print(f"[GroqClient.chat] LLM call failed: {exc}")
            return None

    async def chat_with_history(
        self,
        system_prompt: str,
        messages: list[dict],
        temperature: float = 0.7,
        max_tokens: int = 800,
    ) -> str | None:
        """LLM call with full message history for multi-turn conversations.

        ``messages`` should be a list of {"role": ..., "content": ...} dicts
        (user / assistant turns).  The system prompt is prepended automatically.
        """
        try:
            all_messages = [{"role": "system", "content": system_prompt}] + messages
            async with httpx.AsyncClient(timeout=30.0) as client:
                response = await client.post(
                    f"{self.base_url}/chat/completions",
                    headers={
                        "Authorization": f"Bearer {self.api_key}",
                        "Content-Type": "application/json",
                    },
                    json={
                        "model": self.model,
                        "messages": all_messages,
                        "temperature": temperature,
                        "max_tokens": max_tokens,
                    },
                )
                response.raise_for_status()
                result = response.json()
                return result["choices"][0]["message"]["content"].strip()
        except Exception as exc:
            print(f"[GroqClient.chat_with_history] LLM call failed: {exc}")
            return None

    async def generate_content(self, concept: Concept) -> str:
        """
        Generate micro-learning content for a concept.
        
        Args:
            concept: The concept to generate content for
            
        Returns:
            Educational content text (150-200 words)
        """
        prompt = f"""Generate a micro-learning card for the financial concept: {concept.name}

Description: {concept.description}
Target audience: Young adults learning personal finance
Tone: Educational, friendly, non-advisory
Difficulty level: {concept.difficulty}/5

Requirements:
- 150-200 words maximum
- Use simple, clear language
- Include 1-2 practical examples
- Focus on understanding, not financial advice
- End with a key takeaway
- Educational tone only

Format as plain text without any markdown or formatting."""

        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                f"{self.base_url}/chat/completions",
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json"
                },
                json={
                    "model": self.model,
                    "messages": [{"role": "user", "content": prompt}],
                    "temperature": 0.7,
                    "max_tokens": 400
                }
            )
            
            response.raise_for_status()
            result = response.json()
            return result["choices"][0]["message"]["content"].strip()
    
    async def generate_quiz(self, concept: Concept, content: str) -> Quiz:
        """
        Generate a conceptual quiz for a learning card.
        
        Args:
            concept: The concept being tested
            content: The educational content from the card
            
        Returns:
            Quiz object with question, options, and explanation
        """
        prompt = f"""Create a conceptual quiz question for the financial concept: {concept.name}

Content covered:
{content}

Requirements:
- Test understanding, not memorization
- 4 multiple choice options (A, B, C, D)
- One clearly correct answer
- Include explanation for the correct answer
- Difficulty: {concept.difficulty}/5
- Focus on practical application or key concepts

Return ONLY valid JSON in this exact format:
{{
  "question": "Your question here?",
  "options": ["Option A", "Option B", "Option C", "Option D"],
  "correct_answer_index": 0,
  "explanation": "Explanation of why this answer is correct"
}}"""

        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                f"{self.base_url}/chat/completions",
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json"
                },
                json={
                    "model": self.model,
                    "messages": [{"role": "user", "content": prompt}],
                    "temperature": 0.8,
                    "max_tokens": 300,
                    "response_format": {"type": "json_object"}
                }
            )
            
            response.raise_for_status()
            result = response.json()
            quiz_data = json.loads(result["choices"][0]["message"]["content"])
            
            return Quiz(**quiz_data)
    
    async def generate_card(self, concept: Concept) -> tuple[str, Quiz]:
        """
        Generate both content and quiz for a concept.
        Uses concurrent API calls for speed.
        
        Args:
            concept: The concept to generate a card for
            
        Returns:
            Tuple of (content, quiz)
        """
        import asyncio
        import time
        start = time.time()
        
        # Generate content first (quiz needs content as context)
        content = await self.generate_content(concept)
        quiz = await self.generate_quiz(concept, content)
        
        elapsed = time.time() - start
        print(f"[Groq] Generated card for '{concept.id}' in {elapsed:.1f}s")
        return content, quiz


# Singleton instance
_groq_client: Optional[GroqClient] = None

def get_groq_client() -> GroqClient:
    """Get or create the Groq client singleton."""
    global _groq_client
    if _groq_client is None:
        _groq_client = GroqClient()
    return _groq_client
