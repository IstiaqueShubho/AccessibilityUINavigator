# class Tool:
#     def __init__(self, name: str, func: callable, description: str):
#         self.name = name
#         self.func = func
#         self.description = description #The agent uses this to decide whether to call it

#     def run(self, inputQ):
#         return self.func(inputQ)

class Tool:
    def __init__(self):
        self.tools = tools

tools = [
    {
        "type": "function",
        "name": "open_application",
        "description": "Open an application on the mobile device",
        "parameters": {
            "type": "object",
            "properties": {
                "appName": {
                    "type": "string",
                    "description": "Name of the application to open"
                }
            },
            "required": ["appName"],
        }
    },
    {
        "type": "function",
        "name": "take_action",
        "description": "Take an action on a node in the accessibility tree",
        "parameters": {
            "type": "object",
            "properties": {
                "nodeInfo": {
                    "type": "string",
                    "description": "Node information for the action"
                },
                "actionType": {
                    "type": "string",
                    "description": "Type of action to perform (e.g., 'click', 'scroll', 'double tap')"
                }
            },
            "required": ["nodeInfo", "actionType"],
        }
    },
    {
        "type": "function",
        "name": "input_text",
        "description": "Input text into a text field in the accessibility tree",
        "parameters": {
            "type": "object",
            "properties": {
                "nodeInfo": {
                    "type": "string",
                    "description": "Node information for the text field"
                },
                "text": {
                    "type": "string",
                    "description": "Text to input into the text field"
                }
            },
            "required": ["nodeInfo", "text"],
        }
    }
]




