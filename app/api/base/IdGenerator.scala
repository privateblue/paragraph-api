package api.base

import neo.Query

import com.fasterxml.uuid.Generators
import com.fasterxml.uuid.EthernetAddress

import scalaz._
import Scalaz._

object IdGenerator {
    val generator = Generators.timeBasedGenerator(EthernetAddress.fromInterface)

    def generateUUID = generator.generate

    def encode(uuid: java.util.UUID) = {
        val msb = uuid.getMostSignificantBits
        val lsb = uuid.getLeastSignificantBits
        val bytes = java.nio.ByteBuffer.allocate(16).putLong(msb).putLong(lsb).array()
        val base64 = java.util.Base64.getUrlEncoder.encodeToString(bytes)
        base64.substring(0, 22)
    }

    def key: String = encode(generateUUID)
}
