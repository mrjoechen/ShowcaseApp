import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import java.awt.Color
import javax.swing.JFrame
import javax.swing.UIManager

internal fun configureWindowsWindowDecorations(isDark: Boolean = false) {
    if (!isWindows()) return

    // Install the look and feel before creating the window. Setting the system
    // property alone does not replace the native Windows title bar.
    System.setProperty("flatlaf.useWindowDecorations", "true")
    val current = UIManager.getLookAndFeel() as? FlatLaf
    if (current == null || current.isDark != isDark) {
        if (isDark) FlatDarkLaf.setup() else FlatLightLaf.setup()
        UIManager.put("TitlePane.showIcon", false)
        UIManager.put("RootPane.honorFrameMinimumSizeOnResize", true)
        FlatLaf.updateUI()
    }
}

internal fun applyWindowsTitleBar(frame: JFrame, background: Color, isDark: Boolean) {
    if (!isWindows()) return

    frame.rootPane.apply {
        putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICON, false)
        putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, false)
        putClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT, 32)
        putClientProperty(FlatClientProperties.TITLE_BAR_BACKGROUND, background)
        putClientProperty(
            FlatClientProperties.TITLE_BAR_FOREGROUND,
            if (isDark) Color(0xEE, 0xEE, 0xEE) else Color(0x22, 0x22, 0x22),
        )
    }
}
