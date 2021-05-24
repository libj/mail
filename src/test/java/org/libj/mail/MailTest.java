/* Copyright (c) 2010 LibJ
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.libj.mail;

import static org.junit.Assert.*;

import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.junit.Ignore;
import org.junit.Test;

public class MailTest {
  private int successCount;

  private final class TestMessage extends Mail.Message  {
    private TestMessage(final String subject, final MimeContent content, final InternetAddress from, final String... to) throws AddressException {
      super(subject, content, from, to);
    }

    @Override
    public void success() {
      ++successCount;
    }

    @Override
    public void failure(final MessagingException e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Ignore
  public void testClient() throws Exception {
    final PasswordAuthentication smtpCredentials = new PasswordAuthentication("filehost", "FileH0st");
    final Mail.Dispatch dispatch = new Mail.Dispatch.Builder("smtp.safris.com", 465)
      .withSsl(true)
      .withTls(true)
      .withDebug(true)
      .build();

    final Mail.Message[] messages = new TestMessage[] {
      new TestMessage("test1", new MimeContent("test1", "text/html"), new InternetAddress("seva@safris.org", "org"), "seva.safris@gmail.com"),
      new TestMessage("test2", new MimeContent("test2", "text/html"), new InternetAddress("seva@safris.com", "com"), "safris@berkeley.edu"),
      new TestMessage("test3", new MimeContent("test3", "text/html"), new InternetAddress("seva@safris.biz", "biz"), "seva@djseva.com")
    };

    dispatch.send(smtpCredentials, messages);
    assertEquals(messages.length, successCount);
  }
}