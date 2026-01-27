import os
import threading
import requests
from flask import Flask, request, jsonify
from llm_adapter import LLMAdapter

app = Flask(__name__)

# In-memory storage for Android callback URLs (keyed by name)
CALLBACK_REGISTRY = {}

llm = LLMAdapter()


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})


@app.route("/register_callback", methods=["POST"])
def register_callback():
    data = request.get_json(force=True)
    url = data.get("callback_url")
    name = data.get("name", "default")
    if not url:
        return jsonify({"error": "callback_url required"}), 400
    CALLBACK_REGISTRY[name] = url
    return jsonify({"registered": True, "name": name, "callback_url": url})


@app.route("/invoke", methods=["POST"])
def invoke():
    """Synchronous model invocation. Returns result immediately."""
    data = request.get_json(force=True)
    print(data)
    task = data.get("task")
    if not task:
        return jsonify({"error": "task required"}), 400
    result = llm.complete(task, params=data.get("params", {}))
    return jsonify({"result": result})


def _call_callback(url, payload, timeout=10):
    try:
        resp = requests.post(url, json=payload, timeout=timeout)
        return {"ok": True, "status_code": resp.status_code, "body": resp.text}
    except Exception as e:
        return {"ok": False, "error": str(e)}


@app.route("/invoke_async", methods=["POST"])
def invoke_async():
    """Invoke a model task and (optionally) call back a registered Android callback asynchronously.

    JSON input:
      {
        "task": "Describe ...",
        "callback_name": "default"   # optional: name provided at register
      }
    """
    data = request.get_json(force=True)
    task = data.get("task")
    if not task:
        return jsonify({"error": "task required"}), 400

    callback_name = data.get("callback_name", "default")
    callback_url = CALLBACK_REGISTRY.get(callback_name)

    def worker(task, callback_url, request_id=None):
        res = llm.complete(task)
        payload = {"task": task, "result": res, "request_id": request_id}
        if callback_url:
            _call_callback(callback_url, payload)

    # Start background thread to compute and call back
    t = threading.Thread(target=worker, args=(task, callback_url, data.get("request_id")))
    t.daemon = True
    t.start()

    return jsonify({"started": True, "callback_registered": bool(callback_url)})


if __name__ == "__main__":
    # Host on 0.0.0.0 so Android devices on the same LAN can reach it.
    port = int(os.environ.get("PORT", 8000))
    app.run(host="0.0.0.0", port=port, debug=True)
