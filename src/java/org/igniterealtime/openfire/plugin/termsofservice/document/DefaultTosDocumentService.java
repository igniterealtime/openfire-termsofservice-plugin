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

import org.jivesoftware.database.DbConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Database-backed {@link TosDocumentService}, storing rows in the {@code ofTosDocument} table (see the
 * {@code database/} scripts shipped with this plugin).
 */
public class DefaultTosDocumentService implements TosDocumentService
{
    private static final Logger Log = LoggerFactory.getLogger(DefaultTosDocumentService.class);

    private static final String SELECT_ALL =
        "SELECT version, status, body, createdBy, createdAt, updatedBy, updatedAt, activatedAt, replacedAt " +
        "FROM ofTosDocument ORDER BY createdAt DESC";

    private static final String SELECT_CURRENT =
        "SELECT version, status, body, createdBy, createdAt, updatedBy, updatedAt, activatedAt, replacedAt " +
        "FROM ofTosDocument WHERE status='CURRENT'";

    private static final String SELECT_ONE =
        "SELECT version, status, body, createdBy, createdAt, updatedBy, updatedAt, activatedAt, replacedAt " +
        "FROM ofTosDocument WHERE version=?";

    private static final String INSERT =
        "INSERT INTO ofTosDocument (version, status, body, createdBy, createdAt, updatedBy, updatedAt) " +
        "VALUES (?,?,?,?,?,?,?)";

    private static final String UPDATE_BODY =
        "UPDATE ofTosDocument SET body=?, updatedBy=?, updatedAt=? WHERE version=? AND status='DRAFT'";

    private static final String ACTIVATE =
        "UPDATE ofTosDocument SET status='CURRENT', updatedBy=?, updatedAt=?, activatedAt=? WHERE version=? AND status='DRAFT'";

    private static final String RETIRE_CURRENT =
        "UPDATE ofTosDocument SET status='RETIRED', updatedBy=?, updatedAt=?, replacedAt=? WHERE status='CURRENT'";

    @Nonnull
    @Override
    public List<TosDocument> getAll()
    {
        final List<TosDocument> result = new ArrayList<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_ALL);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (final SQLException e) {
            Log.error("Unable to retrieve the list of terms-of-service document versions.", e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return result;
    }

    @Nonnull
    @Override
    public Optional<TosDocument> getCurrent()
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_CURRENT);
            rs = pstmt.executeQuery();
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        } catch (final SQLException e) {
            Log.error("Unable to retrieve the current terms-of-service document.", e);
            return Optional.empty();
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    @Nonnull
    @Override
    public Optional<TosDocument> getByVersion(@Nonnull final String version)
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_ONE);
            pstmt.setString(1, version);
            rs = pstmt.executeQuery();
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        } catch (final SQLException e) {
            Log.error("Unable to retrieve terms-of-service document version '{}'.", version, e);
            return Optional.empty();
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    @Nonnull
    @Override
    public TosDocument createDraft(@Nonnull final String version, @Nonnull final String markdownBody, @Nonnull final String adminUsername)
    {
        if (getByVersion(version).isPresent()) {
            throw new IllegalArgumentException("A terms-of-service document with version '" + version + "' already exists.");
        }
        final Date now = new Date();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT);
            pstmt.setString(1, version);
            pstmt.setString(2, TosDocumentStatus.DRAFT.name());
            pstmt.setString(3, markdownBody);
            pstmt.setString(4, adminUsername);
            pstmt.setTimestamp(5, new Timestamp(now.getTime()));
            pstmt.setString(6, adminUsername);
            pstmt.setTimestamp(7, new Timestamp(now.getTime()));
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            Log.error("Unable to create terms-of-service draft version '{}'.", version, e);
            throw new RuntimeException("Unable to create the draft.", e);
        } finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return new TosDocument(version, TosDocumentStatus.DRAFT, markdownBody, adminUsername, now, adminUsername, now, null, null);
    }

    @Nonnull
    @Override
    public TosDocument updateDraft(@Nonnull final String version, @Nonnull final String newMarkdownBody, @Nonnull final String adminUsername)
    {
        final TosDocument existing = requireStatus(version, TosDocumentStatus.DRAFT, "updated");
        final Date now = new Date();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_BODY);
            pstmt.setString(1, newMarkdownBody);
            pstmt.setString(2, adminUsername);
            pstmt.setTimestamp(3, new Timestamp(now.getTime()));
            pstmt.setString(4, version);
            final int updated = pstmt.executeUpdate();
            if (updated == 0) {
                // Lost a race with something that changed the status between requireStatus() and here.
                throw new IllegalStateException("Terms-of-service document '" + version + "' is no longer a draft.");
            }
        } catch (final SQLException e) {
            Log.error("Unable to update terms-of-service draft version '{}'.", version, e);
            throw new RuntimeException("Unable to update the draft.", e);
        } finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return new TosDocument(version, TosDocumentStatus.DRAFT, newMarkdownBody, existing.getCreatedBy(), existing.getCreatedAt(), adminUsername, now, null, null);
    }

    @Nonnull
    @Override
    public TosDocument activate(@Nonnull final String version, @Nonnull final String adminUsername)
    {
        requireStatus(version, TosDocumentStatus.DRAFT, "activated");
        final Date now = new Date();

        Connection con = null;
        PreparedStatement retirePstmt = null;
        PreparedStatement activatePstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();

            retirePstmt = con.prepareStatement(RETIRE_CURRENT);
            retirePstmt.setString(1, adminUsername);
            retirePstmt.setTimestamp(2, new Timestamp(now.getTime()));
            retirePstmt.setTimestamp(3, new Timestamp(now.getTime()));
            retirePstmt.executeUpdate(); // 0 rows if nothing was current yet; that's fine.

            activatePstmt = con.prepareStatement(ACTIVATE);
            activatePstmt.setString(1, adminUsername);
            activatePstmt.setTimestamp(2, new Timestamp(now.getTime()));
            activatePstmt.setTimestamp(3, new Timestamp(now.getTime()));
            activatePstmt.setString(4, version);
            final int activated = activatePstmt.executeUpdate();
            if (activated == 0) {
                abortTransaction = true;
                throw new IllegalStateException("Terms-of-service document '" + version + "' is no longer a draft.");
            }

            con.commit();
        } catch (final SQLException e) {
            Log.error("Unable to activate terms-of-service document version '{}'.", version, e);
            abortTransaction = true;
            throw new RuntimeException("Unable to activate the terms-of-service document.", e);
        } finally {
            DbConnectionManager.closeStatement(retirePstmt);
            DbConnectionManager.closeStatement(activatePstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
        return getByVersion(version).orElseThrow(() -> new IllegalStateException("Document vanished immediately after being activated."));
    }

    @Nonnull
    @Override
    public TosDocument copyToNewDraft(@Nonnull final String existingVersion, @Nonnull final String newVersion, @Nonnull final String adminUsername)
    {
        final TosDocument existing = getByVersion(existingVersion)
            .orElseThrow(() -> new IllegalArgumentException("No terms-of-service document with version '" + existingVersion + "' exists."));
        return createDraft(newVersion, existing.getMarkdownBody(), adminUsername);
    }

    @Nonnull
    private TosDocument requireStatus(@Nonnull final String version, @Nonnull final TosDocumentStatus required, @Nonnull final String action)
    {
        final TosDocument document = getByVersion(version)
            .orElseThrow(() -> new IllegalArgumentException("No terms-of-service document with version '" + version + "' exists."));
        if (document.getStatus() != required) {
            throw new IllegalStateException("Terms-of-service document '" + version + "' cannot be " + action + ": it is " + document.getStatus() + ", not " + required + ".");
        }
        return document;
    }

    @Nonnull
    private static TosDocument map(@Nonnull final ResultSet rs) throws SQLException
    {
        final Timestamp activatedAt = rs.getTimestamp("activatedAt");
        final Timestamp replacedAt = rs.getTimestamp("replacedAt");
        return new TosDocument(
            rs.getString("version"),
            TosDocumentStatus.valueOf(rs.getString("status")),
            rs.getString("body"),
            rs.getString("createdBy"),
            new Date(rs.getTimestamp("createdAt").getTime()),
            rs.getString("updatedBy"),
            new Date(rs.getTimestamp("updatedAt").getTime()),
            activatedAt == null ? null : new Date(activatedAt.getTime()),
            replacedAt == null ? null : new Date(replacedAt.getTime())
        );
    }
}
