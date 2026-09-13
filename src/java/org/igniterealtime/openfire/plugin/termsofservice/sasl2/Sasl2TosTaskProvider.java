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
package org.igniterealtime.openfire.plugin.termsofservice.sasl2;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.igniterealtime.openfire.plugin.termsofservice.UrlUtil;
import org.igniterealtime.openfire.plugin.termsofservice.acceptance.AcceptanceMechanism;
import org.igniterealtime.openfire.plugin.termsofservice.acceptance.TosAcceptanceService;
import org.igniterealtime.openfire.plugin.termsofservice.document.TosDocument;
import org.igniterealtime.openfire.plugin.termsofservice.document.TosDocumentService;
import org.jivesoftware.openfire.sasl.Failure;
import org.jivesoftware.openfire.sasl.SaslFailureException;
import org.jivesoftware.openfire.sasl.task.Sasl2Task;
import org.jivesoftware.openfire.sasl.task.Sasl2TaskContext;
import org.jivesoftware.openfire.sasl.task.Sasl2TaskProvider;
import org.jivesoftware.openfire.sasl.task.Sasl2TaskResult;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The SASL2 task ({@link Sasl2TaskProvider}) that enforces terms-of-service acceptance during authentication,
 * implementing the "SASL2 Terms of Service Task" specification (a ProtoXEP at the time this plugin was
 * written; the XEP number is not yet assigned).
 *
 * This class is one caller of {@link TosAcceptanceService} and {@link TosDocumentService}, not a special one: it
 * holds no state of its own about who has accepted what, or about which versions exist. See
 * {@link TosAcceptanceService} for how a future acceptance mechanism plugs into the same services without
 * touching this class.
 *
 * The exchange is:
 * <pre>{@code
 * S: <continue xmlns='urn:xmpp:sasl:2'>
 *      <tasks><task>TOS-ACCEPT</task></tasks>
 *      <text>The terms of service have changed and must be accepted before you can sign in.</text>
 *    </continue>
 * C: <next xmlns='urn:xmpp:sasl:2' task='TOS-ACCEPT'/>
 * S: <task-data xmlns='urn:xmpp:sasl:2'>
 *      <terms xmlns='urn:xmpp:termsofservice:0' version='2026-01'>https://example.org/tos/terms</terms>
 *    </task-data>
 * C: <task-data xmlns='urn:xmpp:sasl:2'>
 *      <accept xmlns='urn:xmpp:termsofservice:0' version='2026-01'/>
 *    </task-data>
 * S: <success xmlns='urn:xmpp:sasl:2'>...</success>
 * }</pre>
 */
public class Sasl2TosTaskProvider implements Sasl2TaskProvider
{
    public static final String NAMESPACE = "urn:xmpp:termsofservice:0";
    public static final String TASK_NAME = "TOS-ACCEPT";

    private final TosDocumentService documentService;
    private final TosAcceptanceService acceptanceService;
    private final BooleanSupplier enabled;

    /**
     * @param documentService where the currently-applicable version is looked up (cannot be null).
     * @param acceptanceService where prior acceptances are checked and new ones are recorded (cannot be null).
     * @param enabled whether the requirement is switched on at all; consulted on every check, so that disabling it
     *                administratively takes effect for the very next authentication attempt (cannot be null).
     */
    public Sasl2TosTaskProvider(@Nonnull final TosDocumentService documentService,
                                 @Nonnull final TosAcceptanceService acceptanceService,
                                 @Nonnull final BooleanSupplier enabled)
    {
        this.documentService = documentService;
        this.acceptanceService = acceptanceService;
        this.enabled = enabled;
    }

    @Override
    @Nonnull
    public String getIdentifier()
    {
        return "org.igniterealtime.openfire.plugin.termsofservice.sasl2";
    }

    @Override
    @Nonnull
    public Set<String> getTaskNames()
    {
        return Set.of(TASK_NAME);
    }

    @Override
    @Nonnull
    public List<String> getOfferedTasks(@Nonnull final Sasl2TaskContext context)
    {
        if (!enabled.getAsBoolean()) {
            return List.of();
        }
        final Optional<TosDocument> current = documentService.getCurrent();
        if (current.isEmpty()) {
            // Enabled, but no version has ever been activated: there is nothing to enforce yet.
            return List.of();
        }
        final String version = current.get().getVersion();
        if (context.getCompletedTaskNames().contains(TASK_NAME)) {
            return List.of();
        }

        final String username = context.getAuthorizationIdentity();
        final boolean upToDate;
        if (username == null) {
            // Anonymous authentication: there is no account that has recorded an acceptance.
            upToDate = false;
        } else {
            upToDate = acceptanceService.hasAccepted(username, version);
        }
        return upToDate ? List.of() : List.of(TASK_NAME);
    }

    @Override
    @Nonnull
    public Optional<String> getContinueText(@Nonnull final Sasl2TaskContext context)
    {
        return Optional.of("The terms of service have changed and must be accepted before you can sign in.");
    }

    @Override
    @Nonnull
    public Sasl2Task createTask(@Nonnull final String taskName, @Nonnull final Sasl2TaskContext context)
    {
        return new TermsOfServiceTask(context);
    }

    /**
     * The stateful part: one instance per negotiation in which the peer selected this task.
     *
     * The document offered in {@link #begin(Element)} is captured on this instance and reused in
     * {@link #onTaskData(Element)}, rather than looking up "the current document" a second time. Without that, an
     * administrator activating a new version between those two calls could cause a genuine acceptance of the
     * offered version to be rejected as a mismatch, or worse, validated against a version the peer was never
     * actually shown.
     */
    private class TermsOfServiceTask implements Sasl2Task
    {
        private final Sasl2TaskContext context;
        private TosDocument offeredDocument;

        private TermsOfServiceTask(@Nonnull final Sasl2TaskContext context)
        {
            this.context = context;
        }

        @Override
        @Nonnull
        public String getName()
        {
            return TASK_NAME;
        }

        @Override
        @Nonnull
        public Sasl2TaskResult begin(@Nonnull final Element next) throws SaslFailureException
        {
            final TosDocument current = documentService.getCurrent()
                .orElseThrow(() -> new SaslFailureException(Failure.TEMPORARY_AUTH_FAILURE, "No terms of service version is currently configured."));
            this.offeredDocument = current;

            final Element terms = DocumentHelper.createElement(QName.get("terms", NAMESPACE));
            terms.addAttribute("version", current.getVersion());
            terms.setText(UrlUtil.getAnnouncedTermsUrl());
            return Sasl2TaskResult.taskData(terms);
        }

        @Override
        @Nonnull
        public Sasl2TaskResult onTaskData(@Nonnull final Element taskData) throws SaslFailureException
        {
            final Element accept = taskData.element(QName.get("accept", NAMESPACE));
            if (accept == null) {
                throw new SaslFailureException(Failure.NOT_AUTHORIZED, "The terms of service were not accepted.");
            }
            if (!offeredDocument.getVersion().equals(accept.attributeValue("version"))) {
                throw new SaslFailureException(Failure.MALFORMED_REQUEST, "A version of the terms of service was accepted that is not the version that was offered.");
            }
            final String username = context.getAuthorizationIdentity();
            if (username != null) {
                // A non-anonymous session has an account to record the acceptance against, so it is not asked
                // again on a later connection (until the version changes). An anonymous session has no such
                // account: its acceptance covers only this one negotiation, which is why getOfferedTasks() offers
                // this task on every anonymous authentication rather than trying to remember a prior one.
                acceptanceService.recordAcceptance(username, offeredDocument.getVersion(), AcceptanceMechanism.SASL2_TASK.label());
            }
            return Sasl2TaskResult.completed();
        }
    }
}
