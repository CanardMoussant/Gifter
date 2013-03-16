package controllers

import models.user._
import models.gift._
import anorm._
import play.api.mvc._
import play.api._
import play.api.data._
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import play.api.db._
import play.api.Play.current
import play.api.db.evolutions.Evolutions
import org.joda.time.DateTime

trait Secured {

  /**
   * Retrieve the connected user email.
   */
  private def userId(request: RequestHeader) = request.session.get("userId")

  /**
   * Redirect to login if the user in not authorized.
   */
  def onUnauthorized() = Results.Redirect(routes.Application.index)
  // the one below is just to comply to the signature asked by Security.Authenticated
  def onUnauthorized(request: RequestHeader): Result = onUnauthorized()

  /**
   * Action for authenticated users. The bodyParser argument is to be able to specify, for example, a json parser.
   */
  def IsAuthenticated[A](bodyParser: BodyParser[A])(f: => User => Request[A] => Result) = Security.Authenticated(userId, onUnauthorized) { userId =>
    User.findById(userId.toLong) match {
      case Some(user) => Action(bodyParser)(request => f(user)(request))
      case _ => Action(bodyParser)(request => onUnauthorized())
    }
  }
  def IsAuthenticated(f: => User => Request[AnyContent] => Result): play.api.mvc.EssentialAction =
    IsAuthenticated(BodyParsers.parse.anyContent)(f)

  /**
   * Check if the connected user is the creator of an event.
   */
  def IsCreatorOf(eventid: Long)(f: => User => Request[AnyContent] => Result) = IsAuthenticated { user => request =>
    if(Event.isCreator(eventid, user.id.get)) {
      f(user)(request)
    } else {
      Results.Forbidden
    }
  }
  
  def IsOwnerOf(eventid: Long, userid: Long) = {
    Participant.findByEventIdAndByUserId(eventid, userid) match {
      case Some(p) if p.role == Participant.Role.Owner => true
      case _ => false
    }
  }

  /**
   * Check if the connected user is a owner of this event.
   */
  def IsOwnerOf(eventid: Long)(f: => User => Request[AnyContent] => Result): play.api.mvc.EssentialAction = IsAuthenticated { user => request =>
    if(IsOwnerOf(eventid, user.id.get)) {
      f(user)(request)
    }
    else {
      Results.Forbidden
    }
  }
}

object UserApplication extends Controller with Secured {

  def index = IsAuthenticated { user => implicit request =>
    Ok(views.html.userHome(user))
  }
  
  /** temporary method to reset the bootstrap data */
  def reset = IsAuthenticated { user => implicit request =>
    Logger.info("!!! resetting data !!!")
    DB.withConnection { implicit connection =>
      val api = current.plugin[DBPlugin].map(_.api).getOrElse(throw new Exception(
        "there should be a database plugin registered at this point but looks like it's not available, so evolution won't work. Please make sure you register a db plugin properly"))
      api.datasources.foreach {
        case (ds, db) => {
          val database = Evolutions.databaseEvolutions(api, db)
          SQL(database(0).sql_down).executeUpdate()
          SQL(database(0).sql_up).executeUpdate()
        }
      }
    }

    bootstrap.InitialData.addTestData()
    Redirect(routes.UserApplication.index)
  }

  val profileForm = Form[String](
   "name" -> nonEmptyText
  )

  def profile = IsAuthenticated{ user => implicit request =>
    Ok(views.html.profile(user, profileForm.fill(user.name)))
  }

  def postProfile() = IsAuthenticated { user => implicit request =>
    profileForm.bindFromRequest.fold(
      errors => {
        println(errors)
        BadRequest(views.html.profile(user, errors))
      },
      name => {
        val userUpdated = User.update(user.id.get, User(name=name))
        val userInDb = User.findById(user.id.get).get
        Ok(views.html.userHome(userInDb))
      }
    )
  }  

}
