package emil.tnef

import cats.effect._
import cats.effect.unsafe.implicits.global
import emil.builder._
import fs2.hashing.{HashAlgorithm, Hashing}
import munit._
import scodec.bits.ByteVector

class TnefReaderTest extends FunSuite {

  val winmailDatUrl = getClass.getResource("/winmail.dat")
  require(winmailDatUrl != null, "test file not found")

  val winmailData =
    fs2.io.readInputStream(IO(winmailDatUrl.openStream()), 8192)

  test("read tnef file") {
    val data = TnefExtract
      .fromStream[IO](winmailData)
      .evalMap { a =>
        val file = a.filename.getOrElse(sys.error("no filename"))
        val sha = a.content
          .through(Hashing.forSync[IO].hash(HashAlgorithm.SHA256))
          .map(_.bytes)
          .compile
          .fold(ByteVector.empty)((bv, c) => bv ++ c.toByteVector)
          .map(_.toHex)
        val size = a.length
        for {
          cs <- sha
          sz <- size
        } yield (file, cs, sz)
      }
      .compile
      .toVector
      .unsafeRunSync()
      .sortBy(_._3)

    assertEquals(data.size, 2)
    assertEquals(data.head._1, "ZAPPA_~2.JPG")
    assertEquals(
      data.head._2,
      "bea844f30e0fcc20fad419a0d11032a6465da93c1da185a1196949955994409a"
    )
    assertEquals(data.head._3, 2937L)

    assertEquals(data(1)._1, "bookmark.htm")
    assertEquals(
      data(1)._2,
      "1e08d6e23c75ff80ac992eebc24c2943c7843b7dfee235966b37de5eb4362599"
    )
    assertEquals(data(1)._3, 85805L)
  }

  test("change mail") {
    val mail = MailBuilder.build[IO](
      From("me@example.com"),
      To("me@example.com"),
      Subject("test"),
      TextBody[IO]("hello"),
      AttachStream[IO](winmailData, Some("winmail.dat"), TnefMimeType.applicationTnef)
    )

    assertEquals(mail.attachments.size, 1)

    val mail2 = TnefExtract.replace[IO](mail).unsafeRunSync()
    assertEquals(mail2.attachments.size, 2)
  }
}
