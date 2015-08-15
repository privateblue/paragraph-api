import org.neo4j.graphdb.Label
import org.neo4j.graphdb.RelationshipType

package object neo {
    def createLabel(n: String) = new Label {
        def name = n
    }

    def createRelType(n: String) = new RelationshipType {
        def name = n
    }

    def createProperty[T: NeoValueWrites](k: String, v: T) = NonUniqueProperty(k, v)

    def createOptionalProperty[T: NeoValueWrites](k: String, v: Option[T]) =
        v.map(createProperty(k, _)).getOrElse(EmptyProperty)
}
