package tech.kzen.shell.ui

import java.awt.Component
import java.awt.Font
import javax.swing.*
import javax.swing.JPanel
import javax.swing.JTextPane
import java.awt.Desktop
import java.awt.Image
import java.net.URI
import javax.imageio.ImageIO


object DesktopUi {
    //-----------------------------------------------------------------------------------------------------------------
    private const val title = "Kzen"
    private const val location = "http://localhost:8080"

    private var lazyFrame: JFrame? = null



    //-----------------------------------------------------------------------------------------------------------------
    fun show() {
        if (lazyFrame != null) {
            return
        }

        javax.swing.SwingUtilities.invokeLater {
            lazyFrame = createAndShowUi()}
    }


    fun onLoaded() {
        val frame = lazyFrame
                ?: return

        javax.swing.SwingUtilities.invokeLater {
            frame.title = title
            frame.contentPane = loadedPane()
            frame.revalidate()
            frame.repaint()

            openInBrowser()
        }
    }


    private fun openInBrowser(): Boolean {
        val desktop =
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop()
                }
                else {
                    return false
                }

        if (! desktop.isSupported(Desktop.Action.BROWSE)) {
            return false
        }

        return try {
            desktop.browse(URI(location))
            true
        }
        catch (e: Exception) {
            false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun loadedPane(): JPanel {
        val pane = JPanel()
        pane.layout = BoxLayout(pane, BoxLayout.Y_AXIS)

        val logo = JLabel(ImageIcon(logo()))
        logo.alignmentX = Component.CENTER_ALIGNMENT
        pane.add(JLabel(" "))
        pane.add(logo)
        pane.add(JLabel(" "))
        pane.add(JLabel(" "))

        val label = JLabel("Ready:")
        label.font = Font(Font.SANS_SERIF, Font.BOLD, 32)
        label.alignmentX = Component.CENTER_ALIGNMENT
        pane.add(label)

        val f = JTextPane()
        f.contentType = "text/html"
        f.text = "<html><div style='text-align: center;'><span style='font-size: 32px'>$location<span></div></html>"
        f.isEditable = false
        f.isOpaque = false
        f.border = null
        f.font = Font(Font.SANS_SERIF, Font.BOLD, 32)
        f.setSize(f.width, 40)
        f.alignmentX = Component.CENTER_ALIGNMENT
        pane.add(f)

        pane.add(doc("Note: this is not the main UI window,"))
        pane.add(doc("  the UI should open in a browser tab on startup."))
        pane.add(doc("If you don't see the UI, try the button below,"))
        pane.add(doc("  or simply copy the above URL and paste in a browser."))
        pane.add(doc(" "))
        pane.add(doc("To exit the app, which will close all running projects,"))
        pane.add(doc("  simply close this window."))
        pane.add(doc("After exiting, the UI in the browser will not work"))
        pane.add(doc("  until the app is started again."))
        pane.add(doc(" "))

        val open = JButton("Open in browser")
        open.font = Font(Font.SANS_SERIF, Font.BOLD, 32)
        open.alignmentX = Component.CENTER_ALIGNMENT
        open.addActionListener {
            openInBrowser()
        }
        pane.add(open)

        return pane
    }


    private fun loadingPane(): JPanel {
        val pane = JPanel()
        pane.layout = BoxLayout(pane, BoxLayout.Y_AXIS)

        val logo = JLabel(ImageIcon(logo()))
        logo.alignmentX = Component.CENTER_ALIGNMENT
        pane.add(JLabel(" "))
        pane.add(logo)
        pane.add(JLabel(" "))
        pane.add(JLabel(" "))

        val label = JLabel("Loading...")
        label.font = Font(Font.SANS_SERIF, Font.BOLD, 32)
        label.alignmentX = Component.CENTER_ALIGNMENT
        pane.add(label)

        val progressBar = JProgressBar()
        progressBar.isIndeterminate = true
        pane.add(progressBar)

        pane.add(JLabel(" "))
        pane.add(doc("Note: some components may need to be downloaded."))
        pane.add(doc("  If your firewall asks for permission,"))
        pane.add(doc("  please allow access."))

        return pane
    }


    private fun doc(text: String): JLabel {
        val doc = JLabel(text)
        doc.alignmentX = Component.CENTER_ALIGNMENT
        return doc
    }



    private fun logo(): Image {
        return ImageIO
                .read(javaClass.getResource("/logo.png"))
                .getScaledInstance(72, 72, Image.SCALE_DEFAULT)
    }


    private fun createAndShowUi(): JFrame {
        val frame = JFrame("$title - Loading...")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        val mainContainer = loadingPane()
        frame.contentPane = mainContainer

        frame.pack()
        frame.setSize(650, 500)

        frame.setLocationRelativeTo(null)
        frame.isVisible = true

        return frame
    }
}