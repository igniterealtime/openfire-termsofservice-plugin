
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
package org.igniterealtime.openfire.plugin.termsofservice.http;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.igniterealtime.openfire.plugin.termsofservice.TermsOfServicePlugin;
import org.igniterealtime.openfire.plugin.termsofservice.document.MarkdownRenderer;
import org.igniterealtime.openfire.plugin.termsofservice.document.TosDocument;
import org.igniterealtime.openfire.plugin.termsofservice.document.TosDocumentService;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Serves the current terms of service publicly and without authentication, at whatever path this servlet is
 * mounted at (see {@link org.igniterealtime.openfire.plugin.termsofservice.UrlUtil#DEFAULT_PATH}).
 *
 * The representation served is negotiated from the request's {@code Accept} header: {@code text/html},
 * {@code text/markdown}, or {@code text/plain}, each derived on the fly from the single Markdown source held by
 * {@link TosDocumentService} (see {@link MarkdownRenderer}). A request with no usable preference (no
 * {@code Accept} header, or a wildcard {@code Accept} header) is served HTML, since a human clicking the link in a browser is
 * the most common case this endpoint exists for.
 */
public class TosServlet extends HttpServlet
{
    private enum Representation
    {
        HTML("text/html"),
        MARKDOWN("text/markdown"),
        PLAIN("text/plain");

        private final String mediaType;

        Representation(@Nonnull final String mediaType)
        {
            this.mediaType = mediaType;
        }
    }

    private TosDocumentService documentService = null;
    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Override
    public void init() throws ServletException
    {
        final TermsOfServicePlugin plugin = (TermsOfServicePlugin) XMPPServer.getInstance()
            .getPluginManager()
            .getPluginByName("Terms of Service")
            .orElseThrow();

        documentService = plugin.getDocumentService();
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException
    {
        if (!TermsOfServicePlugin.ENABLED.getValue()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        final Optional<TosDocument> current = documentService.getCurrent();
        if (current.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        final TosDocument document = current.get();

        final Representation representation = negotiate(req);
        if (representation == null) {
            resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Supported representations: text/html, text/markdown, text/plain");
            return;
        }

        // Conditional GET: the current document's activation time doesn't change while it stays current, so a
        // client (or an XMPP client caching what it showed a user before) can avoid re-downloading it.
        final long lastModified = document.getActivatedAt() != null ? document.getActivatedAt().getTime() : document.getUpdatedAt().getTime();

        final long ifModifiedSince = req.getDateHeader("If-Modified-Since");
        if (ifModifiedSince >= 0 && (lastModified / 1000) <= (ifModifiedSince / 1000)) {
            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        final String body;
        switch (representation) {
            case HTML -> body = wrapAsHtmlDocument(document, renderer.toHtml(document.getMarkdownBody()));
            case MARKDOWN -> body = document.getMarkdownBody();
            case PLAIN -> body = renderer.toPlainText(document.getMarkdownBody());
            default -> throw new IllegalStateException("Unreachable: " + representation);
        }

        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentType(representation.mediaType);
        resp.setDateHeader("Last-Modified", lastModified);
        resp.setHeader("X-Tos-Version", document.getVersion());
        resp.setHeader("Cache-Control", "public, max-age=300");
        resp.setHeader("Vary", "Accept");
        resp.getWriter().write(body);
    }

    /**
     * Wraps the provided Terms of Service (ToS) document and its rendered body into a complete HTML document structure.
     *
     * @param document the Terms of Service document that provides metadata such as the version, which is used in the generated HTML title; must not be null.
     * @param renderedBody the HTML content to be embedded within the body of the HTML document; must not be null.
     * @return a string representing an HTML document that includes the provided ToS metadata and the rendered body content.
     */
    @Nonnull
    private static String wrapAsHtmlDocument(@Nonnull final TosDocument document, @Nonnull final String renderedBody)
    {
        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\"/>\n" +
            "<title>Terms of Service (" + StringUtils.escapeHTMLTags(document.getVersion()) + ")</title>\n" +
            "</head>\n<body>\n" + renderedBody + "\n</body>\n</html>\n";
    }

    /**
     * Negotiates the response representation from the request's {@code Accept} header.
     *
     * This intentionally supports only the concrete media types served by this servlet and {@code * / *}.
     * Requests with no {@code Accept} header, or with only a wildcard preference, are served as HTML.
     *
     * @param request the HTTP request.
     * @return the representation to serve, or null if none of the supported representations are acceptable.
     */
    @Nullable
    private static Representation negotiate(@Nonnull final HttpServletRequest request)
    {
        final String accept = request.getHeader(HttpHeader.ACCEPT.asString());

        if (accept == null || accept.isBlank()) {
            return Representation.HTML;
        }

        final HttpFields fields = HttpFields.from(new HttpField(HttpHeader.ACCEPT, accept));

        for (final String mediaRange : fields.getQualityCSV(HttpHeader.ACCEPT))
        {
            if ("*/*".equals(mediaRange)) {
                return Representation.HTML;
            }

            for (final Representation representation : Representation.values())
            {
                if (representation.mediaType.equalsIgnoreCase(mediaRange)) {
                    return representation;
                }
            }
        }

        return null;
    }
}
