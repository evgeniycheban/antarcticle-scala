package util

import models.ArticleModels.Language._
import models.database._
import services.SlickSessionProvider
import scala.slick.driver.{H2Driver, JdbcProfile}
import scala.slick.jdbc.JdbcBackend
import scala.slick.jdbc.JdbcBackend.Database
import migrations.{MigrationTool, MigrationsContainer}

/**
 * Configures H2 database for tests. Database scope is single session.
 **/
trait TestDatabaseConfiguration extends Profile with SlickSessionProvider {
  def driverInstance = Class.forName("org.h2.Driver").newInstance.asInstanceOf[java.sql.Driver]

  override val profile: JdbcProfile = H2Driver
  override val db: JdbcBackend#Database = Database.forDriver(driver = driverInstance, url = "jdbc:h2:mem:test1;DATABASE_TO_UPPER=false")
}

/**
 * Provides method withTestDb that executes migrations, populates database with fixtures and runs test method in one session.
 * Can be customized by overriding fixtures method.
 **/
trait TestDatabaseConfigurationWithFixtures extends TestDatabaseConfiguration with MigrationTool {
  this: Schema =>

  import com.github.nscala_time.time.Imports._
  import utils.Implicits._
  import models.database._
  import profile.simple._

  class EmptyMigrationsConainer extends MigrationsContainer

  override val migrationsContainer = new EmptyMigrationsConainer

  def withTestDb[T](f: JdbcBackend#Session => T): T = withSession { implicit session =>
    migrate
    fixtures
    f(session)
  }

  // you can override it in subclasses for more specific fixtures
  def fixtures(implicit session: JdbcBackend#Session): Unit = {
    val time = DateTime.now

    tags.map(_.name) ++= Seq("tag1", "tag2", "tag3")

    users ++= Seq(
      UserRecord(None, "user1", "password1", "mail01@mail.zzz"),
      UserRecord(None, "user2", "password2", "mail02@mail.zzz"),
      UserRecord(None, "user3", "password3", "mail03@mail.zzz"),
      UserRecord(None, "doesn't have notifications", "password3", "mail04@mail.zzz")
    )

    articles.map(a => (a.title, a.content, a.createdAt, a.updatedAt, a.description, a.authorId, a.language, a.sourceId, a.published)) ++= Seq(
        ("New title 1", "<b>content</b>", time + 1.day, time, "description1", 1, Russian, 1, true),
        ("New title 2", "<i>html text</i>", time, time, "description2", 2, Russian, 1, true),
        ("New title 3", "<i>html text</i>", time + 2.days, time, "description3", 2, Russian, 1, true),
        ("New title 4", "<i>html text</i>", time + 4.days, time, "description4", 2, Russian, 1, false)
      )

    articlesTags ++= Seq(
       (1,1), (1,2), (2,1), (2,3)
    )

    comments ++= Seq(
      CommentRecord(None, 1, 1, "<b>content</b>", time + 1.hour, None),
      CommentRecord(None, 2, 1, "content", time + 1.day, None),
      CommentRecord(None, 2, 1, "dsf sdf s$#$ 4#%#$", time, None),
      CommentRecord(None, 1, 2, "42342", time + 1.week, None),
      CommentRecord(None, 2, 2, "", time + 1.second, None),
      CommentRecord(None, 2, 3, "dsfasdfsdaf", time, None),
      CommentRecord(None, 1, 4, "<b>content2</b>", time - 10.days, None),
      CommentRecord(None, 1, 1, "<b>content42342</b>", time + 5.minutes, None)
    )

    notifications ++= Seq(
      Notification(Some(1), 2, 2, Some(2), "Be careful, it's JTalks, baby", "Don't deny it, you met it.", time),
      Notification(Some(2), 2, 2, Some(2), "Again and again", "Don't deny it, you met it.", time),
      Notification(Some(3), 1, 2, Some(2), "You can delete this notification.", "But be careful, it's JTalks, baby.", time),
      Notification(Some(4), 3, 2, Some(2), "Have you checked a content of your notification?", "Bullshit, do it now.", time)
    )

    properties ++= Seq(
      ApplicationProperty(Some(1), "property", Some("property for a test"), "default value of property for a test", time),
      ApplicationProperty(Some(2), "property_for_update", Some("property for update"), "property for update", time)
    )
  }
}
