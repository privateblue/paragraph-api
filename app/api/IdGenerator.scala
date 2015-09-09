package api

import neo.Query

import scalaz._
import Scalaz._

object IdGenerator {
    def generateUUID = java.util.UUID.randomUUID

    def encode(uuid: java.util.UUID) = {
        val msb = uuid.getMostSignificantBits
        val lsb = uuid.getLeastSignificantBits
        val bytes = java.nio.ByteBuffer.allocate(16).putLong(msb).putLong(lsb).array()
        val base64 = java.util.Base64.getUrlEncoder.encodeToString(bytes)
        base64.substring(0, 22)
    }

    def key: Query.Exec[String] = encode(generateUUID).point[Query.Exec]
}
