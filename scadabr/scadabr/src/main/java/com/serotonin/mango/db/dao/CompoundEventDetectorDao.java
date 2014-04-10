/*
 Mango - Open Source M2M - http://mango.serotoninsoftware.com
 Copyright (C) 2006-2011 Serotonin Software Technologies Inc.
 @author Matthew Lohbihler
    
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.serotonin.mango.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import com.serotonin.mango.Common;
import static com.serotonin.mango.db.dao.BaseDao.boolToChar;
import com.serotonin.mango.rt.event.type.AuditEventType;
import com.serotonin.mango.rt.event.type.EventType;
import com.serotonin.mango.vo.event.CompoundEventDetectorVO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

/**
 * @author Matthew Lohbihler
 */
public class CompoundEventDetectorDao extends BaseDao {

    private static final String COMPOUND_EVENT_DETECTOR_SELECT = "select id, xid, name, alarmLevel, returnToNormal, disabled, conditionText from compoundEventDetectors ";

    public String generateUniqueXid() {
        return generateUniqueXid(CompoundEventDetectorVO.XID_PREFIX, "compoundEventDetectors");
    }

    public boolean isXidUnique(String xid, int excludeId) {
        return isXidUnique(xid, excludeId, "compoundEventDetectors");
    }

    public List<CompoundEventDetectorVO> getCompoundEventDetectors() {
        return ejt.query(COMPOUND_EVENT_DETECTOR_SELECT + "order by name", new CompoundEventDetectorRowMapper());
    }

    public CompoundEventDetectorVO getCompoundEventDetector(int id) {
        return ejt.queryForObject(COMPOUND_EVENT_DETECTOR_SELECT + "where id=?", new Object[]{id},
                new CompoundEventDetectorRowMapper());
    }

    public CompoundEventDetectorVO getCompoundEventDetector(String xid) {
        try {
            return ejt.queryForObject(COMPOUND_EVENT_DETECTOR_SELECT + "where xid=?", new CompoundEventDetectorRowMapper(), xid);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    class CompoundEventDetectorRowMapper implements RowMapper<CompoundEventDetectorVO> {

        @Override
        public CompoundEventDetectorVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            CompoundEventDetectorVO ced = new CompoundEventDetectorVO();
            int i = 0;
            ced.setId(rs.getInt(++i));
            ced.setXid(rs.getString(++i));
            ced.setName(rs.getString(++i));
            ced.setAlarmLevel(rs.getInt(++i));
            ced.setReturnToNormal(charToBool(rs.getString(++i)));
            ced.setDisabled(charToBool(rs.getString(++i)));
            ced.setCondition(rs.getString(++i));
            return ced;
        }
    }

    public void saveCompoundEventDetector(final CompoundEventDetectorVO ced) {
        if (ced.getId() == Common.NEW_ID) {
            insertCompoundEventDetector(ced);
        } else {
            updateCompoundEventDetector(ced);
        }
    }

    private void insertCompoundEventDetector(final CompoundEventDetectorVO ced) {
        final int id = doInsert(new PreparedStatementCreator() {

            static final String SQL_INSERT = "insert into compoundEventDetectors (xid, name, alarmLevel, returnToNormal, disabled, conditionText) "
                    + "values (?,?,?,?,?,?)";

            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement ps = con.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, ced.getXid());
                ps.setString(2, ced.getName());
                ps.setInt(3, ced.getAlarmLevel());
                ps.setString(4, boolToChar(ced.isReturnToNormal()));
                ps.setString(5, boolToChar(ced.isDisabled()));
                ps.setString(6, ced.getCondition());
                return ps;
            }
        });
        ced.setId(id);
        AuditEventType.raiseAddedEvent(AuditEventType.TYPE_COMPOUND_EVENT_DETECTOR, ced);
    }

    private static final String COMPOUND_EVENT_DETECTOR_UPDATE = "update compoundEventDetectors set xid=?, name=?, alarmLevel=?, returnToNormal=?, disabled=?, conditionText=? "
            + "where id=?";

    private void updateCompoundEventDetector(CompoundEventDetectorVO ced) {
        CompoundEventDetectorVO old = getCompoundEventDetector(ced.getId());

        ejt.update(COMPOUND_EVENT_DETECTOR_UPDATE, new Object[]{ced.getXid(), ced.getName(), ced.getAlarmLevel(),
            boolToChar(ced.isReturnToNormal()), boolToChar(ced.isDisabled()), ced.getCondition(), ced.getId()});

        AuditEventType.raiseChangedEvent(AuditEventType.TYPE_COMPOUND_EVENT_DETECTOR, old, ced);

    }

    public void deleteCompoundEventDetector(final int compoundEventDetectorId) {
        final JdbcTemplate ejt2 = ejt;
        CompoundEventDetectorVO ced = getCompoundEventDetector(compoundEventDetectorId);
        if (ced != null) {
            getTransactionTemplate().execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    ejt2.update("delete from eventHandlers where eventTypeId=" + EventType.EventSources.COMPOUND
                            + " and eventTypeRef1=?", new Object[]{compoundEventDetectorId});
                    ejt2.update("delete from compoundEventDetectors where id=?",
                            new Object[]{compoundEventDetectorId});
                }
            });

            AuditEventType.raiseDeletedEvent(AuditEventType.TYPE_COMPOUND_EVENT_DETECTOR, ced);
        }
    }
}