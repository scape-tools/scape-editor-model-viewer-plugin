package plugin

import scape.editor.gui.plugin.IPlugin
import scape.editor.gui.plugin.PluginDescriptor

@PluginDescriptor(name = "Model Viewer", authors = ["Nshusa"])
class Plugin : IPlugin {

    override fun applicationIcon(): String {
        return "icons/icon.png"
    }

    override fun fxml(): String {
        return "scene.fxml"
    }

    override fun stylesheets(): Array<String> {
        return arrayOf("css/style.css")
    }
}