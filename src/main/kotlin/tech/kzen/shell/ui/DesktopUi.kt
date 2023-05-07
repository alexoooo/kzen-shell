package tech.kzen.shell.ui

import java.awt.*
import java.awt.event.*
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.system.exitProcess


object DesktopUi {
    //-----------------------------------------------------------------------------------------------------------------
    private const val title = "Kzen"
//    private const val location = "http://localhost:8080"

    private var loaded: Boolean = false
    private var hideWhenMinimized: Boolean = SystemTray.isSupported()
    private var lazyFrame: JFrame? = null


    //-----------------------------------------------------------------------------------------------------------------
    private var port: Int = -1


    fun setPort(port: Int) {
        this.port = port
    }


    private fun port(): Int {
        check(port != -1)
        return port
    }


    private fun location(): URI {
        return URI("http://localhost:${port()}")
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun show() {
        if (lazyFrame != null) {
            return
        }

        SwingUtilities.invokeLater {
            selectTheme()
            lazyFrame = createAndShowUi()
            addToSystemTray(lazyFrame!!)
        }
    }


    fun onLoaded() {
        loaded = true
        val frame = lazyFrame
                ?: return

        SwingUtilities.invokeLater {
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
            desktop.browse(location())
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

        val locationText = location().toString()

        val f = JTextPane()
        f.contentType = "text/html"
        f.text = "<html><div style='text-align: center;'><span style='font-size: 32px'>$locationText<span></div></html>"
        f.isEditable = false
        f.isOpaque = false
        f.border = null
        f.font = Font(Font.SANS_SERIF, Font.BOLD, 32)
        f.setSize(f.width, 40)
        f.alignmentX = Component.CENTER_ALIGNMENT
        pane.add(f)

        pane.add(doc(" "))

        if (SystemTray.isSupported()) {
            val hideControl = JCheckBox("Hide when minimized")
            hideControl.isSelected = hideWhenMinimized
            hideControl.addItemListener {
                hideWhenMinimized = (it.stateChange == ItemEvent.SELECTED)
            }
            pane.add(hideControl)
            pane.add(doc("(When hidden, this window can be restored from the System Tray icon)"))

            pane.add(doc(" "))
        }

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

        pane.add(doc(" "))

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


    private fun selectTheme() {
        for (info in UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus" == info.name) {
                UIManager.setLookAndFeel(info.className)
                break
            }
        }
    }


    private fun createAndShowUi(): JFrame {
        val frame = JFrame("$title - Loading...")

        frame.addWindowListener(object: WindowAdapter() {
            override fun windowIconified(e: WindowEvent?) {
                if (loaded && hideWhenMinimized) {
                    frame.isVisible = false
                }
            }
        })

        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        frame.iconImage = ImageIO.read(javaClass.getResource(
                "/logo-black-on-half-white.png"))

        val mainContainer = loadingPane()
        frame.contentPane = mainContainer

        frame.pack()
        frame.setSize(650, 600)

        frame.setLocationRelativeTo(null)
        frame.isVisible = true

        return frame
    }


    private fun addToSystemTray(frame: JFrame) {
        if (! SystemTray.isSupported()) {
            return
        }
        val systemTray = SystemTray.getSystemTray()

        val trayImage = ImageIO
                .read(javaClass.getResource("/logo-black-on-half-white.png"))

        val trayIcon = TrayIcon(trayImage,"Kzen")
        val popup = PopupMenu()

        val exitItem = MenuItem("Exit")
        exitItem.addActionListener {
            exitProcess(0)
        }

        popup.add(exitItem)

        trayIcon.popupMenu = popup
        trayIcon.isImageAutoSize = true

//        MenuSelectionManager.defaultManager().selectedPath = arrayOf(popup)
        trayIcon.addMouseListener(object: MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                if (e?.button != MouseEvent.BUTTON1) {
                    return
                }

                // see: https://stackoverflow.com/a/4005554/1941359
                frame.isVisible = true
                frame.state = JFrame.NORMAL

                SwingUtilities.invokeLater {
                    frame.toFront()
//                    frame.repaint()
                    frame.requestFocus()
                }
            }
        })

//        trayIcon.addActionListener {
//            frame.toFront()
//            frame.repaint()
//        }

        systemTray.add(trayIcon)
    }
}