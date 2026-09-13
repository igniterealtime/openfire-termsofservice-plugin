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
import java.util.List;
import java.util.Optional;

/**
 * Manages the versioned repository of terms-of-service texts.
 *
 * <h2>Lifecycle</h2>
 * A version is created as a {@link TosDocumentStatus#DRAFT} ({@link #createDraft(String, String, String)}), can be
 * freely edited while still a draft ({@link #updateDraft(String, String, String)}), and is then activated
 * ({@link #activate(String, String)}), which does two things atomically: the previously {@code CURRENT} document
 * (if any) becomes {@link TosDocumentStatus#RETIRED}, and the activated document becomes {@code CURRENT}. Neither
 * a {@code CURRENT} nor a {@code RETIRED} document can be edited again; {@link #copyToNewDraft(String, String, String)}
 * exists for the common case of wanting to start the next version from the previous one's text.
 *
 * <h2>Concurrency</h2>
 * At most one document is {@code CURRENT} at any time. Implementations are responsible for enforcing that
 * invariant even under concurrent activation attempts.
 */
public interface TosDocumentService
{
    /**
     * Every document version on file, most recently created first, for the management listing page.
     *
     * @return an immutable list of documents (never null, possibly empty).
     */
    @Nonnull
    List<TosDocument> getAll();

    /**
     * The document currently being enforced, if any.
     *
     * @return the current document, or empty if no version has ever been activated (never null).
     */
    @Nonnull
    Optional<TosDocument> getCurrent();

    /**
     * Looks up one document by its version identifier.
     *
     * @param version the version identifier (cannot be null).
     * @return the matching document, or empty if no document has this version identifier (never null).
     */
    @Nonnull
    Optional<TosDocument> getByVersion(@Nonnull String version);

    /**
     * Creates a new draft.
     *
     * @param version the version identifier for the new draft; must not already be in use (cannot be null).
     * @param markdownBody the initial Markdown source (cannot be null, may be empty).
     * @param adminUsername the administrator creating this draft, recorded as both creator and last editor
     *                       (cannot be null).
     * @return the newly created document, with status {@link TosDocumentStatus#DRAFT} (never null).
     * @throws IllegalArgumentException if {@code version} is already in use.
     */
    @Nonnull
    TosDocument createDraft(@Nonnull String version, @Nonnull String markdownBody, @Nonnull String adminUsername);

    /**
     * Replaces the Markdown source of an existing draft.
     *
     * @param version the version identifier of the draft to update (cannot be null).
     * @param newMarkdownBody the replacement Markdown source (cannot be null, may be empty).
     * @param adminUsername the administrator making this edit, recorded as last editor (cannot be null).
     * @return the updated document (never null).
     * @throws IllegalArgumentException if no document has this version identifier.
     * @throws IllegalStateException if the document is not a {@link TosDocumentStatus#DRAFT}.
     */
    @Nonnull
    TosDocument updateDraft(@Nonnull String version, @Nonnull String newMarkdownBody, @Nonnull String adminUsername);

    /**
     * Activates a draft: it becomes {@link TosDocumentStatus#CURRENT}, and whichever document was previously
     * current (if any) becomes {@link TosDocumentStatus#RETIRED}.
     *
     * @param version the version identifier of the draft to activate (cannot be null).
     * @param adminUsername the administrator performing the activation, recorded as last editor of both the
     *                       newly-current and newly-retired documents (cannot be null).
     * @return the now-current document (never null).
     * @throws IllegalArgumentException if no document has this version identifier.
     * @throws IllegalStateException if the document is not a {@link TosDocumentStatus#DRAFT}.
     */
    @Nonnull
    TosDocument activate(@Nonnull String version, @Nonnull String adminUsername);

    /**
     * Creates a new draft whose initial text is a copy of an existing document's text, regardless of the existing
     * document's status. This is the supported way to start the next version: since a {@code CURRENT} or
     * {@code RETIRED} document can no longer be edited directly, copying it forward is how its text is reused.
     *
     * @param existingVersion the version identifier to copy the text from (cannot be null).
     * @param newVersion the version identifier for the new draft; must not already be in use (cannot be null).
     * @param adminUsername the administrator performing the copy, recorded as creator and last editor of the new
     *                       draft (cannot be null).
     * @return the newly created draft (never null).
     * @throws IllegalArgumentException if {@code existingVersion} does not exist, or if {@code newVersion} is
     *                                   already in use.
     */
    @Nonnull
    TosDocument copyToNewDraft(@Nonnull String existingVersion, @Nonnull String newVersion, @Nonnull String adminUsername);
}
