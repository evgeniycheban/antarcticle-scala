package controllers

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.AfterExample
import util.FakeAuthentication
import play.api.test._
import play.api.test.Helpers._
import scalaz._
import Scalaz._
import security.SecurityServiceComponent
import scala.concurrent.Future
import conf.Constants._
import security.AuthenticatedUser
import play.api.mvc.Cookie
import scala.Some
import org.mockito.Matchers

class AuthenticationControllerSpec extends Specification with Mockito with AfterExample {

  object controller extends AuthenticationController
                     with SecurityServiceComponent
                     with FakeAuthentication {
    override val securityService = mock[SecurityService]
    override val usersRepository = mock[UsersRepository]
  }

  import controller._

  def after = {
    org.mockito.Mockito.reset(securityService)
    org.mockito.Mockito.reset(usersRepository)
  }

  "show login page" should {

    "render login page" in {
      val page = controller.showLoginPage(FakeRequest())

      status(page) must equalTo(200)
      contentType(page) must beSome("text/html")
    }
  }

  "login form submit" should {

    val username = "username"
    val password = "password"
    val rememberMeToken = "token"
    val user = mock[AuthenticatedUser]
    val request = FakeRequest("POST","/")
      .withFormUrlEncodedBody(("login", username),("password", password))

    "perform authentication with valid credentials" in {
      securityService.signInUser(username, password) returns Future.successful((rememberMeToken, user).successNel)

      val page = controller.login(request)

      status(page) must equalTo(200)
      contentType(page) must beSome("text/plain")
      cookies(page).get(rememberMeCookie).get must equalTo(
        Cookie(rememberMeCookie, rememberMeToken, Some(rememberMeExpirationTime), httpOnly = true)
      )
    }

    "return an error if credentials are invalid" in {
      securityService.signInUser(username, password) returns Future.successful("Invalid credentials".failureNel)

      val page = controller.login(request)

      status(page) must equalTo(400)
      contentType(page) must beSome("text/html")
      cookies(page).get(rememberMeCookie) must beNone
    }

    "trim username" in {
      val notTrimmedUsername: String = " " + username + " "
      val requestForTrim = FakeRequest("POST","/")
        .withFormUrlEncodedBody(("login", notTrimmedUsername),("password", password))
      securityService.signInUser(Matchers.eq(username), Matchers.eq(password)) returns
        Future.successful((rememberMeToken, user).successNel)

      val page = controller.login(requestForTrim)
      status(page) must equalTo(200)
      there was one(securityService).signInUser(Matchers.eq(username), Matchers.eq(password))
      there was no(securityService).signInUser(Matchers.eq(notTrimmedUsername), Matchers.eq(password))
    }

  }

  "logout action" should {

    "terminate current session" in {
      val request = FakeRequest().withCookies(Cookie(rememberMeCookie, "token"))
      val page = controller.logout(request)

      status(page) must equalTo(303)
      // cookie is destroyed by setting negative maxAge, so browser should drop it
      cookies(page).get(rememberMeCookie).get.maxAge.get must beLessThan(0)
    }
  }
}
