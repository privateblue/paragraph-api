package object neo {
    implicit class QueryHelper(val sc: StringContext) extends AnyVal {
        def neo(args: Any*): Query = {
            val emptyQuery = Query(sc.parts.head, Map.empty[String, AnyRef])
            val (_, Query(query, parameters), _) =
                sc.parts.tail.foldLeft((args.toList, emptyQuery, 0)) {
                    case ((Label(name)::rest, Query(q, params), c), cur) =>
                        (rest, Query(s"$q$name$cur", params), c)

                    case ((Arrow(name)::rest, Query(q, params), c), cur) =>
                        (rest, Query(s"$q$name$cur", params), c)

                    case ((Property(name)::rest, Query(q, params), c), cur) =>
                        (rest, Query(s"$q$name$cur", params), c)

                    case ((PropertyValue.NonEmpty(name, NeoValue(underlying))::rest, Query(q, params), c), cur) =>
                        val pName = s"p$c"
                        (rest, Query(s"$q$name:{$pName}$cur", params + (pName -> underlying)), c + 1)

                    case ((PropertyValue.Empty::rest, Query(q, params), c), cur) =>
                        (rest, Query(s"$q$cur", params), c)

                    case ((NeoValue(underlying)::rest, Query(q, params), c), cur) =>
                        val pName = s"p$c"
                        (rest, Query(s"$q{$pName}$cur", params + (pName -> underlying)), c + 1)

                    case ((other::rest, Query(q, params), c), cur) =>
                        (rest, Query(s"$q$other$cur", params), c)
                }
            val sanitized = query
                .replaceAll("\n", "")
                .replaceAll(" +", " ")
            Query(sanitized, parameters)
        }
    }
}
