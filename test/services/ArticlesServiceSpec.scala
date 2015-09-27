package services

import models.ArticleModels.Language._
import org.specs2.mutable.Specification
import models.database._
import utils.Implicits._
import com.github.nscala_time.time.Imports._
import org.specs2.mock.Mockito
import repositories.{UsersRepositoryComponent, TagsRepositoryComponent, ArticlesRepositoryComponent}
import org.mockito.Matchers
import models.ArticleModels.{Translation, ArticleDetailsModel, Article, ArticleListModel}
import util.{TimeFridge, MockSession}
import models.Page
import org.specs2.specification.BeforeEach
import scala.slick.jdbc.JdbcBackend
import scalaz._
import Scalaz._
import validators.{TagValidator, Validator}
import util.ScalazValidationTestUtils._
import org.specs2.scalaz.ValidationMatchers
import conf.Constants._
import security._
import security.Result._


class ArticlesServiceSpec extends Specification with Mockito with BeforeEach with ValidationMatchers with MockSession {

  object service extends ArticlesServiceComponentImpl
  with ArticlesRepositoryComponent
  with TagsServiceComponent with UsersRepositoryComponent with TagsRepositoryComponent with MockSessionProvider
  with NotificationsServiceComponent {
    override val notificationsService = mock[NotificationsService]
    override val articlesRepository = mock[ArticlesRepository]
    override val tagsService = mock[TagsService]
    override val articleValidator = mock[Validator[Article]]
    override val usersRepository = mock[UsersRepository]
    override val tagsRepository = mock[TagsRepository]
    val tagValidator: TagValidator = mock[TagValidator]
  }

  def anySession = any[JdbcBackend#Session]

  import service._

  def before = {
    org.mockito.Mockito.reset(notificationsService)
    org.mockito.Mockito.reset(tagsService)
    org.mockito.Mockito.reset(articlesRepository)
    org.mockito.Mockito.reset(articleValidator)
    org.mockito.Mockito.reset(session)
    org.mockito.Mockito.reset(usersRepository)
    org.mockito.Mockito.reset(tagsRepository)
  }

  val dbRecord = {
    val article = ArticleRecord(1.some, "", "", DateTime.now, DateTime.now, "", 1, Russian, 1.some, true)
    val user = UserRecord(1.some, "", "", "")
    val tags = List("tag1", "tag2")
    (article, user, tags)
  }

  var articleList = {
    List((dbRecord._1, dbRecord._2, dbRecord._3, 1))
  }

  "get article" should {
    val article = dbRecord.some

    "return model" in {
      tagsRepository.getByName(any)(Matchers.eq(session)) returns None
      articlesRepository.get(anyInt)(Matchers.eq(session)) returns article
      articlesRepository.getTranslations(anyInt)(Matchers.eq(session)) returns List()

      val model = articlesService.get(1)

      model.map(_.id) must beSome(1)
    }

    "have correct author" in {
      articlesRepository.get(anyInt)(Matchers.eq(session)) returns article
      articlesRepository.getTranslations(anyInt)(Matchers.eq(session)) returns List()

      val model = articlesService.get(1)

      model.map(_.author.id) must beSome(1)
    }

    "have correct tags" in {
      articlesRepository.get(anyInt)(Matchers.eq(session)) returns article
      articlesRepository.getTranslations(anyInt)(Matchers.eq(session)) returns List()

      val model = articlesService.get(1)

      model.map(_.tags).get must containTheSameElementsAs(dbRecord._3)
    }

    "have correct list of translations" in {
      val translations = List((1, Russian.toString), (2, English.toString))
      articlesRepository.get(anyInt)(Matchers.eq(session)) returns article
      articlesRepository.getTranslations(anyInt)(Matchers.eq(session)) returns translations

      val model = articlesService.get(1)

      val expectedTranslations = List(Translation(1, Russian), Translation(2, English)).some
      model.map(_.translations) must beEqualTo(expectedTranslations)

      there was one(articlesRepository).getTranslations(1)(session)
    }


  }

  "paginated articles list" should {

    "request second page with correct parameters" in {
      tagsRepository.getByName(any)(Matchers.eq(session)) returns None
      articlesRepository.getList(anyInt, anyInt, any)(Matchers.eq(session)) returns List()
      articlesRepository.count(any)(Matchers.eq(session)) returns PAGE_SIZE_ARTICLES * 2

      articlesService.getPage(2)

      there was one(articlesRepository).getList(PAGE_SIZE_ARTICLES, PAGE_SIZE_ARTICLES, None)(session)
    }

    "contain list models" in {
      tagsRepository.getByName(any)(Matchers.eq(session)) returns None
      articlesRepository.getList(anyInt, anyInt, any)(Matchers.eq(session)) returns articleList
      articlesRepository.count(any)(Matchers.eq(session)) returns PAGE_SIZE_ARTICLES

      articlesService.getPage(1).fold(
        fail = nel => ko,
        succ = model => {
          model.list(0).id must_== 1
          model.list(0).author.id must_== 1
        }
      )
    }

    "contain article models with correct count of comments for specified page" in {
      tagsRepository.getByName(any)(Matchers.eq(session)) returns None
      articlesRepository.getList(anyInt, anyInt, any)(Matchers.eq(session)) returns articleList
      articlesRepository.count(any)(Matchers.eq(session)) returns PAGE_SIZE_ARTICLES

      articlesService.getPage(1).fold(
        fail = nel => ko,
        succ = model => {
          model.list(0).id must_== 1
          model.list(0).commentsCount must_== 1
        }
      )
    }

    "contain current page" in {
      tagsRepository.getByName(any)(Matchers.eq(session)) returns None
      articlesRepository.getList(anyInt, anyInt, any)(Matchers.eq(session)) returns articleList
      articlesRepository.count(any)(Matchers.eq(session)) returns PAGE_SIZE_ARTICLES

      articlesService.getPage(1).fold(
        fail = nel => ko,
        succ = model => model.currentPage must_== 1
      )
    }

    "contain total pages count" in {
      val count = 3 * PAGE_SIZE_ARTICLES/2
      tagsRepository.getByName(any)(Matchers.eq(session)) returns None
      articlesRepository.getList(anyInt, anyInt, any)(Matchers.eq(session)) returns articleList
      articlesRepository.count(any)(Matchers.eq(session)) returns count

      articlesService.getPage(1).fold(
        fail = nel => ko,
        succ = model => model.totalPages must_== 2
      )
    }

    "correctly return empty article list" in {
      tagsRepository.getByName(any)(Matchers.eq(session)) returns None
      articlesRepository.getList(anyInt, anyInt, any)(Matchers.eq(session)) returns articleList
      articlesRepository.count(any)(Matchers.eq(session)) returns 0

      articlesService.getPage(1).fold(
        fail = nel => ko,
        succ = model => {
          model.list.head.id must_== 1
          model.list.head.author.id must_== 1
        }
      )
    }

    "return failure for non-existing page" in {
      tagsRepository.getByName(any)(Matchers.eq(session)) returns None
      articlesRepository.getList(anyInt, anyInt, any)(Matchers.eq(session)) returns articleList
      articlesRepository.count(any)(Matchers.eq(session)) returns 1

      articlesService.getPage(-15).fold(
        fail = nel => ok,
        succ = model => ko
      )
    }

    "filter articles by tag" in {
      val count = 3 * PAGE_SIZE_ARTICLES/2
      tagsRepository.getByNames(Seq("tag"))(session) returns Seq(new Tag(1, "tag"))
      articlesRepository.getList(0, PAGE_SIZE_ARTICLES, Some(Seq(1)))(session) returns articleList
      articlesRepository.count(any)(Matchers.eq(session)) returns count
      tagValidator.validate("tag") returns "tag".successNel

      articlesService.getPage(1, Some("tag")).fold(
       fail = nel => ko,
       succ = model => {
         model.list.nonEmpty must beTrue
         model.currentPage must_== 1
         model.totalItems must_== count
       }
     )
    }

    "filter articles by more than one tag" in {
      val count = 3 * PAGE_SIZE_ARTICLES/2
      tagsRepository.getByNames(Seq("first", "second"))(session) returns Seq(new Tag(1, "first"), new Tag(2, "second"))
      articlesRepository.getList(0, PAGE_SIZE_ARTICLES, Some(Seq(1, 2)))(session) returns articleList
      articlesRepository.count(any)(Matchers.eq(session)) returns count
      tagValidator.validate("first") returns "first".successNel
      tagValidator.validate("second") returns "second".successNel

      articlesService.getPage(1, Some("first,second")).fold(
        fail = nel => ko,
        succ = model => {
          model.list.nonEmpty must beTrue
          model.currentPage must_== 1
          model.totalItems must_== count
        }
      )
    }

    "handle nonexistent tags" in {
      tagsRepository.getByNames(Seq("tag"))(session) returns Seq()
      articlesRepository.getList(0, PAGE_SIZE_ARTICLES, Some(List()))(session) returns articleList
      articlesRepository.count(any)(Matchers.eq(session)) returns PAGE_SIZE_ARTICLES

      articlesService.getPage(1, Some("tag")).fold(
        fail = nel => ko,
        succ = model => model.list.nonEmpty must beTrue
      )
    }

    "search all articles when tags are empty" in {
      val count = 3 * PAGE_SIZE_ARTICLES/2
      articlesRepository.getList(0, PAGE_SIZE_ARTICLES, None)(session) returns articleList
      articlesRepository.count(None)(session) returns count

      articlesService.getPage(1, Some("")).fold(
        fail = nel => ko,
        succ = model => {
          model.list.nonEmpty must beTrue
          model.currentPage must_== 1
          model.totalItems must_== count
        }
      )
    }
  }

  "creating new article" should {
    implicit def getCurrentUser = AuthenticatedUser(1, "username", Authorities.User)
    val article = Article(None, "", "", List(), Russian, None, true)
    val userRecord = UserRecord(1.some, "user", "password", "mail01@mail.zzz").some

    "insert new article" in {
      TimeFridge.withFrozenTime() { dt =>
          val record = ArticleRecord(None, "", "", dt, dt, "", getCurrentUser.userId, Russian, None, true)
          articlesRepository.insert(any[ArticleRecord])(Matchers.eq(session)) returns 1
          articleValidator.validate(any[Article]) returns article.successNel
          tagsService.createTagsForArticle(anyInt, any[Seq[String]])(Matchers.eq(session)) returns Seq.empty.successNel
          usersRepository.getByUsername(getCurrentUser.username)(session) returns userRecord

          articlesService.insert(article)

          there was one(articlesRepository).insert(record)(session)
      }
    }

    "insert new translation should send notification to author" in {
      TimeFridge.withFrozenTime() { dt =>
        val article = Article(None, "", "", List(), Russian, Some(1), true)
        articlesRepository.insert(any[ArticleRecord])(Matchers.eq(session)) returns 1
        articleValidator.validate(any[Article]) returns article.successNel
        tagsService.createTagsForArticle(anyInt, any[Seq[String]])(Matchers.eq(session)) returns Seq.empty.successNel
        usersRepository.getByUsername(getCurrentUser.username)(session) returns userRecord
        articlesRepository.getTranslations(anyInt)(Matchers.eq(session)) returns List()

        articlesService.insert(article)

        there was one(notificationsService)
          .createNotificationForArticleTranslation(any[ArticleDetailsModel])(any[AuthenticatedUser], Matchers.eq(session))
      }
    }

    "return model with assigned id" in {
      articlesRepository.insert(any[ArticleRecord])(Matchers.eq(session)) returns 1
      articleValidator.validate(any[Article]) returns article.successNel
      tagsService.createTagsForArticle(anyInt, any[Seq[String]])(Matchers.eq(session)) returns Seq.empty.successNel
      usersRepository.getByUsername(getCurrentUser.username)(session) returns userRecord

      articlesService.insert(article) match {
        case Authorized(Success(model)) => model.id must_== 1
        case _ => ko
      }
    }

    "create tags" in {
      val tags = List("tag1", "tag2")
      val articleId = 1
      articlesRepository.insert(any[ArticleRecord])(Matchers.eq(session)) returns articleId
      articleValidator.validate(any[Article]) returns article.successNel
      usersRepository.getByUsername(getCurrentUser.username)(session) returns userRecord
      tagsService.createTagsForArticle(anyInt, any[Seq[String]])(Matchers.eq(session)) returns tags.successNel

      articlesService.insert(article.copy(tags = tags))

      there was one(tagsService).createTagsForArticle(articleId, tags)(session)
    }

    "not create article when validation failed" in {
      articleValidator.validate(any[Article]) returns "".failureNel
      tagsService.createTagsForArticle(anyInt, any[Seq[String]])(Matchers.eq(session)) returns Seq.empty.successNel

      articlesService.insert(article)

      there was noMoreCallsTo(articlesRepository, tagsService)
    }

    "rollback transaction when tags creation failed" in {
      articleValidator.validate(any[Article]) returns article.successNel
      tagsService.createTagsForArticle(anyInt, any[Seq[String]])(Matchers.eq(session)) returns "".failureNel

      articlesService.insert(article)

      there was one(session).rollback()
    }

    "set author as current user" in {
      articlesRepository.insert(any[ArticleRecord])(Matchers.eq(session)) returns 1
      articleValidator.validate(any[Article]) returns article.successNel
      tagsService.createTagsForArticle(anyInt, any[Seq[String]])(Matchers.eq(session)) returns Seq.empty.successNel
      usersRepository.getByUsername(getCurrentUser.username)(session) returns userRecord

      articlesService.insert(article) match {
        case Authorized(Success(model)) => model.author.id must_== getCurrentUser.userId
        case _ => ko
      }
    }

    "fail when user is not authorized to do it" in {
      val currentUser = spy(AuthenticatedUser(1, "username", Authorities.User))
      org.mockito.Mockito.doReturn(false)
        .when(currentUser)
        .can(Matchers.eq(Permissions.Create), Matchers.eq(Entities.Article))

      articlesService.insert(article)(currentUser) must beLike {
        case NotAuthorized() => ok
        case _ => ko
      }
    }
  }

  "article update" should {
    val articleId = 1
    val tags = dbRecord._3
    val article = Article(articleId.some, "", "", tags, Russian, articleId.some, true)
    val translations = List((articleId, Russian.toString))
    implicit def getCurrentUser = AuthenticatedUser(1, "username", Authorities.User)

    "update existing article" in {
      articlesRepository.get(articleId)(session) returns dbRecord.some
      articlesRepository.getTranslations(articleId)(session) returns translations
      articleValidator.validate(any[Article]) returns article.successNel
      tagsService.updateTagsForArticle(Matchers.eq(articleId), any[Seq[String]])(Matchers.eq(session)) returns Seq.empty.successNel

      articlesService.updateArticle(article) must beSuccessful

      there was one(articlesRepository).update(Matchers.eq(articleId), any[ArticleToUpdate])(Matchers.eq(session))
    }

    "update modification time" in {
      TimeFridge.withFrozenTime() { now =>
        articlesRepository.get(articleId)(session) returns dbRecord.some
        articlesRepository.getTranslations(articleId)(session) returns translations
        articleValidator.validate(any[Article]) returns article.successNel
        tagsService.updateTagsForArticle(Matchers.eq(articleId), any[Seq[String]])(Matchers.eq(session)) returns Seq.empty.successNel

        articlesService.updateArticle(article)

        there was one(articlesRepository).update(articleId, ArticleToUpdate("", "", now, "", Russian.toString, true))(session) //TODO: match only modification time
      }
    }

    "update tags" in {
      articlesRepository.get(articleId)(session) returns dbRecord.some
      articlesRepository.getTranslations(articleId)(session) returns translations
      articleValidator.validate(any[Article]) returns article.successNel
      tagsService.updateTagsForArticle(articleId, tags)(session) returns Seq.empty.successNel

      articlesService.updateArticle(article)

      there was one(tagsService).updateTagsForArticle(articleId, tags)(session)
    }

    "return failure when tags validation failed" in {
      articlesRepository.get(articleId)(session) returns dbRecord.some
      articlesRepository.getTranslations(articleId)(session) returns translations
      articleValidator.validate(any[Article]) returns article.successNel
      tagsService.updateTagsForArticle(articleId, tags)(session) returns "".failureNel

      articlesService.updateArticle(article) must beFailing
    }

    "return failure when article validation failed" in {
      articlesRepository.get(articleId)(session) returns dbRecord.some
      articleValidator.validate(any[Article]) returns "".failureNel

      articlesService.updateArticle(article) must beFailing

      there was no(articlesRepository).update(any[Int], any[ArticleToUpdate])(anySession)
    }

    "return failure when article not found" in {
      articlesRepository.get(articleId)(session) returns None

      articlesService.updateArticle(article) must beFailing
    }

    "not update article when tags validation failed" in {
      articlesRepository.get(articleId)(session) returns dbRecord.some
      articleValidator.validate(any[Article]) returns article.successNel
      articlesRepository.getTranslations(articleId)(session) returns translations
      tagsService.updateTagsForArticle(Matchers.eq(articleId), any[Seq[String]])(Matchers.eq(session)) returns "".failureNel

      articlesService.updateArticle(article) must beFailing

      there was no(articlesRepository).update(anyInt, any[ArticleToUpdate])(anySession)
    }

    "not update article when article validation failed" in {
      articlesRepository.get(articleId)(session) returns dbRecord.some
      articleValidator.validate(any[Article]) returns "".failureNel

      articlesService.updateArticle(article) must beFailing

      there was no(articlesRepository).update(anyInt, any[ArticleToUpdate])(Matchers.eq(session))
    }

    "return authorization failure when user is not authorized to do it" in {
      val currentUser = spy(AuthenticatedUser(1, "username", Authorities.User))
      org.mockito.Mockito.doReturn(false)
        .when(currentUser)
        .can(Matchers.eq(Permissions.Update), Matchers.eq(dbRecord._1))
      articlesRepository.get(articleId)(session) returns dbRecord.some

      articlesService.updateArticle(article)(currentUser) must beSuccessful.like {
        case NotAuthorized() => ok
        case _ => ko
      }
    }
  }

  "article removal" should {
    val articleId = 1
    implicit def getCurrentUser = AuthenticatedUser(1, "username", Authorities.User)

    "remove article" in {
      articlesRepository.get(articleId)(session) returns dbRecord.some

      articlesService.removeArticle(articleId)

      there was one(articlesRepository).remove(articleId)(session)
    }

    "return successful result" in {
      articlesRepository.get(articleId)(session) returns dbRecord.some

      articlesService.removeArticle(articleId) must beSuccessful.like {
        case Authorized(()) => ok
        case _ => ko
      }
    }

    "return authorization failure when user is not authorized to do it" in {
      val currentUser = spy(AuthenticatedUser(1, "username", Authorities.User))
      org.mockito.Mockito.doReturn(false)
        .when(currentUser)
        .can(Matchers.eq(Permissions.Delete), Matchers.eq(dbRecord._1))
      articlesRepository.get(articleId)(session) returns dbRecord.some

      articlesService.removeArticle(articleId)(currentUser) must beSuccessful.like {
        case NotAuthorized() => ok
        case _ => ko
      }
    }
  }
}
