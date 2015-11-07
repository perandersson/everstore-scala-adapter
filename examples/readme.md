## Examples

### akka-spray-example

This example shows us how to use everstore in combination with **Scala** and **Spray**.

### console-examples

A couple of console examples on how to use the everstore adapter without (almost) any third-party libraries, such as **spray**. 

### How does PersistenceActor[T] work?

You start by extending an actor with `everstore.adapter.akka.PersistenceActor`. This enables us to open transactions, 
read events and committing events to the event store. All actions to the data storage is asynchronous and thus returns `Future[T]`. 

Let's say for example that we want to manage the concept **Users** using Everstore:

```scala
// Domain classes
case class Email(address: String)
case class User(name: String, email: Email)

// Events saved on server
case class UserCreated(name: String)
case class UserEmailUpdated(address: String)

// Commands received by this actor to do stuff
case class CreateUser(name: String)
case class UpdateEmail(email: Email)
case object GetUser

class UserActor(id: String) extends PersistenceActor[User] {
 import PersistenceActor._
 
 // Where to read and write our events to
 override val journalName = s"/users/$id"

 override def receiveCommand: Receive = {
   case CreateUser(name) =>
     val senderRef = sender()
     openTransaction().foreach(implicit transaction => {
       persist(UserCreated(name))
       commit() onComplete {
        case Success(data) =>
          senderRef ! Option(User(name))
        case Failed(f) =>
          senderRef ! None
       }
     })
   case UpdateEmail(email) =>
     val senderRef = sender()
     openTransaction().foreach(implicit transaction => {
       persist(UserEmailUpdated(email.address))
       commit() onComplete {
        case Success(data) =>
          senderRef ! Option(email)
        case Failed(f) =>
          senderRef ! None
       }
     })
   case GetUser =>
    self forward GetState
 }
 
 // Process one event at a time
 override def processEvent(state: State, event: Any): State = event match {
    case UserCreated(name) =>
      User(name)
    case UserEmailUpdated(address) =>
      state.copy(email = Email(address))
 }
}
```

It's important to save the original sender before doing anything. The reason for it is because the sender reference is 
lost to us when the futures onComplete method is called. After the transaction is successfully opened then it's possible 
to persist events to it. The events won't be saved until we commit the transaction. If the commit is successful then we 
return something "REST-y" (create a user, then return the newly created user).

Note that the actors internal state is not manipulated or stored in the actor yet. Internal states are only read 
when "getting" the state with a `GetState` command. I've also added a `GetUser` command that fulfills two things:

1. Make the core more readable
2. Ensure that we don't send a command to read the wrong actor's internal state.

When `PersistenceActor[T]` receives a `GetState` command, it loads all new events from the server and then call 
the `processEvent` method for each new event. This is done asynchronously. The state returned by the `processEvent` method
is then stored internally. 

This is how you might work with this actor (note that some parts has been excluded in the example to make it more readable):

```scala
case class UserCandidate(name: String)

class RestRouting extends HttpService with Actor {
  def receive = runRoute(
    pathPrefix("api") {
      respondWithMediaType(`application/json`) {
        path("users") {
          path(IntNumber) { userId =>
            get {
              val userActor = getActorResponsibleForUser(userId)
              val futureUser = (userActor ? GetUser).mapTo[Option[User]]
              complete(futureUser)
            } ~ 
            path("email") {
              get {
                val userActor = getActorResponsibleForUser(userId)
                val futureUser = (userActor ? GetUser).mapTo[Option[User]]
                val possibleEmail = futureUser.map({
                  case Some(user) => user.email
                  case _ => None
                })
                complete(possibleEmail)
              }
            }
          } ~
          post {
            entity(as[UserCandidate]) { user =>
              val userId = createUUIDAsString()
              val userActor = getActorResponsibleForUser(userId)
              val result = (userActor ? CreateUser(user.name)).mapTo[Option[User]]
              complete(result)
            }
          }
        }
      }
    }
  )
}
```