package code.name.monkey.retromusic.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.DialogPlaybackSpeedBinding
import code.name.monkey.retromusic.extensions.accent
import code.name.monkey.retromusic.extensions.colorButtons
import code.name.monkey.retromusic.extensions.materialDialog
import code.name.monkey.retromusic.util.PreferenceUtil
import com.google.android.material.slider.Slider

class PlaybackSpeedDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogPlaybackSpeedBinding.inflate(layoutInflater)
        binding.playbackSpeedSlider.accent()
        binding.playbackPitchSlider.accent()
        PreferenceUtil.playbackSpeed.toString().also { binding.speedValue.text = it }
        PreferenceUtil.playbackPitch.toString().also { binding.pitchValue.text = it }
        binding.playbackSpeedSlider.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            binding.speedValue.text = "$value"
            PreferenceUtil.playbackSpeed = value
        })
        binding.playbackPitchSlider.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            binding.pitchValue.text = "$value"
            PreferenceUtil.playbackPitch = value
        })
        binding.playbackSpeedSlider.value = PreferenceUtil.playbackSpeed
        binding.playbackPitchSlider.value = PreferenceUtil.playbackPitch

        val dialog = materialDialog(R.string.playback_settings)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.reset_action, null) // <- no lambda here
            .setView(binding.root)
            .create()
            .colorButtons()

        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_NEUTRAL)?.setOnClickListener {
                val resetSpeed = 1F
                val resetPitch = 1F
                binding.playbackSpeedSlider.value = resetSpeed
                binding.playbackPitchSlider.value = resetPitch
                binding.speedValue.text = "$resetSpeed"
                binding.pitchValue.text = "$resetPitch"
                PreferenceUtil.playbackSpeed = resetSpeed
                PreferenceUtil.playbackPitch = resetPitch
            }
        }
        return dialog
    }

    private fun updatePlaybackAndPitch(speed: Float, pitch: Float) {
        PreferenceUtil.playbackSpeed = speed
        PreferenceUtil.playbackPitch = pitch
    }

    companion object {
        fun newInstance(): PlaybackSpeedDialog {
            return PlaybackSpeedDialog()
        }
    }
}
