package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.res.TypedArrayUtils
import androidx.core.view.isVisible
import androidx.preference.EditTextPreference
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore

class UrlTestPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = TypedArrayUtils.getAttr(
        context, R.attr.editTextPreferenceStyle,
        android.R.attr.editTextPreferenceStyle
    ),
    defStyleRes: Int = 0
) : EditTextPreference(context, attrs, defStyleAttr, defStyleRes) {

    var concurrent: EditText? = null
    var timeout: EditText? = null

    init {
        dialogLayoutResource = R.layout.layout_urltest_preference_dialog

        setOnBindEditTextListener {
            concurrent = it.rootView.findViewById(R.id.edit_concurrent)
            concurrent?.apply {
                setText(DataStore.connectionTestConcurrency.toString())
            }
            timeout = it.rootView.findViewById(R.id.edit_timeout)
            timeout?.apply {
                setText(DataStore.connectionTestTimeout.toString())
            }
            it.rootView.findViewById<LinearLayout>(R.id.concurrent_layout)?.isVisible = true
            it.rootView.findViewById<LinearLayout>(R.id.timeout_layout)?.isVisible = true
        }

        setOnPreferenceChangeListener { _, _ ->
            concurrent?.apply {
                var newConcurrent = text?.toString()?.toIntOrNull()
                if (newConcurrent == null || newConcurrent <= 0) {
                    newConcurrent = 6
                }
                DataStore.connectionTestConcurrency = newConcurrent
            }
            timeout?.apply {
                var newTimeout = text?.toString()?.toIntOrNull()
                if (newTimeout == null || newTimeout <= 0) {
                    newTimeout = 5000
                }
                DataStore.connectionTestTimeout = newTimeout
            }
            true
        }
    }

}
