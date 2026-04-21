package repositories

import models._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.PostgresProfile

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RoomRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[PostgresProfile] {
  import profile.api._

  implicit val visibilityMapper: profile.BaseColumnType[RoomVisibility] =
    MappedColumnType.base[RoomVisibility, String](
      { case RoomVisibility.Public => "public"; case RoomVisibility.Private => "private" },
      { case "public" => RoomVisibility.Public; case _ => RoomVisibility.Private },
    )

  implicit val roleMapper: profile.BaseColumnType[MemberRole] =
    MappedColumnType.base[MemberRole, String](
      { case MemberRole.Owner => "owner"; case MemberRole.Admin => "admin"; case _ => "member" },
      { case "owner" => MemberRole.Owner; case "admin" => MemberRole.Admin; case _ => MemberRole.Member },
    )

  private class RoomsTable(tag: Tag) extends Table[Room](tag, "rooms") {
    def id          = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name        = column[String]("name")
    def description = column[Option[String]]("description")
    def visibility  = column[RoomVisibility]("visibility")
    def ownerId     = column[Long]("owner_id")
    def createdAt   = column[Instant]("created_at")
    def deletedAt   = column[Option[Instant]]("deleted_at")
    def * = (id, name, description, visibility, ownerId, createdAt, deletedAt).mapTo[Room]
  }

  private class MembersTable(tag: Tag) extends Table[RoomMember](tag, "room_members") {
    def roomId   = column[Long]("room_id")
    def userId   = column[Long]("user_id")
    def role     = column[MemberRole]("role")
    def joinedAt = column[Instant]("joined_at")
    def * = (roomId, userId, role, joinedAt).mapTo[RoomMember]
  }

  private class BansTable(tag: Tag) extends Table[RoomBan](tag, "room_bans") {
    def roomId   = column[Long]("room_id")
    def userId   = column[Long]("user_id")
    def bannedBy = column[Long]("banned_by")
    def bannedAt = column[Instant]("banned_at")
    def * = (roomId, userId, bannedBy, bannedAt).mapTo[RoomBan]
  }

  private val rooms   = TableQuery[RoomsTable]
  private val members = TableQuery[MembersTable]
  private val bans    = TableQuery[BansTable]

  def create(name: String, description: Option[String], visibility: RoomVisibility, ownerId: Long): Future[Room] = {
    val room = Room(0L, name, description, visibility, ownerId, Instant.now(), None)
    db.run((rooms returning rooms.map(_.id) into ((r, id) => r.copy(id = id))) += room)
  }

  def findById(id: Long): Future[Option[Room]] =
    db.run(rooms.filter(r => r.id === id && r.deletedAt.isEmpty).result.headOption)

  def findPublic(search: Option[String]): Future[Seq[Room]] = {
    val base = rooms.filter(r => r.visibility === (RoomVisibility.Public: RoomVisibility) && r.deletedAt.isEmpty)
    val filtered = search.fold(base)(q => base.filter(_.name.toLowerCase like s"%${q.toLowerCase}%"))
    db.run(filtered.sortBy(_.createdAt.desc).result)
  }

  def findByMember(userId: Long): Future[Seq[Room]] =
    db.run(
      members.filter(_.userId === userId)
        .join(rooms).on(_.roomId === _.id)
        .filter(_._2.deletedAt.isEmpty)
        .map(_._2)
        .sortBy(_.name)
        .result
    )

  private def roleToStr(role: MemberRole): String = role match {
    case MemberRole.Owner  => "owner"
    case MemberRole.Admin  => "admin"
    case MemberRole.Member => "member"
  }

  def addMember(roomId: Long, userId: Long, role: MemberRole): Future[Unit] = {
    val roleStr = roleToStr(role)
    db.run(
      sqlu"""INSERT INTO room_members (room_id, user_id, role, joined_at)
             VALUES ($roomId, $userId, $roleStr, NOW())
             ON CONFLICT (room_id, user_id) DO UPDATE SET role = EXCLUDED.role"""
    ).map(_ => ())
  }

  def banInsert(roomId: Long, userId: Long, bannedBy: Long): Future[Unit] = {
    db.run(
      sqlu"""INSERT INTO room_bans (room_id, user_id, banned_by, banned_at)
             VALUES (${roomId}, ${userId}, ${bannedBy}, NOW())
             ON CONFLICT (room_id, user_id) DO NOTHING"""
    ).map(_ => ())
  }

  def removeMember(roomId: Long, userId: Long): Future[Unit] =
    db.run(members.filter(m => m.roomId === roomId && m.userId === userId).delete).map(_ => ())

  def findMembers(roomId: Long): Future[Seq[RoomMember]] =
    db.run(members.filter(_.roomId === roomId).result)

  def getMemberRole(roomId: Long, userId: Long): Future[Option[MemberRole]] =
    db.run(members.filter(m => m.roomId === roomId && m.userId === userId).map(_.role).result.headOption)

  def isMember(roomId: Long, userId: Long): Future[Boolean] =
    db.run(members.filter(m => m.roomId === roomId && m.userId === userId).exists.result)

  def isBanned(roomId: Long, userId: Long): Future[Boolean] =
    db.run(bans.filter(b => b.roomId === roomId && b.userId === userId).exists.result)

  def ban(roomId: Long, userId: Long, bannedBy: Long): Future[Unit] =
    banInsert(roomId, userId, bannedBy)

  def unban(roomId: Long, userId: Long): Future[Unit] =
    db.run(bans.filter(b => b.roomId === roomId && b.userId === userId).delete).map(_ => ())

  def updateRole(roomId: Long, userId: Long, newRole: MemberRole): Future[Unit] = {
    val roleStr = roleToStr(newRole)
    db.run(
      members.filter(m => m.roomId === roomId && m.userId === userId)
        .map(_.role)
        .update(newRole)
    ).map(_ => ())
  }

  def findBans(roomId: Long): Future[Seq[RoomBan]] =
    db.run(bans.filter(_.roomId === roomId).result)

  def findByOwner(ownerId: Long): Future[Seq[Room]] =
    db.run(rooms.filter(r => r.ownerId === ownerId && r.deletedAt.isEmpty).result)

  def softDelete(roomId: Long): Future[Unit] =
    db.run(rooms.filter(_.id === roomId).map(_.deletedAt).update(Some(Instant.now()))).map(_ => ())

  def hardDelete(roomId: Long): Future[Unit] =
    db.run(rooms.filter(_.id === roomId).delete).map(_ => ())

  def removeAllMembershipsForUser(userId: Long): Future[Unit] =
    db.run(members.filter(_.userId === userId).delete).map(_ => ())

  def countMembers(roomId: Long): Future[Int] =
    db.run(members.filter(_.roomId === roomId).length.result)

  def update(roomId: Long, name: String, description: Option[String], visibility: RoomVisibility): Future[Unit] =
    db.run(
      rooms.filter(_.id === roomId)
        .map(r => (r.name, r.description, r.visibility))
        .update((name, description, visibility))
    ).map(_ => ())
}
