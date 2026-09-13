/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.igniterealtime.openfire.plugin.termsofservice.document;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.annotation.Nonnull;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Renders a {@link TosDocument}'s Markdown source as HTML or as plain text.
 *
 * HTML rendering is delegated to <a href="https://github.com/commonmark/commonmark-java">commonmark-java</a>, a
 * conformant CommonMark implementation. Plain-text rendering is a best-effort, hand-rolled stripping of common
 * Markdown syntax: it is not a full CommonMark-to-text renderer, and does not attempt to handle every construct
 * the spec allows (nested block quotes, HTML blocks, and reference-style links are not specially handled, for
 * example) — but it comfortably covers headings, paragraphs, emphasis, links, and lists, which is what
 * terms-of-service prose is made of in practice. If this proves too rough for a given document, that document can
 * always be read via the HTML or Markdown representation instead.
 */
public class MarkdownRenderer
{
    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder().build();

    // Order matters: links before emphasis, so an emphasised link's asterisks aren't consumed first.
    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}\\s*(.*?)\\s*#*\\s*$");
    private static final Pattern LINK = Pattern.compile("\\[([^]]*)]\\(([^)]*)\\)");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^]]*)]\\(([^)]*)\\)");
    private static final Pattern BOLD_ITALIC = Pattern.compile("(\\*\\*\\*|___)(.+?)\\1");
    private static final Pattern BOLD = Pattern.compile("(\\*\\*|__)(.+?)\\1");
    private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)|(?<!_)_(?!_)(.+?)(?<!_)_(?!_)");
    private static final Pattern CODE_SPAN = Pattern.compile("`([^`]*)`");
    private static final Pattern BLOCKQUOTE = Pattern.compile("(?m)^>\\s?");
    private static final Pattern UNORDERED_LIST_MARKER = Pattern.compile("(?m)^(\\s*)[-*+]\\s+");
    private static final Pattern ORDERED_LIST_MARKER = Pattern.compile("(?m)^(\\s*)\\d+\\.\\s+");
    private static final Pattern THEMATIC_BREAK = Pattern.compile("(?m)^\\s*([-*_])\\s*(\\1\\s*){2,}$");
    private static final Pattern MULTIPLE_BLANK_LINES = Pattern.compile("\\n{3,}");

    /**
     * Renders the given Markdown source as HTML.
     *
     * @param markdown the Markdown source (cannot be null).
     * @return the rendered HTML fragment (never null).
     */
    @Nonnull
    public String toHtml(@Nonnull final String markdown)
    {
        final Node document = PARSER.parse(markdown);
        return HTML_RENDERER.render(document);
    }

    /**
     * Renders the given Markdown source as plain text, by stripping common Markdown syntax rather than by parsing
     * a document tree. See the class documentation for the limits of this approach.
     *
     * @param markdown the Markdown source (cannot be null).
     * @return a best-effort plain-text rendering (never null).
     */
    @Nonnull
    public String toPlainText(@Nonnull final String markdown)
    {
        String text = markdown;

        text = IMAGE.matcher(text).replaceAll(mr -> altOrEmpty(mr) + " (" + mr.group(2) + ")");
        text = LINK.matcher(text).replaceAll(mr -> mr.group(1) + " (" + mr.group(2) + ")");
        text = HEADING.matcher(text).replaceAll(mr -> mr.group(1));
        text = THEMATIC_BREAK.matcher(text).replaceAll("");
        text = BOLD_ITALIC.matcher(text).replaceAll(mr -> mr.group(2));
        text = BOLD.matcher(text).replaceAll(mr -> mr.group(2));
        text = ITALIC.matcher(text).replaceAll(mr -> mr.group(1) != null ? mr.group(1) : mr.group(2));
        text = CODE_SPAN.matcher(text).replaceAll(mr -> mr.group(1));
        text = BLOCKQUOTE.matcher(text).replaceAll("");
        text = UNORDERED_LIST_MARKER.matcher(text).replaceAll(mr -> mr.group(1) + "- ");
        text = ORDERED_LIST_MARKER.matcher(text).replaceAll(mr -> mr.group(1) + "- ");
        text = MULTIPLE_BLANK_LINES.matcher(text).replaceAll("\n\n");

        return text.trim();
    }

    @Nonnull
    private static String altOrEmpty(@Nonnull final MatchResult mr)
    {
        final String alt = mr.group(1);
        return alt == null ? "" : alt;
    }
}
