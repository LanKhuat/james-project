/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ************************************************************** */

package org.apache.james.jmap.method

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_MAIL, JMAP_MDN}
import org.apache.james.jmap.core.Invocation._
import org.apache.james.jmap.core.{AccountId, ErrorCode, Invocation, Session}
import org.apache.james.jmap.json.{MDNParseSerializer, ResponseSerializer}
import org.apache.james.jmap.mail.MDNParse.UnparsedBlobId
import org.apache.james.jmap.mail.{BlobId, MDNParseRequest, MDNParseResponse, MDNParseResults, MDNParsed}
import org.apache.james.jmap.routes.{BlobNotFoundException, BlobResolvers, SessionSupplier}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mdn.MDN
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.server.core.MimeMessageInputStream
import org.apache.james.util.MimeMessageUtil
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}

import java.io.InputStream
import javax.inject.Inject
import scala.util.{Failure, Success, Try}

case class RequestTooLargeException(description: String) extends Exception

class MDNParseMethod @Inject()(val blobResolvers: BlobResolvers,
                               val metricFactory: MetricFactory,
                               val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[MDNParseRequest] {
  override val methodName: MethodName = MethodName("MDN/parse")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_MDN, JMAP_MAIL)

  def doProcess(capabilities: Set[CapabilityIdentifier],
                invocation: InvocationWithContext,
                mailboxSession: MailboxSession,
                request: MDNParseRequest): SMono[InvocationWithContext] = {
    computeResponseInvocation(request, invocation.invocation, mailboxSession)
      .map(InvocationWithContext(_, invocation.processingContext))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, MDNParseRequest] =
    MDNParseSerializer.deserializeMDNParseRequest(invocation.arguments.value) match {
      case JsSuccess(emailGetRequest, _) => validateRequestParameters(emailGetRequest)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  private def validateRequestParameters(request: MDNParseRequest): Either[RequestTooLargeException, MDNParseRequest] =
    if (request.blobIds.value.length > 200) {
      Left(RequestTooLargeException("The number of ids requested by the client exceeds the maximum number the server is willing to process in a single method call"))
    } else {
      Right(request)
    }

  def computeResponseInvocation(request: MDNParseRequest,
                                invocation: Invocation,
                                mailboxSession: MailboxSession): SMono[Invocation] =
    computeResponse(request, mailboxSession)
      .map(res => Invocation(
        methodName,
        Arguments(MDNParseSerializer.serialize(res).as[JsObject]),
        invocation.methodCallId))

  private def computeResponse(request: MDNParseRequest,
                              mailboxSession: MailboxSession): SMono[MDNParseResponse] = {
    val validations: Seq[Either[MDNParseResults, BlobId]] = request.blobIds.value
      .map(id => BlobId.of(id)
        .toEither
        .left
        .map(_ => MDNParseResults.notFound(id)))
    val parsedIds: Seq[BlobId] = validations.flatMap(_.toOption)
    val invalid: Seq[MDNParseResults] = validations.map(_.left).flatMap(_.toOption)

    val parsed: SFlux[MDNParseResults] = SFlux.fromIterable(parsedIds)
      .flatMap(blobId => blobResolvers.resolve(blobId, mailboxSession))
      .map(blob => parse(blob.blobId, blob.content))

    SFlux.merge(Seq(parsed, SFlux.fromIterable(invalid)))
      .onErrorRecover {
        case e: BlobNotFoundException => MDNParseResults.notFound(e.blobId)
      }
      .reduce(MDNParseResults.empty())(MDNParseResults.merge)
      .map(result => result.asResponse(request.accountId))
  }

  private def parse(blobId: BlobId, blobContent: InputStream): MDNParseResults = {
    val maybeMdn = for {
      message <- Try(new DefaultMessageBuilder().parseMessage(blobContent))
      mdn <- Try(MDN.parse(message))
      jmapMdn = MDNParsed.convertFromMDN(mdn, message)
    } yield {
      MDNParseResults.parse(blobId, jmapMdn)
    }

    maybeMdn.fold(_ => MDNParseResults.notParse(blobId), result => result)
  }
}
