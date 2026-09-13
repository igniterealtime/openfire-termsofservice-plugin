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

/**
 * The channel through which an acceptance was obtained.
 *
 * This is deliberately not the only possible value: {@link TosAcceptanceService#recordAcceptance(String, String, String)}
 * takes a plain {@code String}, not this enum, so that a mechanism added later (a web login flow, an email
 * confirmation link, an import from an external system) needs no change to the acceptance table or to this
 * interface. It just calls {@code recordAcceptance} with a new label of its own choosing. This enum exists only
 * to give the mechanisms shipped with this plugin a single, typo-proof place to name themselves.
 */
public enum AcceptanceMechanism
{
    /**
     * Recorded by {@link org.igniterealtime.openfire.plugin.termsofservice.sasl2.Sasl2TosTaskProvider}.
     */
    SASL2_TASK,

    /**
     * Recorded by an administrator on a user's behalf, via the per-user admin console page.
     */
    ADMIN;

    /**
     * The value to pass to, or expect from, {@link TosAcceptanceService}. Kept as an explicit method (rather than
     * relying on callers to use {@link #name()} directly) so that a future change to how these constants are
     * named does not silently change what is stored in, or matched against, the database.
     *
     * @return the wire/storage value for this mechanism (never null).
     */
    public String label()
    {
        return name();
    }
}
