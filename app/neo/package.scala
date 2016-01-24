import scala.collection.JavaConverters._

package object neo {
    implicit class QueryHelper(val sc: StringContext) extends AnyVal {
        def neo(args: Any*): Query = {
            val (_, query, parameters, _) =
                sc.parts.tail.foldLeft((args.toList, sc.parts.head, Map.empty[String, AnyRef], 0)) {
                    case ((Label(name)::rest, q, params, c), cur) =>
                        (rest, s"$q$name$cur", params, c)

                    case ((Arrow(name)::rest, q, params, c), cur) =>
                        (rest, s"$q$name$cur", params, c)

                    case ((Property(name, identifier)::rest, q, params, c), cur) =>
                        val id = identifier.map(i => s"$i.").getOrElse("")
                        (rest, s"$q$id$name$cur", params, c)

                    case ((PropertyValue.NonEmpty(identifier, name, value)::rest, q, params, c), cur) =>
                        val pName = s"p$c"
                        val expr = identifier.map(i => s"$i.$name={$pName}").getOrElse(s"$name:{$pName}")
                        (rest, s"$q$expr$cur", params + (pName -> value), c + 1)

                    case ((PropertyValue.Empty::rest, q, params, c), cur) =>
                        (rest, s"$q[[[empty]]]$cur", params, c)

                    case ((other::rest, q, params, c), cur) =>
                        (rest, s"$q$other$cur", params, c)
                }
            val sanitized = query
                .replaceAll("\n", "")
                .replaceAll(" +", " ")
                .replaceAll(",? *\\[\\[\\[empty\\]\\]\\] *,?", "")
            Query(sanitized, parameters.asJava)
        }
    }
}
