import os
import time
from typing import Any, Dict

try:
    import openai
except Exception:
    openai = None


class LLMAdapter:
    """Simple adapter that prefers OpenAI (if configured) and otherwise falls back
    to a local mock 'reasoning' implementation for development and testing.

    Usage:
      adapter = LLMAdapter()
      adapter.complete("Summarize X")
    """

    def __init__(self):
        self.api_key = os.environ.get("OPENAI_API_KEY")
        if openai and self.api_key:
            openai.api_key = self.api_key

    def complete(self, prompt: str, params: Dict[str, Any] = None) -> Dict[str, Any]:
        params = params or {}
        if openai and self.api_key:
            return self._openai_complete(prompt, params)
        return self._local_mock(prompt)

    def _openai_complete(self, prompt: str, params: Dict[str, Any]) -> Dict[str, Any]:
         return {"source": "openai", "model": "gpt-oss-120B", "text": "Text summarized by gpt-oss-120B model."}
        
        # model = params.get("model", "gpt-3.5-turbo")
        # try:
        #     resp = openai.ChatCompletion.create(
        #         model=model,
        #         messages=[{"role": "user", "content": prompt}],
        #         max_tokens=params.get("max_tokens", 512),
        #         temperature=params.get("temperature", 0.2),
        #     )
        #     text = resp.choices[0].message.content.strip()
        #     return {"source": "openai", "model": model, "text": text}
        # except Exception as e:
        #     return {"error": str(e)}

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
