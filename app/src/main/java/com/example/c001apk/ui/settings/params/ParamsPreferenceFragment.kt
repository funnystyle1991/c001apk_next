package com.example.c001apk.ui.settings.params

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.core.graphics.ColorUtils
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import com.example.c001apk.constant.Constants
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.TokenDeviceUtils.applyDefaultFingerprint
import com.example.c001apk.util.TokenDeviceUtils.applyRealDeviceFingerprint
import com.example.c001apk.util.TokenDeviceUtils.defaultDeviceCode
import com.example.c001apk.util.TokenDeviceUtils.detectRealDevice
import com.example.c001apk.util.TokenDeviceUtils.getDeviceCode
import com.example.c001apk.util.TokenDeviceUtils.getLastingDeviceCode
import com.example.c001apk.util.TokenDeviceUtils.getTokenV3
import com.example.c001apk.util.TokenDeviceUtils.randHexString
import com.example.c001apk.util.Utils.randomAndroidVersionRelease
import com.example.c001apk.util.Utils.randomBrand
import com.example.c001apk.util.Utils.randomDeviceModel
import com.example.c001apk.util.Utils.randomManufacturer
import com.example.c001apk.util.Utils.randomSdkInt
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ParamsPreferenceFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ): RecyclerView {
        val recyclerView =
            super.onCreateRecyclerView(inflater, parent, savedInstanceState)
        recyclerView.apply {
            isVerticalScrollBarEnabled = false
        }
        return recyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDivider(resources.getDrawable(R.drawable.divider, requireContext().theme))
        PrefManager.registerOnSharedPreferenceChangeListener(this)
    }

    @SuppressLint("SetTextI18n", "InflateParams")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.params, null)

        // ---- 机型检测 ----
        findPreference<SwitchPreferenceCompat>("reportRealDevice")?.apply {
            isChecked = PrefManager.reportRealDevice
            setOnPreferenceChangeListener { _, newValue ->
                val on = newValue as Boolean
                PrefManager.reportRealDevice = on
                // 立刻重建设备串，不必等下一次请求；关掉则回落到官方认可的那一组
                if (on) applyRealDeviceFingerprint() else applyDefaultFingerprint()
                updateRealDeviceSummary()
                Snackbar.make(
                    requireView(),
                    if (on) "已按本机机型上报（需酷安收录该机型，否则帖子下方不显示）"
                    else "已回落到默认机型",
                    Snackbar.LENGTH_LONG
                ).show()
                true
            }
        }

        findPreference<Preference>("realDeviceInfo")?.apply {
            setOnPreferenceClickListener {
                val d = detectRealDevice()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.real_device_info)
                    .setMessage(
                        "厂商：${d.manufacturer}\n品牌：${d.brand}\n型号：${d.model}\n" +
                            "版本：${d.buildNumber}\nAndroid：${d.androidVersion}" +
                            "（API ${d.sdkInt}）"
                    )
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.real_device_apply) { _, _ ->
                        applyRealDeviceFingerprint(d)
                        PrefManager.reportRealDevice = true
                        updateRealDeviceSummary()
                        Snackbar.make(requireView(), "设备串已按本机机型重建", Snackbar.LENGTH_SHORT)
                            .show()
                    }
                    .show()
                true
            }
        }
        updateRealDeviceSummary()

        findPreference<Preference>("VERSION_NAME")?.apply {
            summary = PrefManager.VERSION_NAME
            setOnPreferenceClickListener {
                val view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_x_app_token, null, false)
                val editText: EditText = view.findViewById(R.id.editText)
                editText.highlightColor = ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorPrimaryDark,
                        0
                    ), 128
                )
                editText.setText(PrefManager.VERSION_NAME)
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setView(view)
                    setTitle("VERSION_NAME")
                    setNegativeButton(android.R.string.cancel, null)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        PrefManager.VERSION_NAME =
                            editText.text.toString().ifEmpty { Constants.VERSION_NAME }
                        updateUserAgent()
                    }
                }.create().apply {
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                    editText.requestFocus()
                }.show()
                true
            }
        }

        findPreference<Preference>("API_VERSION")?.apply {
            summary = PrefManager.API_VERSION
            setOnPreferenceClickListener {
                val view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_x_app_token, null, false)
                val editText: EditText = view.findViewById(R.id.editText)
                editText.highlightColor = ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorPrimaryDark,
                        0
                    ), 128
                )
                editText.setText(PrefManager.API_VERSION)
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setView(view)
                    setTitle("API_VERSION")
                    setNegativeButton(android.R.string.cancel, null)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        PrefManager.API_VERSION =
                            editText.text.toString().ifEmpty { Constants.API_VERSION }
                    }
                }.create().apply {
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                    editText.requestFocus()
                }.show()
                true
            }
        }

        findPreference<Preference>("VERSION_CODE")?.apply {
            summary = PrefManager.VERSION_CODE
            setOnPreferenceClickListener {
                val view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_x_app_token, null, false)
                val editText: EditText = view.findViewById(R.id.editText)
                editText.highlightColor = ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorPrimaryDark,
                        0
                    ), 128
                )
                editText.setText(PrefManager.VERSION_CODE)
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setView(view)
                    setTitle("VERSION_CODE")
                    setNegativeButton(android.R.string.cancel, null)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        PrefManager.VERSION_CODE =
                            editText.text.toString().ifEmpty { Constants.VERSION_CODE }
                        updateUserAgent()
                    }
                }.create().apply {
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                    editText.requestFocus()
                }.show()
                true
            }
        }

        findPreference<Preference>("MANUFACTURER")?.apply {
            summary = PrefManager.MANUFACTURER
            setOnPreferenceClickListener {
                val view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_x_app_token, null, false)
                val editText: EditText = view.findViewById(R.id.editText)
                editText.highlightColor = ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorPrimaryDark,
                        0
                    ), 128
                )
                editText.setText(PrefManager.MANUFACTURER)
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setView(view)
                    setTitle("MANUFACTURER")
                    setNeutralButton(R.string.system_info) { _, _ ->
                        PrefManager.MANUFACTURER = android.os.Build.MANUFACTURER
                        PrefManager.xAppDevice = getDeviceCode(false)
                    }
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        PrefManager.MANUFACTURER =
                            editText.text.toString().ifEmpty { randomManufacturer() }
                        PrefManager.xAppDevice = getDeviceCode(false)
                    }
                    setNegativeButton(R.string.random_value) { _, _ ->
                        PrefManager.MANUFACTURER = randomManufacturer()
                        PrefManager.xAppDevice = getDeviceCode(false)
                    }
                }.create().apply {
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                    editText.requestFocus()
                }.show()
                true
            }
        }

        findPreference<Preference>("BRAND")?.apply {
            summary = PrefManager.BRAND
            setOnPreferenceClickListener {
                val view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_x_app_token, null, false)
                val editText: EditText = view.findViewById(R.id.editText)
                editText.highlightColor = ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorPrimaryDark,
                        0
                    ), 128
                )
                editText.setText(PrefManager.BRAND)
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setView(view)
                    setTitle("BRAND")
                    setNeutralButton(R.string.system_info) { _, _ ->
                        PrefManager.BRAND = android.os.Build.BRAND
                        PrefManager.xAppDevice = getDeviceCode(false)
                        updateUserAgent()
                    }
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        PrefManager.BRAND = editText.text.toString().ifEmpty { randomBrand() }
                        PrefManager.xAppDevice = getDeviceCode(false)
                        updateUserAgent()
                    }
                    setNegativeButton(R.string.random_value) { _, _ ->
                        PrefManager.BRAND = randomBrand()
                        PrefManager.xAppDevice = getDeviceCode(false)
                        updateUserAgent()
                    }
                }.create().apply {
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                    editText.requestFocus()
                }.show()
                true
            }
        }

        findPreference<Preference>("MODEL")?.apply {
            summary = PrefManager.MODEL
            setOnPreferenceClickListener {
                val view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_x_app_token, null, false)
                val editText: EditText = view.findViewById(R.id.editText)
                editText.highlightColor = ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorPrimaryDark,
                        0
                    ), 128
                )
                editText.setText(PrefManager.MODEL)
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setView(view)
                    setTitle("MODEL")
                    setNeutralButton(R.string.system_info) { _, _ ->
                        PrefManager.MODEL = android.os.Build.MODEL
                        PrefManager.xAppDevice = getDeviceCode(false)
                        updateUserAgent()
                    }
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        PrefManager.MODEL =
                            editText.text.toString().ifEmpty { randomDeviceModel() }
                        PrefManager.xAppDevice = getDeviceCode(false)
                        updateUserAgent()
                    }
                    setNegativeButton(R.string.random_value) { _, _ ->
                        PrefManager.MODEL = randomDeviceModel()
                        PrefManager.xAppDevice = getDeviceCode(false)
                        updateUserAgent()
                    }
                }.create().apply {
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                    editText.requestFocus()
                }.show()
                true
            }
        }

        findPreference<Preference>("BUILDNUMBER")?.apply {
            summary = PrefManager.BUILDNUMBER
            setOnPreferenceClickListener {
                val view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_x_app_token, null, false)
                val editText: EditText = view.findViewById(R.id.editText)
                editText.highlightColor = ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorPrimaryDark,
                        0
                    ), 128
                )
                editText.setText(PrefManager.BUILDNUMBER)
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setView(view)
                    setTitle("BUILDNUMBER")
                    setNeutralButton(R.string.system_info) { _, _ ->
                        PrefManager.BUILDNUMBER = android.os.Build.DISPLAY
                        PrefManager.xAppDevice = getDeviceCode(false)
                        updateUserAgent()
                    }
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        PrefManager.BUILDNUMBER =
                            editText.text.toString().ifEmpty { randHexString(16) }
                        PrefManager.xAppDevice = getDeviceCode(false)
                        updateUserAgent()
                    }
                    setNegativeButton(R.string.random_value) { _, _ ->
                        PrefManager.BUILDNUMBER = randHexString(16)
                        PrefManager.xAppDevice = getDeviceCode(false)
                        updateUserAgent()
                    }
                }.create().apply {
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                    editText.requestFocus()
                }.show()
                true
            }
        }

        findPreference<Preference>("SDK_INT")?.apply {
            summary = PrefManager.SDK_INT
            setOnPreferenceClickListener {
                val view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_x_app_token, null, false)
                val editText: EditText = view.findViewById(R.id.editText)
                editText.highlightColor = ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorPrimaryDark,
                        0
                    ), 128
                )
                editText.setText(PrefManager.SDK_INT)
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setView(view)
                    setTitle("SDK_INT")
                    setNeutralButton(R.string.system_info) { _, _ ->
                        PrefManager.SDK_INT = android.os.Build.VERSION.SDK_INT.toString()
                    }
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        PrefManager.SDK_INT = editText.text.toString().ifEmpty { randomSdkInt() }
                    }
                    setNegativeButton(R.string.random_value) { _, _ ->
                        PrefManager.SDK_INT = randomSdkInt()
                    }
                }.create().apply {
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                    editText.requestFocus()
                }.show()
                true
            }
        }

        findPreference<Preference>("ANDROID_VERSION")?.apply {
            summary = PrefManager.ANDROID_VERSION
            setOnPreferenceClickListener {
                val view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_x_app_token, null, false)
                val editText: EditText = view.findViewById(R.id.editText)
                editText.highlightColor = ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorPrimaryDark,
                        0
                    ), 128
                )
                editText.setText(PrefManager.ANDROID_VERSION)
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setView(view)
                    setTitle("ANDROID_VERSION")
                    setNeutralButton(R.string.system_info) { _, _ ->
                        PrefManager.ANDROID_VERSION = android.os.Build.VERSION.RELEASE
                        updateUserAgent()
                    }
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        PrefManager.ANDROID_VERSION =
                            editText.text.toString().ifEmpty { randomAndroidVersionRelease() }
                        updateUserAgent()
                    }
                    setNegativeButton(R.string.random_value) { _, _ ->
                        PrefManager.ANDROID_VERSION = randomAndroidVersionRelease()
                        updateUserAgent()
                    }
                }.create().apply {
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                    editText.requestFocus()
                }.show()
                true
            }
        }

        findPreference<Preference>("USER_AGENT")?.apply {
            summary = PrefManager.USER_AGENT
            setOnPreferenceClickListener {
                val view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_x_app_token, null, false)
                val editText: EditText = view.findViewById(R.id.editText)
                editText.highlightColor = ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorPrimaryDark,
                        0
                    ), 128
                )
                editText.setText(PrefManager.USER_AGENT)
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setView(view)
                    setTitle("USER_AGENT")
                    setNegativeButton(android.R.string.cancel, null)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        PrefManager.USER_AGENT = editText.text.toString()
                            .ifEmpty { Constants.USER_AGENT }
                    }
                }.create().apply {
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                    editText.requestFocus()
                }.show()
                true
            }
        }

        findPreference<Preference>("xAppToken")?.apply {
            // 按当前参数实时生成 v3 token，方便抓包对照/排错
            PrefManager.xAppToken = getLastingDeviceCode().getTokenV3(PrefManager.VERSION_CODE)
            summary = PrefManager.xAppToken
            setOnPreferenceClickListener {
                val view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_x_app_token, null, false)
                val editText: EditText = view.findViewById(R.id.editText)
                editText.highlightColor = ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorPrimaryDark,
                        0
                    ), 128
                )
                editText.setText(PrefManager.xAppToken)
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setView(view)
                    setTitle("X-App-Token")
                    setNegativeButton(android.R.string.cancel, null)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        PrefManager.xAppToken = editText.text.toString()
                    }
                }.create().apply {
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                    editText.requestFocus()
                }.show()
                true
            }
        }

        findPreference<Preference>("xAppDevice")?.apply {
            summary = PrefManager.xAppDevice
            setOnPreferenceClickListener {
                val view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_x_app_token, null, false)
                val editText: EditText = view.findViewById(R.id.editText)
                editText.highlightColor = ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorPrimaryDark,
                        0
                    ), 128
                )
                editText.setText(PrefManager.xAppDevice)
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setView(view)
                    setTitle("X-App-Device")
                    setNegativeButton(android.R.string.cancel, null)
                    // 手填/粘贴的整串设备串会被风控要求验证码，这里给一条一键回退的通道
                    setNeutralButton("恢复默认") { _, _ ->
                        applyDefaultFingerprint()
                        Snackbar.make(requireView(), "已恢复默认设备串", Snackbar.LENGTH_SHORT)
                            .show()
                    }
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        val device = editText.text.toString()
                        PrefManager.xAppDevice = device.ifEmpty { defaultDeviceCode() }
                        // 与默认串一致就不算自定义，避免以后指纹升级时被这份旧值卡住
                        // （注意默认串含 PrefManager.SZLMID，不能用 Constants.DEFAULT_DEVICE_CODE 比）
                        PrefManager.customFingerprint =
                            PrefManager.xAppDevice != defaultDeviceCode()
                        if (PrefManager.customFingerprint)
                            Snackbar.make(
                                requireView(),
                                "自定义设备串可能被酷安风控要求验证码",
                                Snackbar.LENGTH_LONG
                            ).show()
                    }
                }.create().apply {
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                    editText.requestFocus()
                }.show()
                true
            }
        }

        findPreference<Preference>("regenerate")?.setOnPreferenceClickListener {
            PrefManager.xAppDevice = getDeviceCode(true)
            // 随机生成 = 显式自定义：置位后不再被自动还原
            // （只随机机型；szlmId 走 PrefManager.SZLMID，MAC/尾字段沿用骨架；
            //  但随机机型未必被酷安收录，帖子下方那行「来自 xxx」可能不显示）
            PrefManager.customFingerprint = true
            Snackbar.make(
                requireView(),
                "已重新生成随机机型（酷安未收录的机型不会显示）",
                Snackbar.LENGTH_LONG
            ).show()
            true
        }

    }

    /** 「本机机型检测」条目摘要：本机型号 + 当前是否按它上报 */
    private fun updateRealDeviceSummary() {
        findPreference<SwitchPreferenceCompat>("reportRealDevice")?.isChecked =
            PrefManager.reportRealDevice
        findPreference<Preference>("realDeviceInfo")?.summary = detectRealDevice().let {
            "${it.brand} ${it.model}（Android ${it.androidVersion}）" +
                if (PrefManager.reportRealDevice) " · 上报中" else " · 未上报（用默认机型）"
        }
    }

    private fun updateUserAgent() {
        PrefManager.USER_AGENT =
            "Dalvik/2.1.0 (Linux; U; Android ${PrefManager.ANDROID_VERSION}; ${PrefManager.MODEL} ${PrefManager.BUILDNUMBER}) (#Build; ${PrefManager.BRAND}; ${PrefManager.MODEL}; ${PrefManager.BUILDNUMBER}; ${PrefManager.ANDROID_VERSION}) +CoolMarket/${PrefManager.VERSION_NAME}-${PrefManager.VERSION_CODE}-${Constants.MODE}"
    }

    override fun onDestroyView() {
        PrefManager.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroyView()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "VERSION_NAME" -> findPreference<Preference>(key)?.summary = PrefManager.VERSION_NAME
            "VERSION_CODE" -> findPreference<Preference>(key)?.summary = PrefManager.VERSION_CODE
            "API_VERSION" -> findPreference<Preference>(key)?.summary = PrefManager.API_VERSION
            "MANUFACTURER" -> {
                findPreference<Preference>(key)?.summary = PrefManager.MANUFACTURER
                updateRealDeviceSummary()
            }
            "BRAND" -> {
                findPreference<Preference>(key)?.summary = PrefManager.BRAND
                updateRealDeviceSummary()
            }
            "MODEL" -> {
                findPreference<Preference>(key)?.summary = PrefManager.MODEL
                updateRealDeviceSummary()
            }
            "BUILDNUMBER" -> {
                findPreference<Preference>(key)?.summary = PrefManager.BUILDNUMBER
                updateRealDeviceSummary()
            }
            "SDK_INT" -> findPreference<Preference>(key)?.summary = PrefManager.SDK_INT
            "ANDROID_VERSION" -> findPreference<Preference>(key)?.summary = PrefManager.ANDROID_VERSION
            "USER_AGENT" -> findPreference<Preference>(key)?.summary = PrefManager.USER_AGENT
            "xAppDevice" -> findPreference<Preference>(key)?.summary = PrefManager.xAppDevice
            "reportRealDevice" -> updateRealDeviceSummary()
        }
    }
}