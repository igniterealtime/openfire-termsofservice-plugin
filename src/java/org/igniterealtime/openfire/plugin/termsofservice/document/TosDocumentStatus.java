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

/**
 * The lifecycle state of one {@link TosDocument}.
 *
 * The lifecycle is one-directional and has no "delete": {@code DRAFT} -&gt; {@code CURRENT} -&gt; {@code RETIRED}.
 * A draft can be edited freely; once activated, its text is frozen (see {@link TosDocument}), so that a
 * previously-accepted version can never retroactively change under the account that accepted it.
 */
public enum TosDocumentStatus
{
    /**
     * Being written. Editable. Never offered to end users and never accepted.
     */
    DRAFT,

    /**
     * The one version currently being enforced. Not editable. At most one document has this status at a time.
     */
    CURRENT,

    /**
     * Was once {@link #CURRENT}, superseded by a later version. Not editable, kept for its acceptance history.
     */
    RETIRED
}
