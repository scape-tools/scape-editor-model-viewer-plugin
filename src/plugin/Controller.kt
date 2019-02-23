package plugin

import javafx.animation.AnimationTimer
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.collections.transformation.SortedList
import javafx.concurrent.Task
import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.scene.canvas.Canvas
import javafx.scene.control.*
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.text.Font
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import scape.editor.fs.RSFileStore
import scape.editor.fs.graphics.RSModel
import scape.editor.gui.App
import scape.editor.gui.controller.BaseController
import scape.editor.util.CompressionUtils
import java.io.File
import java.net.URL
import java.util.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.Desktop
import java.io.ByteArrayInputStream
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO

class Controller : BaseController() {

    val triangles = mutableListOf<Triangle>()

    lateinit var listView: ListView<ModelWrapper>

    lateinit var canvas : Canvas

    lateinit var yawSlider : Slider

    lateinit var pitchSlider : Slider

    lateinit var rollSlider: Slider

    lateinit var scaleSlider: Slider

    lateinit var translateXSlider: Slider
    lateinit var translateYSlider: Slider
    lateinit var translateZSlider: Slider

    val data = FXCollections.observableArrayList<ModelWrapper>()

    lateinit var searchTf: TextField

    lateinit var fillCb: CheckBox

    lateinit var shadingCb: CheckBox

    lateinit var edgesCb: CheckBox

    lateinit var verticesCb: CheckBox

    override fun initialize(location: URL?, resources: ResourceBundle?) {

        val filteredList = FilteredList(data, {_ -> true})
        searchTf.textProperty().addListener { _, _, newValue -> filteredList.setPredicate { it ->
            if (newValue == null || newValue.isEmpty()) {
                return@setPredicate true
            }

            val lowercase = newValue.toLowerCase()

            if (it.toString().contains(lowercase)) {
                return@setPredicate true
            }

            return@setPredicate false
        }
        }

        val sortedList = SortedList(filteredList)
        listView.items = sortedList

        listView.selectionModel.selectedItemProperty().addListener { _, _, newValue ->

            if (newValue == null) {
                return@addListener
            }

            try {

                var data = ByteArray(0)

                if (newValue.id != -1) {
                    val fs = App.fs
                    val gzipped = fs.readFile(RSFileStore.MODEL_FILE_STORE, newValue.id) ?: return@addListener
                    data = CompressionUtils.degzip(gzipped)
                } else {
                    if (Files.exists(Paths.get(newValue.path))) {
                        val fileData = Files.readAllBytes(Paths.get(newValue.path))

                        if (CompressionUtils.isGZipped(ByteArrayInputStream(fileData))) {
                            data = CompressionUtils.degzip(ByteBuffer.wrap(fileData))
                        } else {
                            data = fileData
                        }
                    }
                }

                if (data.isEmpty()) {
                    return@addListener
                }

                val model = RSModel.decode(data)

                // rotate model by 180 degrees
                model.rotateClockwise()
                model.rotateClockwise()

                triangles.clear()

                for (i in 0 until model.faces) {
                    val faceA = model.facesA[i]
                    val faceB = model.facesB[i]
                    val faceC = model.facesC[i]

                    val vertexAx = model.verticesX[faceA]
                    val vertexAy = model.verticesY[faceA]
                    val vertexAz = model.verticesZ[faceA]

                    val vertexBx = model.verticesX[faceB]
                    val vertexBy = model.verticesY[faceB]
                    val vertexBz = model.verticesZ[faceB]

                    val vertexCx = model.verticesX[faceC]
                    val vertexCy = model.verticesY[faceC]
                    val vertexCz = model.verticesZ[faceC]

                    val hsb = model.colors[i].toInt()
                    val rgb = hsbToRGB(hsb)

                    triangles.add(Triangle(
                            Vertex(vertexAx.toDouble(), vertexAy.toDouble(), vertexAz.toDouble(), 1.0),
                            Vertex(vertexBx.toDouble(), vertexBy.toDouble(), vertexBz.toDouble(), 1.0),
                            Vertex(vertexCx.toDouble(), vertexCy.toDouble(), vertexCz.toDouble(), 1.0), Color(rgb)))
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }

        }

        val g = canvas.graphicsContext2D
        g.font = Font.font("Calibri", 12.0)

        object: AnimationTimer() {
            override fun handle(now: Long) {
                val offsetX = canvas.width / 2
                val offsetY = canvas.height / 2

                g.clearRect(0.0, 0.0, canvas.width, canvas.height)

                val yaw = Math.toRadians(yawSlider.value)

                val yawTransform = Matrix4(doubleArrayOf(
                        Math.cos(yaw), 0.0, -Math.sin(yaw), 0.0,
                        0.0, 1.0, 0.0, 0.0,
                        Math.sin(yaw), 0.0, Math.cos(yaw), 0.0,
                        0.0, 0.0, 0.0, 1.0))

                val pitch = Math.toRadians(pitchSlider.value)

                val pitchTransform = Matrix4(doubleArrayOf(
                        1.0, 0.0, 0.0, 0.0,
                        0.0, Math.cos(pitch), Math.sin(pitch), 0.0,
                        0.0, -Math.sin(pitch), Math.cos(pitch), 0.0,
                        0.0, 0.0, 0.0, 1.0))

                val roll = Math.toRadians(rollSlider.value)
                val rollTransform = Matrix4(doubleArrayOf(
                        Math.cos(roll), -Math.sin(roll), 0.0, 0.0,
                        Math.sin(roll), Math.cos(roll), 0.0, 0.0,
                        0.0, 0.0, 1.0, 0.0,
                        0.0, 0.0, 0.0, 1.0))

                val zoom = scaleSlider.value

                val panOutTransform = Matrix4(doubleArrayOf(
                        zoom, 0.0, 0.0, 0.0,
                        0.0, zoom, 0.0, 0.0,
                        0.0, 0.0, zoom, 0.0,
                        0.0, 0.0, 0.0, 1.0))

                val translate = Matrix4(doubleArrayOf(
                        1.0, 0.0, 0.0, 0.0,
                        0.0, 1.0, 0.0, 0.0,
                        0.0, 0.0, 1.0, 0.0,
                        translateXSlider.value, translateYSlider.value, translateZSlider.value, 1.0
                ))

                val transform = yawTransform.multiply(pitchTransform).multiply(rollTransform).multiply(panOutTransform).multiply(translate)

                if (fillCb.isSelected) {
                    val img = BufferedImage(canvas.width.toInt(), canvas.height.toInt(), BufferedImage.TYPE_INT_ARGB)

                    val zBuffer = DoubleArray(img.width * img.height)
                    // initialize array with extremely far away depths
                    for (q in zBuffer.indices) {
                        zBuffer[q] = java.lang.Double.NEGATIVE_INFINITY
                    }

                    for (t in triangles) {
                        val v1 = transform.transform(t.v1)
                        val v2 = transform.transform(t.v2)
                        val v3 = transform.transform(t.v3)


                        val ab = Vertex(v2.x - v1.x, v2.y - v1.y, v2.z - v1.z, v2.w - v1.w)
                        val ac = Vertex(v3.x - v1.x, v3.y - v1.y, v3.z - v1.z, v3.w - v1.w)
                        val norm = Vertex(
                                ab.y * ac.z - ab.z * ac.y,
                                ab.z * ac.x - ab.x * ac.z,
                                ab.x * ac.y - ab.y * ac.x,
                                1.0
                        )
                        val normalLength = Math.sqrt(norm.x * norm.x + norm.y * norm.y + norm.z * norm.z)
                        norm.x /= normalLength
                        norm.y /= normalLength
                        norm.z /= normalLength

                        val angleCos = Math.abs(norm.z)

                        // translate
                        v1.x += offsetX
                        v1.y += offsetY
                        v2.x += offsetX
                        v2.y += offsetY
                        v3.x += offsetX
                        v3.y += offsetY

                        // compute rectangular bounds for triangle
                        val minX = Math.max(0.0, Math.ceil(Math.min(v1.x, Math.min(v2.x, v3.x)))).toInt()
                        val maxX = Math.min((img.width - 1).toDouble(),
                                Math.floor(Math.max(v1.x, Math.max(v2.x, v3.x)))).toInt()
                        val minY = Math.max(0.0, Math.ceil(Math.min(v1.y, Math.min(v2.y, v3.y)))).toInt()
                        val maxY = Math.min((img.height - 1).toDouble(),
                                Math.floor(Math.max(v1.y, Math.max(v2.y, v3.y)))).toInt()

                        val triangleArea = (v1.y - v3.y) * (v2.x - v3.x) + (v2.y - v3.y) * (v3.x - v1.x)

                        for (y in minY..maxY) {
                            for (x in minX..maxX) {
                                val b1 = ((y - v3.y) * (v2.x - v3.x) + (v2.y - v3.y) * (v3.x - x)) / triangleArea
                                val b2 = ((y - v1.y) * (v3.x - v1.x) + (v3.y - v1.y) * (v1.x - x)) / triangleArea
                                val b3 = ((y - v2.y) * (v1.x - v2.x) + (v1.y - v2.y) * (v2.x - x)) / triangleArea
                                if (b1 >= 0 && b1 <= 1 && b2 >= 0 && b2 <= 1 && b3 >= 0 && b3 <= 1) {
                                    val depth = b1 * v1.z + b2 * v2.z + b3 * v3.z
                                    val zIndex = y * img.width + x
                                    if (zBuffer[zIndex] < depth) {
                                        img.setRGB(x, y, if (shadingCb.isSelected) getShade(t.color, angleCos).rgb else t.color.rgb)
                                        zBuffer[zIndex] = depth
                                    }
                                }
                            }
                        }

                    }
                    g.drawImage(SwingFXUtils.toFXImage(img, null), 0.0, 0.0)
                }

                for (i in 0 until triangles.size) {
                    g.stroke = javafx.scene.paint.Color.GRAY
                    val t = triangles[i]
                    val v1 = transform.transform(t.v1)
                    val v2 = transform.transform(t.v2)
                    val v3 = transform.transform(t.v3)

                    if (edgesCb.isSelected) {
                        g.beginPath()
                        g.moveTo(v1.x + offsetX, v1.y + offsetY)
                        g.lineTo(v2.x + offsetX, v2.y + offsetY)
                        g.lineTo(v3.x + offsetX, v3.y + offsetY)
                        g.closePath()
                        g.stroke()
                    }

                    if (verticesCb.isSelected) {
                        g.fill = javafx.scene.paint.Color.WHITE
                        g.fillRect(v1.x + offsetX, v1.y + offsetY, 1.0, 1.0)
                        g.fillRect(v2.x + offsetX, v2.y + offsetY, 1.0, 1.0)
                        g.fillRect(v3.x + offsetX, v3.y + offsetY, 1.0, 1.0)
                    }
                }
            }
        }.start()

        onPopulate()

    }

    @FXML
    private fun exportImage() {
        if (triangles.isEmpty()) {
            return
        }

        val selectedItem = listView.selectionModel.selectedItem ?: return

        val g = canvas.graphicsContext2D
        g.font = Font.font("Calibri", 12.0)

        val offsetX = canvas.width / 2
        val offsetY = canvas.height / 2

        g.clearRect(0.0, 0.0, canvas.width, canvas.height)

        val yaw = Math.toRadians(yawSlider.value)

        val yawTransform = Matrix4(doubleArrayOf(
                Math.cos(yaw), 0.0, -Math.sin(yaw), 0.0,
                0.0, 1.0, 0.0, 0.0,
                Math.sin(yaw), 0.0, Math.cos(yaw), 0.0,
                0.0, 0.0, 0.0, 1.0))

        val pitch = Math.toRadians(pitchSlider.value)

        val pitchTransform = Matrix4(doubleArrayOf(
                1.0, 0.0, 0.0, 0.0,
                0.0, Math.cos(pitch), Math.sin(pitch), 0.0,
                0.0, -Math.sin(pitch), Math.cos(pitch), 0.0,
                0.0, 0.0, 0.0, 1.0))

        val roll = Math.toRadians(rollSlider.value)
        val rollTransform = Matrix4(doubleArrayOf(
                Math.cos(roll), -Math.sin(roll), 0.0, 0.0,
                Math.sin(roll), Math.cos(roll), 0.0, 0.0,
                0.0, 0.0, 1.0, 0.0,
                0.0, 0.0, 0.0, 1.0))

        val zoom = scaleSlider.value

        val panOutTransform = Matrix4(doubleArrayOf(
                zoom, 0.0, 0.0, 0.0,
                0.0, zoom, 0.0, 0.0,
                0.0, 0.0, zoom, 0.0,
                0.0, 0.0, 0.0, 1.0))

        val translate = Matrix4(doubleArrayOf(
                1.0, 0.0, 0.0, 0.0,
                0.0, 1.0, 0.0, 0.0,
                0.0, 0.0, 1.0, 0.0,
                translateXSlider.value, translateYSlider.value, translateZSlider.value, 1.0
        ))

        val transform = yawTransform.multiply(pitchTransform).multiply(rollTransform).multiply(panOutTransform).multiply(translate)

        if (fillCb.isSelected) {
            val img = BufferedImage(canvas.width.toInt(), canvas.height.toInt(), BufferedImage.TYPE_INT_ARGB)

            val zBuffer = DoubleArray(img.width * img.height)
            // initialize array with extremely far away depths
            for (q in zBuffer.indices) {
                zBuffer[q] = java.lang.Double.NEGATIVE_INFINITY
            }

            for (t in triangles) {
                val v1 = transform.transform(t.v1)
                val v2 = transform.transform(t.v2)
                val v3 = transform.transform(t.v3)

                val ab = Vertex(v2.x - v1.x, v2.y - v1.y, v2.z - v1.z, v2.w - v1.w)
                val ac = Vertex(v3.x - v1.x, v3.y - v1.y, v3.z - v1.z, v3.w - v1.w)
                val norm = Vertex(
                        ab.y * ac.z - ab.z * ac.y,
                        ab.z * ac.x - ab.x * ac.z,
                        ab.x * ac.y - ab.y * ac.x,
                        1.0
                )
                val normalLength = Math.sqrt(norm.x * norm.x + norm.y * norm.y + norm.z * norm.z)
                norm.x /= normalLength
                norm.y /= normalLength
                norm.z /= normalLength

                val angleCos = Math.abs(norm.z)

                // translate
                v1.x += offsetX
                v1.y += offsetY
                v2.x += offsetX
                v2.y += offsetY
                v3.x += offsetX
                v3.y += offsetY

                // compute rectangular bounds for triangle
                val minX = Math.max(0.0, Math.ceil(Math.min(v1.x, Math.min(v2.x, v3.x)))).toInt()
                val maxX = Math.min((img.width - 1).toDouble(),
                        Math.floor(Math.max(v1.x, Math.max(v2.x, v3.x)))).toInt()
                val minY = Math.max(0.0, Math.ceil(Math.min(v1.y, Math.min(v2.y, v3.y)))).toInt()
                val maxY = Math.min((img.height - 1).toDouble(),
                        Math.floor(Math.max(v1.y, Math.max(v2.y, v3.y)))).toInt()

                val triangleArea = (v1.y - v3.y) * (v2.x - v3.x) + (v2.y - v3.y) * (v3.x - v1.x)

                for (y in minY..maxY) {
                    for (x in minX..maxX) {
                        val b1 = ((y - v3.y) * (v2.x - v3.x) + (v2.y - v3.y) * (v3.x - x)) / triangleArea
                        val b2 = ((y - v1.y) * (v3.x - v1.x) + (v3.y - v1.y) * (v1.x - x)) / triangleArea
                        val b3 = ((y - v2.y) * (v1.x - v2.x) + (v1.y - v2.y) * (v2.x - x)) / triangleArea
                        if (b1 >= 0 && b1 <= 1 && b2 >= 0 && b2 <= 1 && b3 >= 0 && b3 <= 1) {
                            val depth = b1 * v1.z + b2 * v2.z + b3 * v3.z
                            val zIndex = y * img.width + x
                            if (zBuffer[zIndex] < depth) {
                                img.setRGB(x, y, if (shadingCb.isSelected) getShade(t.color, angleCos).rgb else t.color.rgb)
                                zBuffer[zIndex] = depth
                            }
                        }
                    }
                }

            }

            ImageIO.write(img, "png", File("./${selectedItem.id}.png"))

        }

    }

    @FXML
    private fun exportObj() {
        if (!App.fs.isLoaded) {
            return
        }

        val selectedItem = listView.selectionModel.selectedItem ?: return

        val model = RSModel.decode(CompressionUtils.degzip(App.fs.readFile(RSFileStore.MODEL_FILE_STORE, selectedItem.id)))

        model.rotateY90Clockwise()
        model.rotateY90Clockwise()

        val colorSet = model.colors.toSet()

        PrintWriter(FileWriter(File("${selectedItem.id}.mtl"))).use { writer ->
            for(hsb in colorSet) {
                val rgb = hsbToRGB(hsb.toInt())

                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF

                writer.println("newmtl $rgb")
                writer.println("Kd ${r.toFloat() / 0xFF} ${g.toFloat() / 0xFF} ${b.toFloat() / 0xFF}")
                writer.println()
            }
        }

        PrintWriter(FileWriter(File("${selectedItem.id}.obj"))).use { writer ->
            writer.println("# Created by Scape Editor")
            writer.println("mtllib ${selectedItem.id}.mtl")

            for (i in 0 until model.vertices) {
                writer.println("v ${model.verticesX[i]} ${model.verticesY[i]} ${model.verticesZ[i]}")
            }

            for (i in 0 until model.faces) {
                val rgb = hsbToRGB(model.colors[i].toInt())
                writer.println("usemtl $rgb")
                writer.println("f ${model.facesA[i] + 1} ${model.facesB[i] + 1} ${model.facesC[i] + 1}")
            }
        }

        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.title = "Confirmation"
        alert.headerText = "Would you like to view these files?"

        val result = alert.showAndWait()

        if (result.get() == ButtonType.OK) {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(File("./"))
            } else {
                val alert = Alert(Alert.AlertType.WARNING)
                alert.title = "Warning"
                alert.headerText = "Not supported by your system."
                alert.showAndWait()
            }
        }

    }

    @FXML
    private fun exportAllObj() {
        if (!App.fs.isLoaded) {
            return
        }

        val dir = File("./dump/")

        if (!dir.exists()) {
            dir.mkdirs()
        }

        val task = object: Task<Boolean>() {
            override fun call(): Boolean {
                for (i in 0 until listView.items.size) {
                    val item = listView.items[i]

                    val model = RSModel.decode(CompressionUtils.degzip(App.fs.readFile(RSFileStore.MODEL_FILE_STORE, item.id)))

                    model.rotateY90Clockwise()
                    model.rotateY90Clockwise()

                    val colorSet = model.colors.toSet()

                    PrintWriter(FileWriter(File(dir,"${item.id}.mtl"))).use { writer ->
                        for(hsb in colorSet) {
                            val rgb = hsbToRGB(hsb.toInt())

                            val r = (rgb shr 16) and 0xFF
                            val g = (rgb shr 8) and 0xFF
                            val b = rgb and 0xFF

                            writer.println("newmtl $rgb")
                            writer.println("Kd ${r.toFloat() / 0xFF} ${g.toFloat() / 0xFF} ${b.toFloat() / 0xFF}")
                            writer.println()
                        }
                    }

                    PrintWriter(FileWriter(File(dir,"${item.id}.obj"))).use { writer ->
                        writer.println("# Created by Scape Editor")
                        writer.println("mtllib ${item.id}.mtl")

                        for (i in 0 until model.vertices) {
                            writer.println("v ${model.verticesX[i]} ${model.verticesY[i]} ${model.verticesZ[i]}")
                        }

                        for (i in 0 until model.faces) {
                            val rgb = hsbToRGB(model.colors[i].toInt())
                            writer.println("usemtl $rgb")
                            writer.println("f ${model.facesA[i] + 1} ${model.facesB[i] + 1} ${model.facesC[i] + 1}")
                        }
                    }

                    val progress = (i + 1).toDouble() / listView.items.size * 100
                    updateMessage(String.format("%.2f%s", progress, "%"))
                    updateProgress((i + 1).toDouble(), listView.items.size.toDouble())
                }

                Platform.runLater {
                    val alert = Alert(Alert.AlertType.CONFIRMATION)
                    alert.title = "Confirmation"
                    alert.headerText = "Would you like to view these files?"

                    val result = alert.showAndWait()

                    if (result.get() == ButtonType.OK) {
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(dir)
                        } else {
                            val alert = Alert(Alert.AlertType.WARNING)
                            alert.title = "Warning"
                            alert.headerText = "Not supported by your system."
                            alert.showAndWait()
                        }
                    }
                }

                return true
            }

        }

        runTask("Model Export task", task)

    }

    override fun onPopulate() {
        val fs = App.fs
        val store = fs.getStore(RSFileStore.MODEL_FILE_STORE) ?: return

        data.clear()
        for (i in 0 until store.fileCount) {
            data.add(ModelWrapper(i.toString(), "", i))
        }
    }

    var mouseX = 0.0
    var mouseY = 0.0
    var zoom = 0.0

    @FXML
    private fun onMousePressed(event: MouseEvent) {
        mouseX = event.x
        mouseY = event.y
    }

    @FXML
    private fun onScroll(event: ScrollEvent) {
        var zoomFactor = 0.15

        if (event.deltaY > 0) {
            zoom += zoomFactor
        } else {
            zoom -= zoomFactor
        }

        if (zoom > 5.0) {
            zoom = 5.0
        }

        scaleSlider.value = zoom
    }

    @FXML
    private fun onMouseDragged(event: MouseEvent) {
        if (event.isPrimaryButtonDown) {
            if (event.isControlDown) {
                translateXSlider.value = event.x - mouseX
                translateYSlider.value = event.y - mouseY
            } else {
                yawSlider.value = event.x - mouseX
                pitchSlider.value = mouseY - event.y
            }
        } else if (event.isSecondaryButtonDown) {
            val dz = mouseY - event.y

            if (dz > 0) {
                zoom += 0.05
            } else {
                zoom -= 0.05
            }
            scaleSlider.value = zoom
        }
    }

    @FXML
    private fun importFromDirectory() {
        val chooser = DirectoryChooser()
        chooser.initialDirectory = File("./")
        chooser.title = "Select directory containing models"
        val dir = chooser.showDialog(App.mainStage) ?: return
        val files = dir.listFiles() ?: return

        data.clear()

        val sortedArray = arrayOfNulls<File>(files.size)
        val set = mutableSetOf<File>()

        for (file in files) {
            if (file.name.indexOf(".") != -1) {
                try {
                    var index = file.name.substring(0, file.name.indexOf(".")).toInt()

                    sortedArray[index] = file
                } catch (ex: Exception) {
                    set.add(file)
                }
            }
        }

        val sortedSet = mutableSetOf<File>()

        for (file in sortedArray) {
            file ?: continue
            sortedSet.add(file)
        }

        for (file in set) {
            sortedSet.add(file)
        }

        for (file in sortedSet) {
            data.add(ModelWrapper(file.name, file.path))
        }
    }

    @FXML
    private fun importFiles() {
        val chooser = FileChooser()
        chooser.initialDirectory = File("./")
        chooser.title = "Select models either gzipped or dat"
        val extFilter = FileChooser.ExtensionFilter("Gzipped or Dat", "*.gz", "*.dat")
        chooser.extensionFilters.add(extFilter)
        val selectedFiles = chooser.showOpenMultipleDialog(App.mainStage) ?: return

        for (file in selectedFiles) {
            data.add(ModelWrapper(file.name, file.path))
        }
    }

    override fun onClear() {
        data.clear()
        triangles.clear()
    }

    companion object {
        fun getShade(color: Color, shade: Double): Color {
            val redLinear = Math.pow(color.red.toDouble(), 2.4) * shade
            val greenLinear = Math.pow(color.green.toDouble(), 2.4) * shade
            val blueLinear = Math.pow(color.blue.toDouble(), 2.4) * shade

            val red = Math.pow(redLinear, 1 / 2.4).toInt()
            val green = Math.pow(greenLinear, 1 / 2.4).toInt()
            val blue = Math.pow(blueLinear, 1 / 2.4).toInt()

            return Color(red, green, blue)
        }

        fun hsbToRGB(hsb: Int): Int {
            val h = hsb shr 10 and 0x3f
            val s = hsb shr 7 and 0x07
            val b = hsb and 0x7f
            return Color.HSBtoRGB(h.toFloat() / 63, s.toFloat() / 7, b.toFloat() / 127)
        }
    }

    class ModelWrapper(val name: String, val path: String = "", val id: Int = -1) {
        override fun toString(): String {
            return name.toString()
        }
    }

}