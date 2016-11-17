package controllers

import java.util.UUID
import javax.inject.Inject

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{PasswordHasherRegistry, PasswordInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import forms.ResetPasswordForm
import models.services.AuthTokenService
import models.services.user.UserService
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import utils.auth.DefaultEnv

import scala.concurrent.Future

/**
 * The `Reset Password` controller.
 *
 * @param messagesApi            The Play messages API.
 * @param silhouette             The Silhouette stack.
 * @param userService            The user service implementation.
 * @param authInfoRepository     The auth info repository.
 * @param passwordHasherRegistry The password hasher registry.
 * @param authTokenService       The auth token service implementation.
 * @param webJarAssets           The WebJar assets locator.
 */
class ResetPasswordController @Inject() (
  val messagesApi: MessagesApi,
  silhouette: Silhouette[DefaultEnv],
  userService: UserService,
  authInfoRepository: AuthInfoRepository,
  passwordHasherRegistry: PasswordHasherRegistry,
  authTokenService: AuthTokenService,
  implicit val webJarAssets: WebJarAssets)
  extends Controller with I18nSupport {

  /**
   * Views the `Reset Password` page.
   *
   * @param token The token to identify a user.
   * @return The result to display.
   */
  def view(token: UUID) = silhouette.UnsecuredAction.async { implicit request =>
    authTokenService.validate(token).map {
      case Some(authToken) => Ok(views.html.resetPassword(ResetPasswordForm.form, token))
      case None => Redirect(routes.AuthenticationController.signInForm()).flashing("error" -> Messages("invalid.reset.link"))
    }
  }

  /**
   * Resets the password.
   *
   * @param token The token to identify a user.
   * @return The result to display.
   */
  def submit(token: UUID) = silhouette.UnsecuredAction.async { implicit request =>
    authTokenService.validate(token).flatMap {
      case Some(authToken) =>
        ResetPasswordForm.form.bindFromRequest.fold(
          form => Future.successful(BadRequest(views.html.resetPassword(form, token))),
          password => userService.retrieve(authToken.userID).flatMap {
            case Some(user) if user.profiles.find(_.providerID == CredentialsProvider.ID).isDefined =>
              val passwordInfo = passwordHasherRegistry.current.hash(password)
              val loginInfo = user.profiles.find(_.providerID == CredentialsProvider.ID).get
              authInfoRepository.update[PasswordInfo](loginInfo, passwordInfo).map { _ =>
                Redirect(routes.AuthenticationController.signInForm()).flashing("success" -> Messages("password.reset"))
              }
            case _ => Future.successful(Redirect(routes.AuthenticationController.signInForm()).flashing("error" -> Messages("invalid.reset.link")))
          }
        )
      case None => Future.successful(Redirect(routes.AuthenticationController.signInForm()).flashing("error" -> Messages("invalid.reset.link")))
    }
  }
}