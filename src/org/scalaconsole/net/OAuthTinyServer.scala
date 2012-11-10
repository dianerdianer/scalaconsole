package org.scalaconsole
package net

import java.net._
import java.io._
import xml.XML
import scala.Some
import javax.swing.JOptionPane
import org.scalaconsole.ScalaConsole
import akka.actor._

object OAuthTinyServer {
  val client_id = "3d4d9d562d4fd186aa41"
  val client_secret = "d2ce4b5ea37b0bd6e3266868a8d38262b550302d"
  val socket = new ServerSocket(4568)
  val port = socket.getLocalPort

  val redirect_path = "/scalaconsole/callback"
  val redirect_uri = "http://localhost:%d%s".format(port, redirect_path)
  val authorize_uri =
    "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&scope=gist".format(client_id, URLEncoder.encode(redirect_uri, "UTF-8"))

  val exchange_template = "https://github.com/login/oauth/access_token?client_id=%s&client_secret=%s&code=%s"

  val ExtractCode = """.*code=(.*).*""".r
  val responseMessage = "ScalaConsole Authenticated. Please close this window."

  private[this] var access_token: Option[String] = None

  def accessToken: Option[String] = access_token

  private def accessToken_=(v: Option[String]) {
    access_token = v
  }


  def withAccessToken(callback: Option[String] => Unit) {
    import akka.actor.ActorDSL._

    val post = actor(actorSystem)(new Act {
      become {
        case token: String =>
          callback(Some(token))
          context.stop(context.self)
      }
    })
    if (accessToken.isDefined) post ! accessToken.get
    else new javax.swing.SwingWorker[Unit, Unit]() {
      def doInBackground() {
        java.awt.Desktop.getDesktop.browse(new java.net.URI(authorize_uri))
        val client = socket.accept()
        val reader = new BufferedReader(new InputStreamReader(client.getInputStream))
        val request_line = reader.readLine
        request_line.split("\\s") match {
          case Array(method: String, path: String, version: String) if valid(method, path, version) =>
            path match {
              case ExtractCode(code) =>
                val exchange_uri = new URL(exchange_template.format(client_id, client_secret, code))
                val conn = exchange_uri.openConnection().asInstanceOf[HttpURLConnection]
                conn.setRequestMethod("POST")
                val response_length = conn.getContentLength
                val buff = new Array[Byte](response_length)
                conn.getInputStream.read(buff)
                val content = new String(buff)
                accessToken = content.split("&").map(_.split("=")).find(_(0) == "access_token").map(_(1))
                for (token <- accessToken) {
                  post ! token
                  writeResponseMessage(client)
                }
                conn.disconnect()
                client.close()
            }
          case _ => throw new RuntimeException("Protocol Error")
        }
      }
    } execute()
  }

  private def writeResponseMessage(client: Socket) {
    val writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream))
    writer.write(responseMessage)
    writer.flush()
  }

  def valid(method: String, path: String, version: String) = {
    method.equalsIgnoreCase("GET") && path.contains(redirect_path) && version.startsWith("HTTP/1.")
  }

}