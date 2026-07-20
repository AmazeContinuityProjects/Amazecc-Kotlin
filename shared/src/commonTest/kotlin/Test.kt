import qrcode.QRCode

fun main() {
    val qr = QRCode("Test").encode()
    println(qr)
}
