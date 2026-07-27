/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package code.name.monkey.retromusic.activities

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.app.AlarmManager
import org.koin.androidx.viewmodel.ext.android.viewModel
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.text.parseAsHtml
import androidx.core.view.isVisible
import code.name.monkey.appthemehelper.util.VersionUtils
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.activities.base.AbsMusicServiceActivity
import code.name.monkey.retromusic.databinding.ActivityPermissionBinding
import code.name.monkey.retromusic.databinding.ActivityCustomLibraryBinding
import code.name.monkey.retromusic.extensions.*
import code.name.monkey.retromusic.fragments.LibraryViewModel
import code.name.monkey.retromusic.util.PreferenceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PermissionActivity : AbsMusicServiceActivity() {
    private lateinit var binding: ActivityPermissionBinding

    private val libraryViewModel by viewModel<LibraryViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setStatusBarColorAuto()
        setTaskDescriptionColorAuto()
        setupTitle()

        binding.storagePermission.setButtonClick {
            requestPermissions()
        }
        if (VersionUtils.hasMarshmallow()) {
            binding.audioPermission.show()
            binding.audioPermission.setButtonClick {
                if (!hasAudioPermission()) {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    intent.data = ("package:" + applicationContext.packageName).toUri()
                    startActivity(intent)
                }
            }
        }

        if (VersionUtils.hasS()) {
            binding.bluetoothPermission.show()
            binding.bluetoothPermission.setButtonClick {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(BLUETOOTH_CONNECT),
                    BLUETOOTH_PERMISSION_REQUEST
                )
            }
            binding.alarmPermission.show()
            binding.alarmPermission.setButtonClick {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        } else {
            binding.audioPermission.setNumber("2")
        }

        binding.finish.accentBackgroundColor()
        binding.finish.setOnClickListener {
            if (hasPermissions()) {
                showCustomLibraryStep()
            }
        }
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAffinity()
                remove()
            }
        })
    }

    private fun showCustomLibraryStep() {
        val customBinding = ActivityCustomLibraryBinding.inflate(layoutInflater)
        setContentView(customBinding.root)

        customBinding.customLibrary.isEnabled = !PreferenceUtil.fixYear

        customBinding.customLibrary.setButtonClick {
            if (!PreferenceUtil.fixYear) {
                scanCustomLibrary()
                }
        }

        customBinding.finish.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            )
        finish()
        }
    }

    private fun scanCustomLibrary(force: Boolean = false) {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val statusText = android.widget.TextView(this).apply {
            text = "Scanning..."
            setPadding(0, 0, 0, padding / 2)
        }
        container.addView(statusText)

        val progressBar = LinearProgressIndicator(this).apply {
            isIndeterminate = false
        }
        container.addView(progressBar)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Scanning songs")
            .setView(container)
            .setCancelable(false)
            .show()

        libraryViewModel.startMetadataScan(
            this,
            force,
            onProgress = { songTitle, index, total ->
                lifecycleScope.launch(Dispatchers.Main) {
                    statusText.text = "$index / $total"
                    progressBar.max = total
                    progressBar.progress = index
                }
            },
            onComplete = {
                lifecycleScope.launch(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(this@PermissionActivity, "Scan completed!, App will be Restarted", Toast.LENGTH_SHORT).show()
                    PreferenceUtil.fixYear = true
                    restartApp(this@PermissionActivity)
                }
            }
        )
    }

    private fun restartApp(context: android.content.Context) {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(context.packageName)
        intent?.let {
            val mainIntent = Intent.makeRestartActivityTask(it.component)
            context.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }

    private fun setupTitle() {
        val color = accentColor()
        val hexColor = String.format("#%06X", 0xFFFFFF and color)
        val appName =
            getString(
                R.string.message_welcome,
                "<b>Effin <span  style='color:$hexColor';>Music</span></b>"
            )
                .parseAsHtml()
        binding.appNameText.text = appName
    }

    override fun onResume() {
        super.onResume()
        binding.finish.isEnabled = hasStoragePermission()
        if (hasStoragePermission()) {
            binding.storagePermission.checkImage.isVisible = true
            binding.storagePermission.checkImage.imageTintList =
                ColorStateList.valueOf(accentColor())
        }
        if (VersionUtils.hasMarshmallow()) {
            if (hasAudioPermission()) {
                binding.audioPermission.checkImage.isVisible = true
                binding.audioPermission.checkImage.imageTintList =
                    ColorStateList.valueOf(accentColor())
            }
        }
        if (VersionUtils.hasS()) {
            if (hasBluetoothPermission()) {
                binding.bluetoothPermission.checkImage.isVisible = true
                binding.bluetoothPermission.checkImage.imageTintList =
                    ColorStateList.valueOf(accentColor())
            }
            if (hasAlarmPermission()) {
                binding.alarmPermission.checkImage.isVisible = true
                binding.alarmPermission.checkImage.imageTintList =
                    ColorStateList.valueOf(accentColor())
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return hasPermissions()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasBluetoothPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun hasAudioPermission(): Boolean {
        return Settings.System.canWrite(this)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasAlarmPermission(): Boolean {
        return getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true
    }
}
