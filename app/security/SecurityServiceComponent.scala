package security

import repositories.UsersRepositoryComponent
import services.SessionProvider
import models.database.UserToInsert
import scala.slick.session.Session
import scalaz._
import Scalaz._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait SecurityServiceComponent {
  val securityService: SecurityService

  trait SecurityService {
    /*
     * @return new remember me token for user if success
     */
    def signInUser(username: String, password: String): Future[ValidationNel[String, (String, AuthenticatedUser)]]
  }
}

trait SecurityServiceComponentImpl extends SecurityServiceComponent {
  this: UsersRepositoryComponent with SessionProvider =>

  val securityService = new SecurityServiceImpl
  val tokenProvider: TokenProvider
  val authenticationManager: AuthenticationManager

  class SecurityServiceImpl extends SecurityService {
    def signInUser(username: String, password: String) = {
      for {
        rawResult <- authenticationManager.authenticate(username, password)
        authenticationResult = rawResult.cata(
          some = _.successNel,
          none = "Invalid username or password".failNel
        )
      } yield {
        for {
          userInfo <- authenticationResult
          authenticatedUser = getOrCreateAuthenticatedUser(userInfo)
          token = issueRememberMeTokenTo(authenticatedUser.id)
        } yield (token, authenticatedUser)
      }
    }

    private def issueRememberMeTokenTo(userId: Int) = {
      val token = tokenProvider.generateToken
      withSession { implicit session: Session =>
        usersRepository.updateRememberToken(userId, token)
      }
      token
    }

    private def getOrCreateAuthenticatedUser(userInfo: UserInfo) = withSession { implicit session: Session =>
      def createUser = {
        val userToInsert = UserToInsert(userInfo.username, false, userInfo.firstName, userInfo.lastName)
        usersRepository.insert(userToInsert)
      }

      usersRepository.getByUsername(userInfo.username).cata(
        some = user => AuthenticatedUser(user.id.get, user.username),
        none = AuthenticatedUser(createUser, userInfo.username)
      )
    }
  }
}

private[security] case class UserInfo(username: String, firstName: Option[String], lastName: Option[String])

