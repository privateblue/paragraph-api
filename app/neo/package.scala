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
                        (rest, s"$q$cur", params)

                    case (((prop@PropertyValue.Single(_, _))::rest, q, params), cur) =>
                        val (expr, map) = toStringAndMap(prop, params.size)
                        (rest, s"$q$expr$cur", params ++ map)

                    case (((prop@PropertyValue.Multi(_))::rest, q, params), cur) =>
                        val (expr, map) = toStringAndMap(prop, params.size)
                        (rest, s"$q{$expr}$cur", params ++ map)

                    case ((other::rest, q, params), cur) =>
                        (rest, s"$q$other$cur", params)
                }
            val sanitized = query
                .replaceAll("\n", "")
                .replaceAll(" +", " ")
            Query(sanitized, parameters.asJava)
        }

        private def toStringAndMap(prop: PropertyValue, base: Int): (String, Map[String, java.lang.Object]) = {
            val zero = (List.empty[String], Map.empty[String, java.lang.Object])
            val (exprs, map) = PropertyValue.toList(prop).foldLeft(zero) {
                case ((expr, map), single) => (s"${single.name}:{p${base + map.size}}" :: expr, map + (s"p${base + map.size}" -> single.value))
            }
            (exprs.reverse.mkString(", "), map)
        }
    }
}
