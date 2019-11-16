package emil.javamail.conv

import emil._
import emil.javamail.internal.{InternalId, Util}
import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail.{Address, Flags, Folder, Message, internet}

trait BasicDecode {

  implicit def flagDecode: Conv[Flags.Flag, Option[Flag]] =
    Conv(flag => if (flag == Flags.Flag.FLAGGED) Some(Flag.Flagged) else None)

  implicit def folderConv: Conv[Folder, MailFolder] =    Conv(f => MailFolder(f.getFullName, f.getName))

  implicit def mailAddressDecode: Conv[Address, MailAddress] =
    Conv {
      case a: InternetAddress =>
        MailAddress.unsafe(Option(a.getPersonal), a.getAddress)
      case a =>
        val ia = new internet.InternetAddress(a.toString)
        MailAddress.unsafe(Option(ia.getPersonal), ia.getAddress)
    }

  implicit def mailAddressParse: Conv[String, MailAddress] =
    Conv[String, InternetAddress](str => new InternetAddress(str)).
      map(a => MailAddress.unsafe(Option(a.getPersonal), a.getAddress))

  implicit def recipientsDecode(implicit ca: Conv[Address, MailAddress]): Conv[MimeMessage, Recipients] = {
    def recipients(msg: MimeMessage, t: Message.RecipientType): List[Address] =
      Option(msg.getRecipients(t)).getOrElse(Array.empty[Address]).toList

    Conv(msg => Recipients(
      recipients(msg, Message.RecipientType.TO).map(ca.convert),
      recipients(msg, Message.RecipientType.CC).map(ca.convert),
      recipients(msg, Message.RecipientType.BCC).map(ca.convert)
    ))
  }

  implicit def mailHeaderDecode(implicit cf: Conv[Folder, MailFolder]
                               , ca: Conv[Address, MailAddress]
                               , cr: Conv[MimeMessage, Recipients]
                               , cs: Conv[String, MailAddress]): Conv[MimeMessage, MailHeader] =
    Conv(msg => Util.withReadFolder(msg) { _ =>
        emil.MailHeader(
          id = InternalId.makeInternalId(msg).asString,
          messageId = Option(msg.getMessageID),
          folder = Option(msg.getFolder).map(cf.convert),
          recipients = cr.convert(msg),
          sender = Option(msg.getSender).map(ca.convert),
          from = msg.getFrom.headOption.map(ca.convert),
          replyTo = {
            // msg.getReplyTo method calls getFrom if there is no ReplyTo header, but we don't want this fallback
            Option(msg.getHeader("Reply-To", ",")).map(cs.convert)
          },
          receivedDate = Option(msg.getReceivedDate).map(_.toInstant),
          sentDate = Option(msg.getSentDate).map(_.toInstant),
          subject = msg.getSubject,
          flags = if (msg.getFlags.contains(Flags.Flag.FLAGGED)) Set(Flag.Flagged) else Set.empty
        )
      })
}