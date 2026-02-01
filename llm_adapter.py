import os
import time
from typing import Any, Dict
import openai

try:
    import openai
except Exception:
    openai = None

os.environ["OPENROUTER_API_KEY"] = ""

class LLMAdapter:
    """Simple adapter that prefers OpenAI (if configured) and otherwise falls back
    to a local mock 'reasoning' implementation for development and testing.

    Usage:
      adapter = LLMAdapter()
      adapter.complete("Summarize X")
    """

    def __init__(self):
        self.api_key = os.environ.get("OPENROUTER_API_KEY")
        if openai and self.api_key:
            self.client = openai.OpenAI(base_url="https://openrouter.ai/api/v1", api_key=os.getenv("OPENROUTER_API_KEY"),)
        else:
            self.client = None
    def complete(self, prompt: str, params: Dict[str, Any] = None) -> Dict[str, Any]:
        params = params or {}
        if self.client and self.api_key:
            return self._openai_complete(prompt, params)
        return self._local_mock(prompt)

    def _openai_complete(self, prompt: str, params: Dict[str, Any]) -> Dict[str, Any]:
        model = params.get("model", "openai/gpt-oss-120b:free")
        try:
            resp = self.client.chat.completions.create(
                model=model,
                messages=[{"role": "user", "content": prompt}],
                max_tokens=params.get("max_tokens", 512),
                temperature=params.get("temperature", 0.2),
            )
            text = resp.choices[0].message.content.strip()
            print("Response: " + text)
            return {"source": "openrouter", "model": model, "text": text}
        except Exception as e:
            return {"error": str(e)}

    def _local_mock(self, prompt: str) -> Dict[str, Any]:
        """Very small local 'reasoning' fallback used when no API key is present.

        It returns a chain-of-thought style breakdown and a short answer.
        """
        # naive tokenization and step generation for demonstration
        time.sleep(0.5)
        sentences = [s.strip() for s in prompt.replace("?", ".").split(".") if s.strip()]
        steps = []
        for i, s in enumerate(sentences, 1):
            steps.append(f"Step {i}: consider '{s}'")
        # final short 'reasoned' answer
        final = " ".join(sentences)
        if not final:
            final = "(no content)"
        return {"source": "local-mock", "thoughts": steps, "final": final}
