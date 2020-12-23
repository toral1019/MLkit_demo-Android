package com.inexture.barcodescanner

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.github.nitrico.lastadapter.LastAdapter
import com.google.firebase.ml.naturallanguage.FirebaseNaturalLanguage
import com.google.firebase.ml.naturallanguage.smartreply.FirebaseTextMessage
import com.inexture.barcodescanner.databinding.ActivitySmartReplyBinding
import android.content.Context.TELEPHONY_SERVICE
import android.graphics.Color
import android.graphics.Typeface.BOLD
import android.telephony.TelephonyManager
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.util.*


class SmartReplyActivity : AppCompatActivity() {

    private lateinit var mBinding: ActivitySmartReplyBinding

    private val messages = arrayListOf<FirebaseTextMessage>()
    private val suggestions = arrayListOf<String>()
    private val smartReply = FirebaseNaturalLanguage.getInstance().smartReply

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_smart_reply)

        setMessageAdapter()
        setSuggestionAdapter()

        mBinding.floatingActionButton.setOnClickListener {

            //The createForRemoteUser() method is to be used when the message was not sent by the user
            val message = FirebaseTextMessage.createForRemoteUser(
                mBinding.etMessage.text.toString(), //Content of the message
                System.currentTimeMillis(), //Time at which the message was sent
                "uid_of_friend" //This has to be unique for every other person involved in the chat who is not your user
            )

            messages.add(
                message
            )

            mBinding.rvMessages.adapter?.notifyDataSetChanged()
            mBinding.etMessage.setText("")

            smartReply.suggestReplies(
                messages.takeLast(2)
            )
                .addOnSuccessListener {
                    Log.d("===result",it.suggestions.toString())
                    suggestions.clear()
                    it.suggestions.forEach {
                        suggestions.add(it.text)
                    }
                    mBinding.rvSuggestions.adapter?.notifyDataSetChanged()
                }
                .addOnFailureListener {
                    it.printStackTrace()
                }

        }
    }

    private fun setMessageAdapter(){
        LastAdapter(messages, BR.item)
            .map<FirebaseTextMessage>(R.layout.item_message_received)
            .into(mBinding.rvMessages)
    }

    private fun setSuggestionAdapter(){
        LastAdapter(suggestions, BR.item)
            .map<String>(R.layout.item_suggestion)
            .into(mBinding.rvSuggestions)
    }
}
