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
package org.igniterealtime.openfire.plugin.termsofservice;

import org.jivesoftware.openfire.XMPPServer;

import javax.annotation.Nonnull;

/**
 * Computes the URL that is handed to peers for reading the terms of service, the same way the HTTP File Upload
 * plugin computes the URLs it advertises for uploads and downloads: a sensible default, overridable piece by piece
 * by an administrator whose deployment sits behind a reverse proxy or otherwise needs the externally-visible
 * address to differ from how Openfire itself is configured.
 *
 * <h2>Why there is no automatic port/protocol detection</h2>
 * The HTTP File Upload plugin's own settings page computes its default announced address from Openfire's HTTP
 * bind configuration. This class deliberately does not attempt the equivalent: the specific API historically used
 * for that (methods on {@code HttpBindManager} such as a one-time-observed {@code getHttpBindSecurePort()}) has
 * already changed incompatibly at least once across Openfire versions. Rather than depend on that surface with
 * uncertain confidence, the protocol, port and context root here default to fixed, documented values, and an
 * administrator is expected to confirm or override them for their environment on the settings page — exactly the
 * fallback the HTTP File Upload settings page itself recommends when its own auto-detected default is wrong (see
 * its "This can be corrected by setting the proper values below" notice).
 */
public class UrlUtil
{
    /** Where the servlet is actually mounted; see {@link org.igniterealtime.openfire.plugin.termsofservice.http.TosServlet}. */
    public static final String DEFAULT_PATH = "/termsofservice/terms";

    private UrlUtil()
    {
    }

    /**
     * The URL to hand to a peer for reading the current terms of service.
     *
     * @return the announced URL (never null).
     */
    @Nonnull
    public static String getAnnouncedTermsUrl()
    {
        String protocol = TermsOfServicePlugin.ANNOUNCED_PROTOCOL.getValue();
        if (isBlank(protocol)) {
            protocol = "https";
        }

        String host = TermsOfServicePlugin.ANNOUNCED_HOST.getValue();
        if (isBlank(host)) {
            // A stable, long-established API (already used elsewhere in Openfire's own SASL2 code), unlike the
            // HTTP-bind-specific getters mentioned above.
            host = XMPPServer.getInstance().getServerInfo().getHostname();
        }

        final Integer port = TermsOfServicePlugin.ANNOUNCED_PORT.getValue();

        String path = TermsOfServicePlugin.ANNOUNCED_CONTEXT_ROOT.getValue();
        if (isBlank(path)) {
            path = DEFAULT_PATH;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        final StringBuilder url = new StringBuilder();
        url.append(protocol).append("://").append(host);
        if (port != null) {
            url.append(':').append(port);
        }
        url.append(path);
        return url.toString();
    }

    private static boolean isBlank(@javax.annotation.Nullable final String value)
    {
        return value == null || value.isBlank();
    }
}
