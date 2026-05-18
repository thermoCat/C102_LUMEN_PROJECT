package com.ssafy.smartcane.crosswalk

import com.ssafy.smartcane.lumen2.assist.AssistFeedbackController

class PhoneSpeechOutput(
    private val feedbackController: AssistFeedbackController
) : SpeechOutput {
    override fun speak(text: String) {
        feedbackController.speak(text)
    }
}
