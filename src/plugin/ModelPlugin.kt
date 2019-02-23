package plugin

import scape.editor.gui.plugin.Plugin
import scape.editor.gui.plugin.extension.PluginExtension

@Plugin(name = "Model Viewer", authors = ["Nshusa"])
class ModelPlugin : PluginExtension() {

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