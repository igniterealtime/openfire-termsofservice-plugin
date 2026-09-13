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
 * Database backed {@link TosAcceptanceService}, storing rows in the {@code ofTosAcceptance} table (see the
 * {@code database/} scripts shipped with this plugin).
 *
 * The table's primary key is {@code (username, version)}: an account can only ever have one acceptance record
 * per version, which is exactly the fact this class needs to check and is what makes
 * {@link #recordAcceptance(String, String, String)} idempotent. A duplicate insert (the same account accepting
 * the same version twice, for example via a retried request) is treated as a harmless race rather than an error:
 * whichever insert lost is simply followed by a lookup of the row the winner created.
 */
public class DefaultTosAcceptanceService implements TosAcceptanceService
{
    private static final Logger Log = LoggerFactory.getLogger(DefaultTosAcceptanceService.class);

    private static final String HAS_ACCEPTED = "SELECT 1 FROM ofTosAcceptance WHERE username=? AND version=?";

    private static final String INSERT ="INSERT INTO ofTosAcceptance (username, version, mechanism, acceptedAt) VALUES (?,?,?,?)";

    private static final String SELECT_ONE = "SELECT username, version, mechanism, acceptedAt FROM ofTosAcceptance WHERE username=? AND version=?";

    private static final String SELECT_HISTORY = "SELECT username, version, mechanism, acceptedAt FROM ofTosAcceptance WHERE username=? ORDER BY acceptedAt DESC";

    @Override
    public boolean hasAccepted(@Nonnull final String username, @Nonnull final String version)
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(HAS_ACCEPTED);
            pstmt.setString(1, username);
            pstmt.setString(2, version);
            rs = pstmt.executeQuery();
            return rs.next();
        } catch (final SQLException e) {
            Log.error("Unable to determine whether user '{}' accepted terms version '{}'.", username, version, e);
            // A database error must not be interpreted as "accepted": that would silently bypass the requirement
            // this plugin exists to enforce. See Sasl2TosTaskProvider for how a caller is expected to treat this.
            return false;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    @Nonnull
    @Override
    public TosAcceptanceRecord recordAcceptance(@Nonnull final String username, @Nonnull final String version, @Nonnull final String mechanism)
    {
        final Optional<TosAcceptanceRecord> existing = selectOne(username, version);
        if (existing.isPresent()) {
            return existing.get();
        }

        final Date acceptedAt = new Date();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT);
            pstmt.setString(1, username);
            pstmt.setString(2, version);
            pstmt.setString(3, mechanism);
            pstmt.setTimestamp(4, new Timestamp(acceptedAt.getTime()));
            pstmt.executeUpdate();
            return new TosAcceptanceRecord(username, version, mechanism, acceptedAt);
        } catch (final SQLException e) {
            // Most likely a primary-key collision from a concurrent insert of the same (username, version): treat
            // that as the acceptance having already been recorded, rather than as a failure.
            final Optional<TosAcceptanceRecord> raceWinner = selectOne(username, version);
            if (raceWinner.isPresent()) {
                Log.debug("Acceptance of version '{}' by user '{}' was already recorded by a concurrent request.", version, username, e);
                return raceWinner.get();
            }
            Log.error("Unable to record acceptance of terms version '{}' by user '{}'.", version, username, e);
            throw new RuntimeException("Unable to record terms-of-service acceptance.", e);
        } finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Nonnull
    @Override
    public Optional<TosAcceptanceRecord> getLatestAcceptance(@Nonnull final String username)
    {
        final List<TosAcceptanceRecord> history = getAcceptanceHistory(username);
        return history.isEmpty() ? Optional.empty() : Optional.of(history.get(0));
    }

    @Nonnull
    @Override
    public List<TosAcceptanceRecord> getAcceptanceHistory(@Nonnull final String username)
    {
        final List<TosAcceptanceRecord> result = new ArrayList<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_HISTORY);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (final SQLException e) {
            Log.error("Unable to retrieve terms-of-service acceptance history for user '{}'.", username, e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return result;
    }

    @Nonnull
    private Optional<TosAcceptanceRecord> selectOne(@Nonnull final String username, @Nonnull final String version)
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_ONE);
            pstmt.setString(1, username);
            pstmt.setString(2, version);
            rs = pstmt.executeQuery();
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        } catch (final SQLException e) {
            Log.error("Unable to look up terms-of-service acceptance of version '{}' by user '{}'.", version, username, e);
            return Optional.empty();
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    @Nonnull
    private static TosAcceptanceRecord map(@Nonnull final ResultSet rs) throws SQLException
    {
        return new TosAcceptanceRecord(
            rs.getString("username"),
            rs.getString("version"),
            rs.getString("mechanism"),
            new Date(rs.getTimestamp("acceptedAt").getTime())
        );
    }
}
