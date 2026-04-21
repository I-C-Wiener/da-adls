package repositories

import models.User
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[PostgresProfile]
  import dbConfig._
  import profile.api._

  private class UsersTable(tag: Tag) extends Table[User](tag, "users") {
    def id           = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def email        = column[String]("email")
    def username     = column[String]("username")
    def passwordHash = column[String]("password_hash")
    def displayName  = column[Option[String]]("display_name")
    def avatarUrl    = column[Option[String]]("avatar_url")
    def isDeleted    = column[Boolean]("is_deleted")
    def createdAt    = column[Instant]("created_at")
    def * = (id, email, username, passwordHash, displayName, avatarUrl, isDeleted, createdAt).mapTo[User]
  }

  private val users = TableQuery[UsersTable]

  def create(email: String, username: String, passwordHash: String): Future[Either[String, User]] = {
    val newUser = User(0L, email, username, passwordHash, None, None, isDeleted = false, Instant.now())
    db.run((users returning users.map(_.id) into ((u, id) => u.copy(id = id))) += newUser)
      .map(Right(_))
      .recover {
        case ex: Exception if ex.getMessage.contains("unique") =>
          Left(if (ex.getMessage.contains("email")) "Email already registered" else "Username already taken")
      }
  }

  def findByEmailOrUsername(loginId: String): Future[Option[User]] =
    db.run(users.filter(u => u.email === loginId || u.username === loginId).result.headOption)

  def findByEmail(email: String): Future[Option[User]] =
    db.run(users.filter(u => u.email === email && !u.isDeleted).result.headOption)

  def findById(id: Long): Future[Option[User]] =
    db.run(users.filter(_.id === id).result.headOption)

  def findByIds(ids: Seq[Long]): Future[Map[Long, String]] =
    if (ids.isEmpty) Future.successful(Map.empty)
    else db.run(users.filter(_.id inSet ids).map(u => (u.id, u.username)).result).map(_.toMap)

  def findByUsername(username: String): Future[Option[User]] =
    db.run(users.filter(u => u.username === username && !u.isDeleted).result.headOption)

  def updatePassword(id: Long, newHash: String): Future[Unit] =
    db.run(users.filter(_.id === id).map(_.passwordHash).update(newHash)).map(_ => ())

  def softDelete(id: Long): Future[Unit] =
    db.run(users.filter(_.id === id).map(_.isDeleted).update(true)).map(_ => ())
}
