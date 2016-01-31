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

                    case ((PropertyValue.Empty::rest, q, params), cur) =>
                        (rest, s"$q[[[empty]]]$cur", params)

                    case ((PropertyValue.Single(name, value)::rest, q, params), cur) =>
                        val pName = s"p${params.size}"
                        val expr = s"$name:{$pName}"
                        (rest, s"$q$expr$cur", params + (pName -> value))

                    case ((PropertyValue.Multi(values)::rest, q, params), cur) =>
                        val base = params.size
                        val nonEmpties = values.collect {
                            case s @ PropertyValue.Single(_, _) => s
                        }
                        val expr = nonEmpties.zipWithIndex.map(e => s"${e._1.name}:{p${base + e._2}}" ).mkString(",")
                        val map = nonEmpties.zipWithIndex.map(e => (s"p${base + e._2}", e._1.value)).toMap
                        (rest, s"$q$expr$cur", params ++ map)

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
