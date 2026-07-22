import com.amazecc.app.shared.utils.QRCodeGenerator

fun main() {
    val qr = QRCodeGenerator.generate("Test")
    println(qr)
}
