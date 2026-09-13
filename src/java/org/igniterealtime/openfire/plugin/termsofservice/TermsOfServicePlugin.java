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

import org.eclipse.jetty.ee8.webapp.WebAppContext;
import org.igniterealtime.openfire.plugin.termsofservice.acceptance.DefaultTosAcceptanceService;
import org.igniterealtime.openfire.plugin.termsofservice.acceptance.TosAcceptanceService;
import org.igniterealtime.openfire.plugin.termsofservice.document.DefaultTosDocumentService;
import org.igniterealtime.openfire.plugin.termsofservice.document.TosDocumentService;
import org.igniterealtime.openfire.plugin.termsofservice.http.TosServlet;
import org.igniterealtime.openfire.plugin.termsofservice.sasl2.Sasl2TosTaskProvider;
import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.sasl.task.Sasl2TaskManager;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;

/**
 * Requires users to accept the current terms of service, and serves the terms themselves over a public HTTP
 * endpoint. This plugin owns the terms-of-service document repository ({@link TosDocumentService}) and the
 * record of who has accepted what ({@link TosAcceptanceService}); how acceptance is obtained is deliberately
 * kept separate from those two concerns.
 *
 * At the time this plugin was written, the only shipped way to obtain acceptance is
 * {@link Sasl2TosTaskProvider}, a SASL2 task implementing the "SASL2 Terms of Service Task"
 * specification (a ProtoXEP; the XEP number is not yet assigned). It is registered here the same way any future
 * mechanism would be: as one more caller of {@link TosAcceptanceService}, with no special standing of its own.
 * See that interface's Javadoc for what's involved in adding another mechanism later.
 *
 * <h2>Jetty integration point</h2>
 * {@link #initializePlugin(PluginManager, File)} mounts {@link TosServlet} via {@code
 * HttpBindManager.getInstance().addJettyHandler(Handler)}, the same mechanism the HTTP File Upload plugin uses to
 * expose its own public download endpoint. The {@code org.eclipse.jetty.ee8.servlet} package used below for
 * {@code ServletContextHandler}/{@code ServletHolder} matches Ignite Realtime's own documentation of Openfire
 * 5.0.0's Jetty 12 upgrade, which confirms {@code org.eclipse.jetty.ee8} as the group used throughout this
 * server's "ee8" servlet environment (the same one {@code javax.servlet.*}, used by {@link TosServlet}, belongs
 * to) — this is a closer match than the previous revision of this class had, though the exact class location was
 * not seen verbatim in that documentation, only the package family it belongs to.
 */
public class TermsOfServicePlugin implements Plugin
{
    private static final Logger Log = LoggerFactory.getLogger(TermsOfServicePlugin.class);

    /** Master switch. Off by default: installing this plugin must not silently start blocking logins. */
    public static final SystemProperty<Boolean> ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("plugin.termsofservice.enabled")
        .setPlugin("Terms of Service")
        .setDynamic(true)
        .setDefaultValue(false)
        .build();

    /**
     * Represents the protocol (e.g., "http" or "https") announced to peers for accessing the Terms of Service page.
     */
    public static final SystemProperty<String> ANNOUNCED_PROTOCOL = SystemProperty.Builder.ofType(String.class)
        .setKey("plugin.termsofservice.announcedProtocol")
        .setPlugin("Terms of Service")
        .setDynamic(true)
        .setDefaultValue("https")
        .build();

    /**
     * Represents the host announced to peers for accessing the Terms of Service page.
     */
    public static final SystemProperty<String> ANNOUNCED_HOST = SystemProperty.Builder.ofType(String.class)
        .setKey("plugin.termsofservice.announcedHost")
        .setPlugin("Terms of Service")
        .setDynamic(true)
        .setDefaultValue(XMPPServer.getInstance().getServerInfo().getHostname())
        .build();

    /**
     * Represents the port announced to peers for accessing the Terms of Service page.
     */
    public static final SystemProperty<Integer> ANNOUNCED_PORT = SystemProperty.Builder.ofType(Integer.class)
        .setKey("plugin.termsofservice.announcedPort")
        .setPlugin("Terms of Service")
        .setDynamic(true)
        .setDefaultValue(HttpBindManager.HTTP_BIND_SECURE_PORT.getValue())
        .build();

    /**
     * Represents the context root announced to peers for accessing the Terms of Service page.
     */
    public static final SystemProperty<String> ANNOUNCED_CONTEXT_ROOT = SystemProperty.Builder.ofType(String.class)
        .setKey("plugin.termsofservice.announcedContextRoot")
        .setPlugin("Terms of Service")
        .setDynamic(true)
        .setDefaultValue("/termsofservice")
        .build();

    /**
     * The context root of the URL under which the public web endpoint for TOS is exposed.
     */
    public static final String CONTEXT_ROOT = "termsofservice";

    private final String[] publicResources = new String[]
        {
            CONTEXT_ROOT
        };

    private WebAppContext context = null;

    private TosAcceptanceService acceptanceService;
    private TosDocumentService documentService;
    private Sasl2TosTaskProvider taskProvider;

    @Override
    public void initializePlugin(@Nonnull final PluginManager manager, @Nonnull final File pluginDirectory)
    {
        acceptanceService = new DefaultTosAcceptanceService();
        documentService = new DefaultTosDocumentService();

        taskProvider = new Sasl2TosTaskProvider(documentService, acceptanceService, ENABLED::getValue);
        Sasl2TaskManager.getInstance().register(taskProvider);

        for ( final String publicResource : publicResources )
        {
            AuthCheckFilter.addExclude( publicResource );
        }

        // Add the Webchat sources to the same context as the one that's providing the BOSH interface.
        context = new WebAppContext( null, pluginDirectory.getPath() + File.separator + "classes/", "/" + CONTEXT_ROOT );
        context.setClassLoader( this.getClass().getClassLoader() );

        HttpBindManager.getInstance().addJettyHandler( context );

        Log.info("Terms-of-service acceptance plugin initialized (enabled={}). Manage versions and settings from the admin console.", ENABLED.getValue());
    }

    @Override
    public void destroyPlugin()
    {
        if (taskProvider != null) {
            Sasl2TaskManager.getInstance().unregister(taskProvider);
            taskProvider = null;
        }

        if ( context != null )
        {
            HttpBindManager.getInstance().removeJettyHandler( context );
            context.destroy();
            context = null;
        }

        for ( final String publicResource : publicResources )
        {
            AuthCheckFilter.removeExclude( publicResource );
        }

        acceptanceService = null;
        documentService = null;
    }

    /**
     * The service backing this plugin's acceptance records. Exposed so that a future acceptance mechanism —
     * shipped as part of this plugin or as a separate one — can record and query acceptances without this plugin
     * needing to know about it; see {@link TosAcceptanceService} for the extension contract.
     *
     * @return the acceptance service, or null if the plugin has not been initialized (or has been destroyed).
     */
    public TosAcceptanceService getAcceptanceService()
    {
        return acceptanceService;
    }

    /**
     * The service backing this plugin's document repository.
     *
     * @return the document service, or null if the plugin has not been initialized (or has been destroyed).
     */
    public TosDocumentService getDocumentService()
    {
        return documentService;
    }
}
