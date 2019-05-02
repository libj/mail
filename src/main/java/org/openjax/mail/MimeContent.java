/* Copyright (c) 2016 OpenJAX
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

package org.openjax.mail;

/**
 * The {@code MimeContent} represents message content with an associated mime
 * type.
 */
public class MimeContent {
  private final String content;
  private final String type;

  /**
   * Creates a new {@code MimeContent} with the provided {@code content} and
   * mime {@code type}.
   *
   * @param content The content string.
   * @param type The mime type string.
   */
  public MimeContent(final String content, final String type) {
    this.content = content;
    this.type = type;
  }

  /**
   * Returns the content string.
   *
   * @return The content string.
   */
  public String getContent() {
    return content;
  }

  /**
   * Returns the type string.
   *
   * @return The type string.
   */
  public String getType() {
    return type;
  }
}