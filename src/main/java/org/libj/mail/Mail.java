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
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;
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
 * The {@link Mail} class is used to send email via a SMTP(S) server. This implementation supports a wide configuration requirement
 * set as could be expected by SMPT(S) servers.
 */
public final class Mail {
  private static final Logger logger = LoggerFactory.getLogger(Mail.class);

  private Mail() {
  }

  /**
   * Class representing a email message.
   */
  public static class Message {
    private static InternetAddress[] toInternetAddress(final String ... emailAddrs) throws AddressException {
      if (emailAddrs == null)
        return null;

      final InternetAddress[] addresses = new InternetAddress[emailAddrs.length];
      for (int i = 0, i$ = emailAddrs.length; i < i$; ++i) // [A]
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
     * Creates a new {@link Message} with the supplied parameters.
     *
     * @param subject The subject of the message.
     * @param content The {@link MimeContent} content.
     * @param from The "from" {@link InternetAddress}.
     * @param to A string array of "to" email addresses.
     * @param cc A string array of "cc" email addresses.
     * @param bcc A string array of "bcc" email addresses.
     * @throws AddressException If the parse of an email address failed.
     * @throws IllegalArgumentException If {@code subject}, {@code content}, {@code from}, or each of {@code to}, {@code cc}, and
     *           {@code bcc} are null.
     */
    public Message(final String subject, final MimeContent content, final InternetAddress from, final String[] to, final String[] cc, final String[] bcc) throws AddressException {
      this(subject, content, from, toInternetAddress(to), toInternetAddress(cc), toInternetAddress(bcc));
    }

    /**
     * Creates a new {@link Message} with the supplied parameters.
     *
     * @param subject The subject of the message.
     * @param content The {@link MimeContent} content.
     * @param from The "from" {@link InternetAddress}.
     * @param to A {@link InternetAddress} array of "to" addresses.
     * @param cc A {@link InternetAddress} array of "cc" addresses.
     * @param bcc A {@link InternetAddress} array of "bcc" addresses.
     * @throws NullPointerException If {@code subject}, {@code content}, or {@code from} is null.
     * @throws IllegalArgumentException If each of {@code to}, {@code cc}, and {@code bcc} are null.
     */
    public Message(final String subject, final MimeContent content, final InternetAddress from, final InternetAddress[] to, final InternetAddress[] cc, final InternetAddress[] bcc) {
      this.subject = Objects.requireNonNull(subject, "subject is null");
      this.content = Objects.requireNonNull(content, "content is null");
      this.from = Objects.requireNonNull(from, "from is null");
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
  public static class Dispatch {
    private static final String[] ptr = {"PTR"};

    public static class Builder {
      private final String host;
      private final int port;

      private boolean ssl;
      private boolean tls;

      private int connectionTimeoutMs = -1;
      private int readTimeoutMs = -1;
      private int writeTimeoutMs = -1;

      private Map<String,String> properties;
      private boolean debug;

      /**
       * Creates a new {@link Builder} with the specified {@code host} and {@code port}.
       *
       * @param host The SMTP server to connect to.
       * @param port The SMTP server port to connect to.
       * @throws NullPointerException If {@code host} is null.
       * @throws IllegalArgumentException If {@code port} is outside the range of [1, 65535].
       */
      public Builder(final String host, final int port) {
        this.host = Objects.requireNonNull(host, "host is null");
        this.port = port;
        if (port < 1 || 65535 < port)
          throw new IllegalArgumentException("port [" + port + "] <> (1, 65535)");
      }

      /**
       * If set to {@code true}, use SSL to connect to host.
       *
       * @param enabled Whether SSL is to be enabled.
       * @return {@code this} {@link Builder}.
       */
      public Builder withSsl(final boolean enabled) {
        this.ssl = enabled;
        return this;
      }

      /**
       * If set to {@code true}, use TLS to connect to host.
       *
       * @param enabled Whether TLS is to be enabled.
       * @return {@code this} {@link Builder}.
       */
      public Builder withTls(final boolean enabled) {
        this.tls = enabled;
        return this;
      }

      /**
       * Set the socket connection timeout value in milliseconds. This timeout is implemented by {@link java.net.Socket}. Default is
       * infinite timeout.
       *
       * @param timeoutMs The socket connection timeout value in milliseconds.
       * @return {@code this} {@link Builder}.
       */
      public Builder withConnectionTimeout(final int timeoutMs) {
        this.connectionTimeoutMs = timeoutMs;
        return this;
      }

      /**
       * Set the socket read timeout value in milliseconds. This timeout is implemented by {@link java.net.Socket}. Default is infinite
       * timeout.
       *
       * @param timeoutMs The socket read timeout value in milliseconds.
       * @return {@code this} {@link Builder}.
       */
      public Builder withReadTimeout(final int timeoutMs) {
        this.readTimeoutMs = timeoutMs;
        return this;
      }

      /**
       * Set the socket write timeout value in milliseconds. This timeout is implemented by using a
       * {@link java.util.concurrent.ScheduledExecutorService} per connection that schedules a thread to close the socket if the timeout
       * expires. Thus, the overhead of using this timeout is one thread per connection. Default is infinite timeout.
       *
       * @param timeoutMs The socket write timeout value in milliseconds.
       * @return {@code this} {@link Builder}.
       */
      public Builder withWriteTimeout(final int timeoutMs) {
        this.writeTimeoutMs = timeoutMs;
        return this;
      }

      /**
       * Whether debugging is to be outputted from {@link Session}.
       *
       * @param enabled Whether debugging is to be outputted from {@link Session}.
       * @return {@code this} {@link Builder}.
       */
      public Builder withDebug(final boolean enabled) {
        this.debug = enabled;
        return this;
      }

      /**
       * Provide properties to be directly applied to the {@link Session}.
       *
       * @param properties The properties to be directly applied to the {@link Session}.
       * @return {@code this} {@link Builder}.
       * @see <a href= "https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html#properties">Session
       *      Properties</a>
       */
      public Builder withProperties(final Map<String,String> properties) {
        this.properties = properties;
        return this;
      }

      /**
       * Returns a new {@link Dispatch} with the options specified in this {@link Builder}.
       *
       * @return A new {@link Dispatch} with the options specified in this {@link Builder}.
       */
      public Dispatch build() {
        return new Dispatch(host, port, ssl, tls, connectionTimeoutMs, readTimeoutMs, writeTimeoutMs, properties, debug);
      }
    }

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
     * Do a reverse DNS lookup to find the host name associated with an IP address. Gets results more often than
     * {@link InetAddress#getCanonicalHostName()}, but also tries the Inet implementation if reverse DNS does not work. Based on code
     * found at http://www.codingforums.com/showpost.php?p=892349&postcount=5
     *
     * @return The host name, if one could be found, or the IP address
     */
    private static String getHostName() {
      try {
        final String ip = getExternalIP();
        final String[] parts = ip.split("\\.");
        if (parts.length != 4)
          throw new IllegalArgumentException(ip + " does not match IPv4 format");

        for (final String part : parts) { // [A]
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
          final Attributes attrs = context.getAttributes(reverseDnsDomain, ptr);
          for (final NamingEnumeration<? extends Attribute> enumeration = attrs.getAll(); enumeration.hasMoreElements();) { // [E]
            final Attribute attr = enumeration.next();
            final String attrId = attr.getID();
            for (final Enumeration<?> values = attr.getAll(); values.hasMoreElements();) { // [E]
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
      catch (final IOException e) {
        return "localhost.localdomain";
      }
    }

    private final String host;
    private final int port;
    private final HashMap<String,String> defaultProperties = new HashMap<>();

    private final String protocol;
    private final boolean debug;

    private Dispatch(final String host, final int port, final boolean ssl, final boolean tls, final int connectionTimeoutMs, final int readTimeoutMs, final int writeTimeoutMs, final Map<String,String> properties, final boolean debug) {
      this.host = host;
      this.port = port;
      if (properties != null)
        defaultProperties.putAll(properties);

      String sslProtocols = null;
      if (ssl) {
        protocol = "smtps";
        sslProtocols = "SSLv3";

        defaultProperties.put("mail." + protocol + ".ssl.enable", "true");
        defaultProperties.put("mail." + protocol + ".socketFactory.class", SSLSocketFactory.class.getName());
        defaultProperties.put("mail." + protocol + ".socketFactory.port", String.valueOf(port));
        defaultProperties.put("mail." + protocol + ".socketFactory.fallback", "false");
      }
      else {
        protocol = "smtp";
      }

      if (tls) {
        if (sslProtocols != null)
          sslProtocols += " TLSv1.2";
        else
          sslProtocols = "TLSv1.2";

        defaultProperties.put("mail." + protocol + ".starttls.enable", "true");
        defaultProperties.put("mail." + protocol + ".starttls.required", "true");
      }

      if (sslProtocols != null)
        defaultProperties.put("mail." + protocol + ".ssl.protocols", sslProtocols);

      defaultProperties.put("mail.transport.protocol", protocol);
      defaultProperties.put("mail." + protocol + ".host", host);
      defaultProperties.put("mail." + protocol + ".localhost", getHostName());
      defaultProperties.put("mail." + protocol + ".port", String.valueOf(port));
      defaultProperties.put("mail." + protocol + ".quitwait", "false");
      defaultProperties.put("mail." + protocol + ".ssl.trust", "*");

      if (connectionTimeoutMs != -1)
        defaultProperties.put("mail." + protocol + ".connectiontimeout", String.valueOf(connectionTimeoutMs));

      if (readTimeoutMs != -1)
        defaultProperties.put("mail." + protocol + ".timeout", String.valueOf(readTimeoutMs));

      if (writeTimeoutMs != -1)
        defaultProperties.put("mail." + protocol + ".writetimeout", String.valueOf(writeTimeoutMs));

      if (this.debug = debug) {
        defaultProperties.put("mail.debug", "true");
        defaultProperties.put("mail." + protocol + ".debug", "true");
      }
    }

    /**
     * Send a message with the provided parameters.
     *
     * @param authentication The {@link PasswordAuthentication} for the transport server.
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
     * @param authentication The {@link PasswordAuthentication} for the transport server.
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
     * @param authentication The {@link PasswordAuthentication} for the transport server (can be null).
     * @param message The {@linkplain Message message} to send.
     * @return The {@link MimeMessage#getMessageID() messageID} of the sent message.
     * @throws MessagingException If a transport error has occurred.
     * @throws NullPointerException If {@code message} is null.
     */
    public String send(final PasswordAuthentication authentication, final Message message) throws MessagingException {
      final Properties properties = new Properties();
      properties.putAll(defaultProperties);

      final Session session;
      if (authentication != null) {
        properties.put("mail." + protocol + ".auth", "true");
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

      if (debug) {
        session.setDebug(debug);
        properties.list(System.err);
      }

      try (final Transport transport = session.getTransport(protocol)) {
        if (authentication != null)
          transport.connect(host, port, authentication.getUserName(), authentication.getPassword());
        else
          transport.connect(host, port, null, null);

        if (logger.isDebugEnabled()) { logger.debug("Sending Email:\n  subject: " + message.subject + "\n       to: " + Arrays.toString(message.to) + (message.cc != null ? "\n       cc: " + Arrays.toString(message.cc) : "") + (message.bcc != null ? "\n      bcc: " + Arrays.toString(message.bcc) : "")); }

        session.getProperties().setProperty("mail." + protocol + ".from", message.from.getAddress());
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
          return mimeMessage.getMessageID();
        }
        catch (final MessagingException e) {
          message.failure(e);
          throw e;
        }
      }
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj == this)
        return true;

      if (!(obj instanceof Dispatch))
        return false;

      final Dispatch that = (Dispatch)obj;
      return defaultProperties.equals(that.defaultProperties);
    }

    @Override
    public int hashCode() {
      return defaultProperties.hashCode();
    }
  }
}