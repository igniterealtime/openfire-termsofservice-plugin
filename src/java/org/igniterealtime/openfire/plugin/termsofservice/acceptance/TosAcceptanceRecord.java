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
package org.igniterealtime.openfire.plugin.termsofservice.acceptance;

import javax.annotation.Nonnull;
import java.util.Date;
import java.util.Objects;

/**
 * One recorded acceptance of one version of the terms of service by one account.
 *
 * Acceptances are never updated or deleted: a new version being accepted produces a new record, so that
 * {@link TosAcceptanceService#getAcceptanceHistory(String)} is a genuine audit trail rather than a single
 * overwritten "last known" fact.
 */
public final class TosAcceptanceRecord
{
    private final String username;
    private final String version;
    private final String mechanism;
    private final Date acceptedAt;

    public TosAcceptanceRecord(@Nonnull final String username, @Nonnull final String version,
                                @Nonnull final String mechanism, @Nonnull final Date acceptedAt)
    {
        this.username = Objects.requireNonNull(username);
        this.version = Objects.requireNonNull(version);
        this.mechanism = Objects.requireNonNull(mechanism);
        this.acceptedAt = new Date(Objects.requireNonNull(acceptedAt).getTime());
    }

    @Nonnull
    public String getUsername()
    {
        return username;
    }

    /**
     * The version identifier that was accepted, matching {@link org.igniterealtime.openfire.plugin.termsofservice.document.TosDocument#getVersion()}
     * of the document this acceptance applies to.
     *
     * @return the accepted version (never null).
     */
    @Nonnull
    public String getVersion()
    {
        return version;
    }

    /**
     * The mechanism through which this acceptance was obtained (see {@link AcceptanceMechanism}), or a label
     * defined by a mechanism this plugin does not itself ship.
     *
     * @return a mechanism label (never null).
     */
    @Nonnull
    public String getMechanism()
    {
        return mechanism;
    }

    @Nonnull
    public Date getAcceptedAt()
    {
        return new Date(acceptedAt.getTime());
    }
}
