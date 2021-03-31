/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                  *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ************************************************************** */

package org.apache.james.jmap.rfc8621.contract

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.message.MultipartBuilder
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.DataProbeImpl
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.junit.jupiter.api.{BeforeEach, Test}
import play.api.libs.json._

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

trait MDNParseMethodContract {
  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await

  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(5, TimeUnit.SECONDS)
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build()
  }

  def randomMessageId: MessageId

  @Test
  def mdnParseHasValidBodyFormatShouldSucceed(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mdn.eml")))
      .getMessageId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${messageId.serialize()}" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post.prettyPeek()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |      [ "MDN/parse", {
           |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |         "parsed": {
           |           "${messageId.serialize()}": {
           |             "subject": "Read: test",
           |             "textBody": "To: magiclan@linagora.com\\r\\nSubject: test\\r\\nMessage was displayed on Tue Mar 30 2021 10:31:50 GMT+0700 (Indochina Time)",
           |             "reportingUA": "OpenPaaS Unified Inbox; ",
           |             "disposition": {
           |               "actionMode": "manual-action",
           |               "sendingMode": "MDN-sent-manually",
           |               "type": "displayed"
           |             },
           |             "finalRecipient": "rfc822; tungexplorer@linagora.com",
           |             "originalMessageId": "<633c6811-f897-ec7c-642a-2360366e1b93@linagora.com>",
           |             "error": [
           |                "Message1",
           |                "Message2"
           |             ],
           |             "extension": {
           |                "X-OPENPAAS-IP" : " 177.177.177.77",
           |                "X-OPENPAAS-PORT" : " 8000"
           |             }
           |           }
           |         }
           |      }, "c1" ]]
           |}""".stripMargin)
  }

  @Test
  def mdnParseShouldFailWhenWrongAccountId(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
         |    {
         |      "accountId": "unknownAccountId",
         |      "blobIds": [ "0f9f65ab-dc7b-4146-850f-6e4881093965" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [[
         |            "error",
         |            {
         |                "type": "accountNotFound"
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def mdnParseShouldFailWhenNumberOfBlobIdsTooLarge(): Unit = {
    val blogIds = LazyList.continually(randomMessageId.serialize()).take(201).toArray;
    val blogIdsJson = Json.stringify(Json.arr(blogIds)).replace("[[", "[").replace("]]", "]");
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds":  ${blogIdsJson}
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "error",
           |    {
           |          "type": "requestTooLarge",
           |          "description": "The number of ids requested by the client exceeds the maximum number the server is willing to process in a single method call"
           |    },
           |    "c1"]]
           |}""".stripMargin)
  }

  @Test
  def parseShouldReturnNotParseableWhenNotAnMDN(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val messageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.builder()
        .build(Message.Builder
          .of
          .setSubject("Subject MDN")
          .setSender(ANDRE.asString())
          .setFrom(ANDRE.asString())
          .setBody(MultipartBuilder.create("report")
            .addTextPart("This is body of text part", StandardCharsets.UTF_8)
            .build)
          .build))
      .getMessageId

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${messageId.serialize()}" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [[
         |      "MDN/parse",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "notParsable": ["${messageId.serialize()}"]
         |      },
         |      "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def parseShouldReturnNotFoundWhenBlobDoNotExist(): Unit = {
    val blobIdShouldNotFound = randomMessageId.serialize()
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "$blobIdShouldNotFound" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [[
         |      "MDN/parse",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "notFound": ["$blobIdShouldNotFound"]
         |      },
         |      "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def parseShouldReturnNotFoundWhenBadBlobId(): Unit = {
    val blobIdShouldNotFound = randomMessageId.serialize()
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "invalid" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [[
         |      "MDN/parse",
         |      {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "notFound": ["invalid"]
         |      },
         |      "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def parseAndNotFoundAndNotParsableCanBeMixed(guiceJamesServer: GuiceJamesServer): Unit = {
    val path: MailboxPath = MailboxPath.inbox(BOB)
    val mailboxProbe: MailboxProbeImpl = guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(path)

    val blobIdParsable: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.from(
        ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mdn.eml")))
      .getMessageId

    val blobIdNotParsable: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), path, AppendCommand.builder()
        .build(Message.Builder
          .of
          .setSubject("Subject MDN")
          .setSender(ANDRE.asString())
          .setFrom(ANDRE.asString())
          .setBody(MultipartBuilder.create("report")
            .addTextPart("This is body of text part", StandardCharsets.UTF_8)
            .build)
          .build))
      .getMessageId
    val blobIdNotFound = randomMessageId
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:mdn",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "${blobIdParsable.serialize()}", "${blobIdNotParsable.serialize()}", "${blobIdNotFound.serialize()}" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted{
      () => {
        val response = `given`
          .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
          .body(request)
        .when
          .post.prettyPeek()
        .`then`
          .statusCode(SC_OK)
          .contentType(JSON)
          .extract
          .body
          .asString
        assertThatJson(response).isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [[
             |      "MDN/parse",
             |      {
             |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |        "notFound": ["${blobIdNotFound.serialize()}"],
             |        "notParsable": ["${blobIdNotParsable.serialize()}"],
             |        "parsed": {
             |           "${blobIdParsable.serialize()}": {
             |             "subject": "Read: test",
             |             "textBody": "To: magiclan@linagora.com\\r\\nSubject: test\\r\\nMessage was displayed on Tue Mar 30 2021 10:31:50 GMT+0700 (Indochina Time)",
             |             "reportingUA": "OpenPaaS Unified Inbox; ",
             |             "disposition": {
             |               "actionMode": "manual-action",
             |               "sendingMode": "MDN-sent-manually",
             |               "type": "displayed"
             |             },
             |             "finalRecipient": "rfc822; tungexplorer@linagora.com",
             |             "originalMessageId": "<633c6811-f897-ec7c-642a-2360366e1b93@linagora.com>",
             |             "error": [
             |                "Message1",
             |                "Message2"
             |             ],
             |             "extension": {
             |                "X-OPENPAAS-IP" : " 177.177.177.77",
             |                "X-OPENPAAS-PORT" : " 8000"
             |             }
             |          }
             |        }
             |      },
             |      "c1"
             |        ]]
             |}""".stripMargin)
      }
    }
  }


  @Test
  def mdnParseShouldReturnUnknownMethodWhenMissingOneCapability(): Unit = {
    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "MDN/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "123" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:mdn"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def mdnParseShouldReturnUnknownMethodWhenMissingAllCapabilities(): Unit = {
    val request =
      s"""{
         |  "using": [],
         |  "methodCalls": [[
         |    "MDN/parse",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "blobIds": [ "123" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:mdn, urn:ietf:params:jmap:mail"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }
}
