package net.foxmediax.scriptfx.client.mui

import icyllis.modernui.fragment.Fragment
import icyllis.modernui.util.DataSet
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.LayoutInflater
import icyllis.modernui.view.View
import icyllis.modernui.view.ViewGroup
import icyllis.modernui.widget.Button
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.TextView
import net.minecraft.client.Minecraft

class TestMuiFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: DataSet?
    ): View {
        val context = requireContext()

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(context).apply {
            text = "ScriptFX · Modern UI OK"
            textSize = 18f
            gravity = Gravity.CENTER
        }

        val hint = TextView(context).apply {
            text = "Если видишь это — Modern UI работает"
            textSize = 14f
            gravity = Gravity.CENTER
        }

        val closeBtn = Button(context).apply {
            text = "Закрыть"
            setOnClickListener {
                Minecraft.getInstance().execute { Minecraft.getInstance().setScreen(null) }
            }
        }

        content.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        )
        content.addView(
            hint,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        )
        content.addView(
            closeBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        return FrameLayout(context).apply {
            addView(
                content,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }
    }
}