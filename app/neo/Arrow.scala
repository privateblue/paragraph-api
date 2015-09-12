package neo

case class Arrow(val name: String) extends org.neo4j.graphdb.RelationshipType

object Arrow {
    def apply(rel: org.neo4j.graphdb.RelationshipType): Arrow = apply(rel.name)
}
