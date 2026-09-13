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
import java.util.List;
import java.util.Optional;

/**
 * Records and queries which accounts have accepted which versions of the terms of service.
 */
public interface TosAcceptanceService
{
    /**
     * Whether the given account has accepted the given version, through any mechanism.
     *
     * @param username the account (cannot be null).
     * @param version the version identifier to check (cannot be null).
     * @return true if an acceptance of this exact version by this account is on record.
     */
    boolean hasAccepted(@Nonnull String username, @Nonnull String version);

    /**
     * Records that an account has accepted a version of the terms of service.
     *
     * Calling this a second time for the same account and version is a harmless no-op: acceptance of a given
     * version, once recorded, does not need to be recorded again.
     *
     * @param username the accepting account (cannot be null).
     * @param version the version identifier being accepted (cannot be null).
     * @param mechanism a label identifying how the acceptance was obtained (cannot be null); use
     *                  {@link AcceptanceMechanism#label()} for a mechanism this plugin ships, or any other
     *                  distinctive string for one it does not.
     * @return the record that is now on file for this account and version (never null).
     */
    @Nonnull
    TosAcceptanceRecord recordAcceptance(@Nonnull String username, @Nonnull String version, @Nonnull String mechanism);

    /**
     * The most recent acceptance on record for the given account, if any, regardless of which version it was for.
     *
     * This is what the per-user admin page shows: not necessarily an acceptance of the currently-applicable
     * version, but whatever the account most recently accepted.
     *
     * @param username the account (cannot be null).
     * @return the most recent acceptance, or empty if the account has never accepted any version (never null).
     */
    @Nonnull
    Optional<TosAcceptanceRecord> getLatestAcceptance(@Nonnull String username);

    /**
     * Every acceptance on record for the given account, most recent first.
     *
     * @param username the account (cannot be null).
     * @return an immutable list of acceptance records (never null, possibly empty).
     */
    @Nonnull
    List<TosAcceptanceRecord> getAcceptanceHistory(@Nonnull String username);
}
