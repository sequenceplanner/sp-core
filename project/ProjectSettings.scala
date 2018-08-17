import sbt.{Developer, url}

object ProjectSettings {
  val developers = List(
    Developer(
      id = "kristoferb",
      name = "kristofer Bengtsson",
      email = "kristofer@sekvensa.se",
      url   = url("https://github.com/kristoferB")
    ),
    Developer(
      id = "m-dahl",
      name = "Martin Dahl",
      email = "martin.dahl@chalmers.se",
      url   = url("https://github.com/m-dahl")
    ),
    Developer(
      id = "patrikm",
      name = "Patrik Bergagård",
      email = "Patrik.Bergagard@alten.se",
      url   = url("https://github.com/patrikm")
    ),
    Developer(
      id = "ashfaqfarooqui",
      name = "Ashfaq Farooqui",
      email = "ashfaqf@chalmers.se",
      url   = url("https://github.com/ashfaqfarooqui")
    ),
    Developer(
      id = "edvardlindelof",
      name = "Edvard Lindelöf",
      email = "edvardlindelof@gmail.com",
      url   = url("https://github.com/edvardlindelof")
    ),
    Developer(
      id = "marptt",
      name = "Martin Petterson",
      email = "laxenmeflaxen@gmail.com",
      url   = url("https://github.com/marptt")
    ),
    Developer(
      id = "ellenkorsberg",
      name = "Ellen Korsberg",
      email = "korsberg.ellen@gmail.com",
      url   = url("https://github.com/ellenkorsberg")
    ),
    Developer(
      id    = "aleastChs",
      name  = "Alexander Åstrand",
      email = "aleast@student.chalmers.se",
      url   = url("https://github.com/aleastChs")
    )
  )
}
