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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Date;
import java.util.Objects;

/**
 * One version of the terms of service.
 *
 * The body is stored once, as Markdown, rather than once per output format: {@link org.igniterealtime.openfire.plugin.termsofservice.document.MarkdownRenderer}
 * derives HTML and plain text from it on demand. This avoids the three representations drifting apart, at the cost of
 * the plain-text and HTML renderings being only as good as a generic Markdown converter can make them for arbitrary
 * legal text; an administrator who needs tighter control over one specific rendering is not well served by this design,
 * but for the common case of prose with headings, paragraphs, lists and links, it is a reasonable trade.
 */
public final class TosDocument
{
    private final String version;
    private final TosDocumentStatus status;
    private final String markdownBody;
    private final String createdBy;
    private final Date createdAt;
    private final String updatedBy;
    private final Date updatedAt;
    private final Date activatedAt;
    private final Date replacedAt;

    public TosDocument(@Nonnull final String version, @Nonnull final TosDocumentStatus status,
                        @Nonnull final String markdownBody, @Nonnull final String createdBy, @Nonnull final Date createdAt,
                        @Nonnull final String updatedBy, @Nonnull final Date updatedAt,
                        @Nullable final Date activatedAt, @Nullable final Date replacedAt)
    {
        this.version = Objects.requireNonNull(version);
        this.status = Objects.requireNonNull(status);
        this.markdownBody = Objects.requireNonNull(markdownBody);
        this.createdBy = Objects.requireNonNull(createdBy);
        this.createdAt = new Date(Objects.requireNonNull(createdAt).getTime());
        this.updatedBy = Objects.requireNonNull(updatedBy);
        this.updatedAt = new Date(Objects.requireNonNull(updatedAt).getTime());
        this.activatedAt = activatedAt == null ? null : new Date(activatedAt.getTime());
        this.replacedAt = replacedAt == null ? null : new Date(replacedAt.getTime());
    }

    /**
     * The opaque version identifier, as used on the wire by the SASL2 task (its {@code <terms version='...'>} and
     * {@code <accept version='...'>} attributes) and as the primary key of this document.
     *
     * @return the version identifier (never null).
     */
    @Nonnull
    public String getVersion()
    {
        return version;
    }

    @Nonnull
    public TosDocumentStatus getStatus()
    {
        return status;
    }

    /**
     * The terms of service, as Markdown source. See {@link MarkdownRenderer} to obtain HTML or plain text.
     *
     * @return the Markdown source (never null).
     */
    @Nonnull
    public String getMarkdownBody()
    {
        return markdownBody;
    }

    @Nonnull
    public String getCreatedBy()
    {
        return createdBy;
    }

    @Nonnull
    public Date getCreatedAt()
    {
        return new Date(createdAt.getTime());
    }

    @Nonnull
    public String getUpdatedBy()
    {
        return updatedBy;
    }

    @Nonnull
    public Date getUpdatedAt()
    {
        return new Date(updatedAt.getTime());
    }

    /**
     * When this document became {@link TosDocumentStatus#CURRENT}.
     *
     * @return the activation time, or null if this document has never been activated (still {@code DRAFT}).
     */
    @Nullable
    public Date getActivatedAt()
    {
        return activatedAt == null ? null : new Date(activatedAt.getTime());
    }

    /**
     * When this document stopped being {@link TosDocumentStatus#CURRENT} because a later version was activated.
     *
     * @return the replacement time, or null if this document was never replaced (still {@code DRAFT} or
     *         {@code CURRENT}).
     */
    @Nullable
    public Date getReplacedAt()
    {
        return replacedAt == null ? null : new Date(replacedAt.getTime());
    }
}
