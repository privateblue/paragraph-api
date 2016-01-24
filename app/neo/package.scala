import scala.collection.JavaConverters._

package object neo {
    implicit class QueryHelper(val sc: StringContext) extends AnyVal {
        def neo(args: Any*): Query = {
            val (_, query, parameters) =
                sc.parts.tail.foldLeft((args.toList, sc.parts.head, Map.empty[String, java.lang.Object])) {
                    case ((Label(name)::rest, q, params), cur) =>
                        (rest, s"$q$name$cur", params)

                    case ((Arrow(name)::rest, q, params), cur) =>
                        (rest, s"$q$name$cur", params)

                    case ((Property(name, identifier)::rest, q, params), cur) =>
                        val id = identifier.map(i => s"$i.").getOrElse("")
                        (rest, s"$q$id$name$cur", params)

                    case ((PropertyValue.NonEmpty(identifier, name, value)::rest, q, params), cur) =>
                        val pName = s"p${params.size}"
                        val expr = identifier.map(i => s"$i.$name={$pName}").getOrElse(s"$name:{$pName}")
                        (rest, s"$q$expr$cur", params + (pName -> value))

                    case ((PropertyValue.Empty::rest, q, params), cur) =>
                        (rest, s"$q[[[empty]]]$cur", params)

                    case ((other::rest, q, params), cur) =>
                        (rest, s"$q$other$cur", params)
                }
            val sanitized = query
                .replaceAll("\n", "")
                .replaceAll(" +", " ")
                .replaceAll(",? *\\[\\[\\[empty\\]\\]\\] *,?", "")
            Query(sanitized, parameters.asJava)
        }
    }
}
