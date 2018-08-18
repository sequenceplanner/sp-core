package sp

object Text {
  val IndexMessage = "sp-core API."
  def BadUUID(s: String) = s"$s is not a valid java.util.UUID."
  val NotFound = "The requested resource could not be found."

  val AskRequiresTopic = "The ask endpoint requires a topic. Example: GET /api/ask/my-topic."

  val NodeJoinedCluster = "SPCore node has joined the cluster"
  val NodeLeftCluster = "SPCore node has been removed from the cluster"

  object ModelMakerText {
    def CannotCreateModel(id: String) = s"Model $id already exists. It can not be created."
    def CannotDeleteModel(id: String) = s"Model $id does not exist. It can not be deleted."
  }
}