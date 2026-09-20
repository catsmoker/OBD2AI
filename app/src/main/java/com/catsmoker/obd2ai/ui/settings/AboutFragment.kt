package com.catsmoker.obd2ai.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.catsmoker.obd2ai.BuildConfig
import com.catsmoker.obd2ai.R

/** App-level About destination (not a settings category). */
class AboutFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.aboutVersion).text =
            getString(R.string.settings_about_version, BuildConfig.VERSION_NAME)
    }
}
