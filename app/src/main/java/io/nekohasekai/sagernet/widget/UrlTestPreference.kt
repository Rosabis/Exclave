package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.takisoft.preferencex.EditTextPreference
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore

class UrlTestPreference : EditTextPreference {

    var concurrent: EditText? = null
    var timeout: EditText? = null

    constructor(context: Context) : this(context, null)

    constructor(
        context: Context,
        attrs: AttributeSet?,
    ) : this(context, attrs, com.takisoft.preferencex.R.attr.editTextPreferenceStyle)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : this(context, attrs, defStyleAttr, 0)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

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
