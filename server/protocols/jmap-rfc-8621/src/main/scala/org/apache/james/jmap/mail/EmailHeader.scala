/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap.mail

import java.nio.charset.StandardCharsets.US_ASCII

import org.apache.james.mime4j.codec.{DecodeMonitor, DecoderUtil}
import org.apache.james.mime4j.field.AddressListFieldImpl
import org.apache.james.mime4j.stream.Field
import org.apache.james.mime4j.util.MimeUtil

object EmailHeader {
  def apply(field: Field): EmailHeader = EmailHeader(EmailHeaderName(field.getName), RawHeaderValue.from(field))
}

object RawHeaderValue extends EmailHeaderValue {
  def from(field: Field): RawHeaderValue = RawHeaderValue(new String(field.getRaw.toByteArray, US_ASCII).substring(field.getName.length + 1))
}

object TextHeaderValue extends EmailHeaderValue {
  def from(field: Field): TextHeaderValue = TextHeaderValue(MimeUtil.unfold(DecoderUtil.decodeEncodedWords(field.getBody, DecodeMonitor.SILENT)).stripLeading())
}

object AddressesHeaderValue extends EmailHeaderValue {
  def from(field: Field): AddressesHeaderValue = AddressesHeaderValue(EmailAddress.from(AddressListFieldImpl.PARSER.parse(field, DecodeMonitor.SILENT).getAddressList))
}

case class EmailHeaderName(value: String) extends AnyVal

sealed trait EmailHeaderValue
case class RawHeaderValue(value: String) extends EmailHeaderValue
case class TextHeaderValue(value: String) extends EmailHeaderValue
case class AddressesHeaderValue(value: List[EmailAddress]) extends EmailHeaderValue

case class EmailHeader(name: EmailHeaderName, value: EmailHeaderValue)
