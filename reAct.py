from openai import OpenAI
from typing import List, Dict, Any
from tools import *
import json

class ReActAgent:
    def __init__(self, client: OpenAI, system: str, tools: list[dict[str, Any]]):
        self.client = client
        # System contains the specific instructions for the agent
        self.system = system
        self.tools = tools
        # To store the sent messages and responses (so that we can use it furthur), initially we are storing the system message
        self.messages: List[Dict[str, str]] = [{"role": "developer", "content": system}]
        self.max_iterations = 5

    def _get_completion(self):
        completion = self.client.responses.create(
            model="openai/gpt-oss-120b:free",
            input=self.messages,
            tools=self.tools,
        )
        # print(f"response: {completion.output}")
        return completion


    def run(self, query: str):
        # Adds user query to the conversation history
        self.messages.append({"role": "user", "content": query})

        # Each response can be a thought, an action or an answer
        response = self._get_completion()
        print(f"Agent: {response.output}")
        # Let's try to find whether the response contains any action
        final_answer = False
        for item in response.output:
            print(f"item: {item}")
            if item.type == "function_call":

                return {
                    "type": f"{item.type}",
                    "call_id": f"{item.call_id}",
                    "name": f"{item.name}",
                    "args": f"{item.arguments}"
                }

                if item.name == "take_action":
                    # 3. Execute the function logic for take_action

                    result = take_action(**json.loads(item.arguments))
                    
                    # 4. Provide function call results to the model
                    input_list.append({
                        "type": "function_call_output",
                        "call_id": item.call_id,
                        "output": json.dumps({
                            "newNodeTree": result
                        })
                    })
                elif item.name == "open_application":
                    # 3. Execute the function logic for open_application
                    result = open_application(json.loads(item.arguments))
                    
                    # 4. Provide function call results to the model
                    input_list.append({
                        "type": "function_call_output",
                        "call_id": item.call_id,
                        "output": json.dumps({
                            "newNodeTree": result
                        })
                    })
                elif item.name == "input_text":
                    # 3. Execute the function logic for input_text
                    result = input_text(**json.loads(item.arguments))
                    
                    # 4. Provide function call results to the model
                    input_list.append({
                        "type": "function_call_output",
                        "call_id": item.call_id,
                        "output": json.dumps({
                            "newNodeTree": result
                        })
                    })
            elif item.type == "message":
                final_answer = True
                break
        cnt += 1
        # print(input_list)

        if final_answer:
            print(response.output_text)
            return response.output_text

        return "Max iterations reached without finding an answer."



    def _execute_tool(self, tool_name: str, tool_input: str):
      print("" + tool_name + ": " + tool_input)
    #   if not (tool_input.startswith('"') and tool_input.endswith('"')):
    #     if not (tool_name.lower() == "wikipedia"):
    #         tool_input = f'"{tool_input}"'

      for tool in self.tools:
          if tool.name.lower() == tool_name.lower():
              return tool.run(tool_input)

      return f"Tool '{tool_name}' not found."

