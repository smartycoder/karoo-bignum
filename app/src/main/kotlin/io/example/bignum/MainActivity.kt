package io.example.bignum

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = getString(R.string.app_name) + "\n\n" +
                getString(R.string.activity_hint)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(48, 96, 48, 48)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(tv)
        }
        setContentView(root)
    }
}
