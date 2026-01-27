//package com.android.accessibilityuinavigator
//
//import com.openai.client.okhttp.OpenAIOkHttpClient
//import com.openai.models.ChatModel
//import com.openai.models.ResponsesModel
//import com.openai.models.responses.ResponseCreateParams
//import com.openai.models.responses.ResponseInputItem
//
//
//class LLMImplementation() {
//    val client = OpenAIOkHttpClient.fromEnv()
//    var inputs: List<ResponseInputItem> = ArrayList()
//
//    fun callLLM(input: String) {
//        val inputs: MutableList<ResponseInputItem?> = ArrayList()
//
//        inputs.add(
//            ResponseInputItem.ofMessage(
//                ResponseInputItem.Message.builder()
//                    .addInputTextContent(input)
//                    .role(ResponseInputItem.Message.Role.USER)
//                    .build()
//            )
//        )
//    }
//}