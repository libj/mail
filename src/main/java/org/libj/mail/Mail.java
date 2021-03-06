/* Copyright (c) 2009 LibJ
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link Mail} class is used to send email via a SMTP(S) server. This
 * implementation supports a wide configuration requirement set as could be
 * expected by SMPT(S) servers.
 */
public final class Mail {
  private static final Logger logger = LoggerFactory.getLogger(Mail.class);

  private Mail() {
  }

  /**
   * Enum representing the mail transfer protocol.
   */
  public enum Protocol {
    SMTP, SMTPS
  }

  /**
   * Class representing a email message.
   */
  public static class Message {
    private static InternetAddress[] toInternetAddress(final String ... emailAddrs) throws AddressException {
      if (emailAddrs == null)
        return null;

      final InternetAddress[] addresses = new InternetAddress[emailAddrs.length];
      for (int i = 0; i < emailAddrs.length; ++i)
        addresses[i] = new InternetAddress(emailAddrs[i]);

      return addresses;
    }

    public final String subject;
    public final MimeContent content;
    public final InternetAddress from;
    public final InternetAddress[] to;
    public final InternetAddress[] cc;
    public final InternetAddress[] bcc;

    /**
     * Create a new {@link Message} with the supplied parameters.
     *
     * @param subject The subject of the message.
     * @param content The {@link MimeContent} content.
     * @param from The "from" {@link InternetAddress}.
     * @param to A string array of "to" email addresses.
     * @param cc A string array of "cc" email addresses.
     * @param bcc A string array of "bcc" email addresses.
     * @throws AddressException If the parse of an email address failed.
     * @throws IllegalArgumentException If {@code subject}, {@code content},
     *           {@code from}, or each of {@code to}, {@code cc}, and
     *           {@code bcc} are null.
     */
    public Message(final String subject, final MimeContent content, final InternetAddress from, final String[] to, final String[] cc, final String[] bcc) throws AddressException {
      this(subject, content, from, toInternetAddress(to), toInternetAddress(cc), toInternetAddress(bcc));
    }

    /**
     * Create a new {@link Message} with the supplied parameters.
     *
     * @param subject The subject of the message.
     * @param content The {@link MimeContent} content.
     * @param from The "from" {@link InternetAddress}.
     * @param to A {@link InternetAddress} array of "to" addresses.
     * @param cc A {@link InternetAddress} array of "cc" addresses.
     * @param bcc A {@link InternetAddress} array of "bcc" addresses.
     * @throws IllegalArgumentException If {@code subject}, {@code content},
     *           {@code from}, or each of {@code to}, {@code cc}, and
     *           {@code bcc} are null.
     */
    public Message(final String subject, final MimeContent content, final InternetAddress from, final InternetAddress[] to, final InternetAddress[] cc, final InternetAddress[] bcc) {
      this.subject = subject;
      if (subject == null)
        throw new IllegalArgumentException("subject == null");

      this.content = content;
      if (content == null)
        throw new IllegalArgumentException("content == null");

      this.from = from;
      if (from == null)
        throw new IllegalArgumentException("from == null");

      this.to = to;
      this.cc = cc;
      this.bcc = bcc;
      if ((to == null || to.length == 0) && (cc == null || cc.length == 0) && (bcc == null || bcc.length == 0))
        throw new IllegalArgumentException("Either \"to\", \"cc\", or \"bcc\" must not be empty");
    }

    public Message(final String subject, final MimeContent content, final InternetAddress from, final String ... to) throws AddressException {
      this(subject, content, from, to, null, null);
    }

    /**
     * Default no-op implementation of success callback.
     */
    public void success() {
    }

    /**
     * Default no-op implementation of failure callback.
     *
     * @param e The {@link MessagingException} that led to the failure.
     */
    public void failure(final MessagingException e) {
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj == this)
        return true;

      if (!(obj instanceof Message))
        return false;

      final Message that = (Message)obj;
      return subject.equals(that.subject) && content.equals(that.content) && from.equals(that.from) && Arrays.equals(to, that.to) && Arrays.equals(cc, that.cc) && Arrays.equals(bcc, that.bcc);
    }

    @Override
    public int hashCode() {
      int hashCode = 1;
      hashCode = 31 * hashCode + subject.hashCode();
      hashCode = 31 * hashCode + content.hashCode();
      hashCode = 31 * hashCode + from.hashCode();
      hashCode = 31 * hashCode + Arrays.hashCode(to);
      hashCode = 31 * hashCode + Arrays.hashCode(cc);
      hashCode = 31 * hashCode + Arrays.hashCode(bcc);
      return hashCode;
    }
  }

  /**
   * Class representing the SMTP(S) sender.
   */
  public static class Sender {
    private static final boolean debug;
    private static String externalIP;

    private static String getExternalIP() throws IOException {
      if (externalIP != null)
        return externalIP;

      final URL whatismyip = new URL("http://checkip.amazonaws.com");
      try (final BufferedReader in = new BufferedReader(new InputStreamReader(whatismyip.openStream()))) {
        return externalIP = in.readLine();
      }
    }

    /**
     * Do a reverse DNS lookup to find the host name associated with an IP
     * address. Gets results more often than
     * {@link java.net.InetAddress#getCanonicalHostName()}, but also tries the
     * Inet implementation if reverse DNS does not work. Based on code found at
     * http://www.codingforums.com/showpost.php?p=892349&postcount=5
     *
     * @param ip The IP address to look up
     * @return The host name, if one could be found, or the IP address
     * @throws IllegalArgumentException If the specified IP address is not in
     *           IPv4 format.
     */
    private static String getHostName(final String ip) throws IOException {
      final String[] parts = ip.split("\\.");
      if (parts.length != 4)
        throw new IllegalArgumentException(ip + " does not match IPv4 format");

      for (final String part : parts) {
        try {
          final int x = Integer.parseInt(part);
          if (x < 0 || 255 < x)
            throw new IllegalArgumentException(ip + " does not match IPv4 format");
        }
        catch (final NumberFormatException e) {
          throw new IllegalArgumentException(ip + " does not match IPv4 format");
        }
      }

      String hostName = null;
      DirContext context = null;
      try {
        final Hashtable<String,String> environment = new Hashtable<>();
        environment.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        context = new InitialDirContext(environment);
        final String reverseDnsDomain = parts[3] + "." + parts[2] + "." + parts[1] + "." + parts[0] + ".in-addr.arpa";
        final Attributes attrs = context.getAttributes(reverseDnsDomain, new String[] {"PTR"});
        for (final NamingEnumeration<? extends Attribute> enumeration = attrs.getAll(); enumeration.hasMoreElements();) {
          final Attribute attr = enumeration.next();
          final String attrId = attr.getID();
          for (final Enumeration<?> values = attr.getAll(); values.hasMoreElements();) {
            hostName = values.nextElement().toString();
            if ("PTR".equals(attrId)) {
              final int len = hostName.length();
              if (len > 1 && hostName.charAt(len - 1) == '.')
                hostName = hostName.substring(0, len - 1);

              break;
            }
          }
        }
      }
      catch (final NamingException e) {
      }
      finally {
        if (context != null) {
          try {
            context.close();
          }
          catch (final NamingException e) {
          }
        }
      }

      return hostName != null ? hostName : InetAddress.getByName(ip).getCanonicalHostName();
    }

    static {
      final Logger logger = LoggerFactory.getLogger(Sender.class);
      if (debug = logger.isDebugEnabled() || logger.isTraceEnabled())
        System.setProperty("javax.net.debug", "ssl,handshake");
    }

    private final Protocol protocol;
    private final String host;
    private final int port;
    private final Properties defaultProperties;

    /**
     * Creates a new {@link Sender} with the specified parameters.
     *
     * @param protocol The mail transport {@link Protocol}.
     * @param host The transport server host.
     * @param port The transport server port.
     * @throws IllegalArgumentException If {@code protocol} or {@code host} are
     *           null, or if {@code port} is outside the range (1, 65535).
     */
    public Sender(final Protocol protocol, final String host, final int port) {
      this.protocol = protocol;
      if (protocol == null)
        throw new IllegalArgumentException("protocol == null");

      this.host = host;
      if (host == null)
        throw new IllegalArgumentException("host == null");

      this.port = port;
      if (port < 1 || 65535 < port)
        throw new IllegalArgumentException("port [" + port + "] <> (1, 65535)");

      final String protocolString = this.protocol.toString().toLowerCase();

      this.defaultProperties = new Properties();
      if (debug)
        defaultProperties.put("mail.debug", "true");

      defaultProperties.put("mail.transport.protocol", protocolString);

      if (debug)
        defaultProperties.put("mail." + protocolString + ".debug", "true");

      defaultProperties.put("mail." + protocolString + ".host", host);
      try {
        defaultProperties.put("mail." + protocolString + ".localhost", getHostName(getExternalIP()));
      }
      catch (final IOException e) {
        defaultProperties.put("mail." + protocolString + ".localhost", "localhost.localdomain");
      }

      defaultProperties.put("mail." + protocolString + ".port", port);

      defaultProperties.put("mail." + protocolString + ".quitwait", "false");

      defaultProperties.put("mail." + protocolString + ".ssl.trust", "*");
      defaultProperties.put("mail." + protocolString + ".starttls.enable", "true");

      if (this.protocol == Protocol.SMTPS) {
        defaultProperties.put("mail." + protocolString + ".ssl.enable", "true");
        defaultProperties.put("mail." + protocolString + ".ssl.protocols", "SSLv3 TLSv1");
        defaultProperties.put("mail." + protocolString + ".socketFactory.class", SSLSocketFactory.class.getName());
        defaultProperties.put("mail." + protocolString + ".socketFactory.port", port);
        defaultProperties.put("mail." + protocolString + ".socketFactory.fallback", "false");
      }
    }

    /**
     * Send a message with the provided parameters.
     *
     * @param authentication The {@link PasswordAuthentication} for the transport
     *          server.
     * @param subject The subject of the message.
     * @param content The {@link MimeContent} content.
     * @param from The "from" {@link InternetAddress}.
     * @param to A string array of "to" email addresses.
     * @throws MessagingException If a transport error has occurred.
     */
    public void send(final PasswordAuthentication authentication, final String subject, final MimeContent content, final InternetAddress from, final String ... to) throws MessagingException {
      send(authentication, new Message(subject, content, from, to, null, null));
    }

    /**
     * Send a message with the provided parameters.
     *
     * @param authentication The {@link PasswordAuthentication} for the transport
     *          server.
     * @param subject The subject of the message.
     * @param content The {@link MimeContent} content.
     * @param from The "from" {@link InternetAddress}.
     * @param to A string array of "to" email addresses.
     * @param cc A string array of "cc" email addresses.
     * @param bcc A string array of "bcc" email addresses.
     * @throws MessagingException If a transport error has occurred.
     */
    public void send(final PasswordAuthentication authentication, final String subject, final MimeContent content, final InternetAddress from, final String[] to, final String[] cc, final String[] bcc) throws MessagingException {
      send(authentication, new Message(subject, content, from, to, cc, bcc));
    }

    /**
     * Send {@code messages} with the provided {@link PasswordAuthentication}.
     *
     * @param authentication The {@link PasswordAuthentication} for the
     *          transport server.
     * @param messages The array of {@link Message} messages.
     * @throws MessagingException If a transport error has occurred.
     * @throws NullPointerException If {@code messages}, or any of its members
     *           is null.
     */
    public void send(final PasswordAuthentication authentication, final Message ... messages) throws MessagingException {
      final String protocolString = protocol.toString().toLowerCase();
      final Properties properties = new Properties(defaultProperties);
      final Session session;
      if (authentication != null) {
        properties.put("mail." + protocolString + ".auth", "true");
        // the following 2 lines were causing "Relaying denied. Proper
        // authentication required." messages from sendmail
        // properties.put("mail." + protocolString + ".ehlo", "false");
        // properties.put("mail." + protocolString + ".user", credentials.getUsername());

        session = Session.getInstance(properties, new Authenticator() {
          @Override
          protected PasswordAuthentication getPasswordAuthentication() {
            return authentication;
          }
        });
      }
      else {
        session = Session.getInstance(properties);
      }

      session.setDebug(debug);
      final Transport transport = session.getTransport(protocolString);
      try {
        if (authentication != null)
          transport.connect(host, port, authentication.getUserName(), authentication.getPassword());
        else
          transport.connect(host, port, null, null);

        for (final Message message : messages) {
          logger.debug("Sending Email:\n  subject: " + message.subject + "\n       to: " + Arrays.toString(message.to) + (message.cc != null ? "\n       cc: " + Arrays.toString(message.cc) : "") + (message.bcc != null ? "\n      bcc: " + Arrays.toString(message.bcc) : ""));
          session.getProperties().setProperty("mail." + protocolString + ".from", message.from.getAddress());
          final MimeMessage mimeMessage = new MimeMessage(session);

          try {
            mimeMessage.setFrom(message.from);

            if (message.to != null)
              mimeMessage.setRecipients(MimeMessage.RecipientType.TO, message.to);

            if (message.cc != null)
              mimeMessage.setRecipients(MimeMessage.RecipientType.CC, message.cc);

            if (message.bcc != null)
              mimeMessage.setRecipients(MimeMessage.RecipientType.BCC, message.bcc);

            // Setting the Subject and Content Type
            mimeMessage.setSubject(message.subject);
            mimeMessage.setContent(message.content.getContent(), message.content.getType());

            mimeMessage.saveChanges();
            transport.sendMessage(mimeMessage, mimeMessage.getAllRecipients());
            message.success();
          }
          catch (final MessagingException e) {
            message.failure(e);
            throw e;
          }
        }
      }
      finally {
        transport.close();
      }
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj == this)
        return true;

      if (!(obj instanceof Sender))
        return false;

      final Sender that = (Sender)obj;
      return host.equals(that.host) && protocol == that.protocol && port == that.port;
    }

    @Override
    public int hashCode() {
      int hashCode = 1;
      hashCode = 31 * hashCode + host.hashCode();
      hashCode = 31 * hashCode + protocol.hashCode();
      hashCode = 31 * hashCode + port;
      return hashCode;
    }
  }
}