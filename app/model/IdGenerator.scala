package model

object IdGenerator {
    def generate = java.util.UUID.randomUUID.toString
}
