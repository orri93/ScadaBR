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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import br.org.scadabr.ShouldNeverHappenException;
import br.org.scadabr.l10n.AbstractLocalizer;
import br.org.scadabr.timer.cron.EventRunnable;
import com.serotonin.mango.Common;
import com.serotonin.mango.rt.event.EventInstance;
import com.serotonin.mango.rt.event.type.AuditEventType;
import com.serotonin.mango.rt.event.type.CompoundDetectorEventType;
import com.serotonin.mango.rt.event.type.DataPointEventType;
import com.serotonin.mango.rt.event.type.DataSourceEventType;
import com.serotonin.mango.rt.event.type.EventType;
import com.serotonin.mango.rt.event.type.MaintenanceEventType;
import com.serotonin.mango.rt.event.type.PublisherEventType;
import com.serotonin.mango.rt.event.type.ScheduledEventType;
import com.serotonin.mango.rt.event.type.SystemEventType;
import com.serotonin.mango.vo.UserComment;
import com.serotonin.mango.vo.event.EventHandlerVO;
import com.serotonin.mango.vo.event.EventTypeVO;
import br.org.scadabr.util.SerializationHelper;
import br.org.scadabr.i18n.I18NUtils;
import br.org.scadabr.util.StringUtils;
import br.org.scadabr.vo.event.AlarmLevel;
import br.org.scadabr.vo.event.EventStatus;
import br.org.scadabr.utils.i18n.LocalizableMessage;
import br.org.scadabr.utils.i18n.LocalizableMessageImpl;
import br.org.scadabr.i18n.LocalizableMessageParseException;
import br.org.scadabr.rt.event.type.EventSources;
import br.org.scadabr.utils.ImplementMeException;
import br.org.scadabr.vo.event.type.AuditEventSource;
import br.org.scadabr.vo.event.type.SystemEventSource;
import static com.serotonin.mango.db.dao.BaseDao.charToBool;
import com.serotonin.mango.vo.User;
import java.sql.Connection;
import java.sql.Statement;
import javax.inject.Inject;
import javax.inject.Named;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionCallback;

@Named
public class EventDao extends BaseDao {

    private static final int MAX_PENDING_EVENTS = 100;

    @Inject
    private UserDao userDao;
    
    public EventDao() {
        super();
    }

    public void saveEvent(EventInstance event) {
        if (event.getId() == Common.NEW_ID) {
            insertEvent(event);
        } else {
            updateEvent(event);
        }
    }

    private void insertEvent(final EventInstance event) {

        final int id = doInsert(new PreparedStatementCreator() {

            final static String SQL_INSERT
                    = "insert into events\n"
                    + " (typeId, typeRef1, typeRef2, activeTs, rtnApplicable, rtnTs, rtnCause, alarmLevel, message, ackTs)\n"
                    + "values\n"
                    + " (?,?,?,?,?,?,?,?,?,?)";

            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement ps = con.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS);
                final EventType type = event.getEventType();
                ps.setInt(1, type.getEventSource().mangoDbId);
                switch (type.getEventSource()) {
                    case AUDIT: {
                        final AuditEventType et = (AuditEventType) type;
                        ps.setInt(2, et.getAuditEventType().getId());
                        ps.setInt(3, et.getReferenceId());
                    }
                    break;
                    /*TODO                    case COMPOUND:
                     ps.setInt(2, ((CompoundEventTy) type).getAuditEventType().mangoDbId);
                     ps.setInt(3, type.getReferenceId2());
                     break;
                     */ case DATA_POINT:
                        ps.setInt(2, ((DataPointEventType) type).getReferenceId1());
                        ps.setInt(3, ((DataPointEventType) type).getReferenceId2());
                        break;
                    case DATA_SOURCE:
                        ps.setInt(2, ((DataSourceEventType) type).getReferenceId1());
                        ps.setInt(3, ((DataSourceEventType) type).getReferenceId2());
                        break;
                    case MAINTENANCE:
                        ps.setInt(2, ((MaintenanceEventType) type).getReferenceId1());
                        ps.setInt(3, ((MaintenanceEventType) type).getReferenceId2());
                        break;
                    case PUBLISHER:
                        ps.setInt(2, ((PublisherEventType) type).getReferenceId1());
                        ps.setInt(3, ((PublisherEventType) type).getReferenceId2());
                        break;
                    case SCHEDULED:
                        ps.setInt(2, ((ScheduledEventType) type).getReferenceId1());
                        ps.setInt(3, ((ScheduledEventType) type).getReferenceId2());
                        break;
                    case SYSTEM: {
                        final SystemEventType et = (SystemEventType) type;
                        ps.setInt(2, et.getSystemEventType().getId());
                        ps.setInt(3, et.getReferenceId());
                    }
                    break;
                    default:
                        throw new ImplementMeException();
                }
                ps.setLong(4, event.getFireTimestamp());
                ps.setString(5, boolToChar(event.isStateful()));
                if (!event.isActive()) {
                    ps.setLong(6, event.getInactiveTimestamp());
                    ps.setInt(7, event.getEventState().getId());
                } else {
                    ps.setNull(6, Types.BIGINT);
                    ps.setNull(7, Types.INTEGER);
                }
                ps.setInt(8, event.getAlarmLevel().getId());
                ps.setString(9, I18NUtils.serialize(event.getMessage()));
                if (!event.isAlarm()) {
                    event.setAcknowledgedTimestamp(event.getFireTimestamp());
                    ps.setLong(10, event.getAcknowledgedTimestamp());
                } else {
                    ps.setNull(10, Types.BIGINT);
                }
                return ps;
            }
        });
        event.setId(id);
        event.setEventComments(new LinkedList<UserComment>());
    }

    private static final String EVENT_UPDATE = "update events set rtnTs=?, rtnCause=? where id=?";

    private void updateEvent(EventInstance event) {
        ejt.update(EVENT_UPDATE,
                new Object[]{event.getInactiveTimestamp(), event.getEventState().getId(),
                    event.getId()});
        updateCache(event);
    }

    private static final String EVENT_ACK = "update events set ackTs=?, ackUserId=?, alternateAckSource=? where id=? and ackTs is null";
    private static final String USER_EVENT_ACK = "update userEvents set silenced=? where eventId=?";

    public void ackEvent(int eventId, long time, int userId,
            int alternateAckSource) {
        // Ack the event
        ejt.update(EVENT_ACK, new Object[]{time, userId == 0 ? null : userId,
            alternateAckSource, eventId});
        // Silence the user events
        ejt.update(USER_EVENT_ACK, new Object[]{boolToChar(true), eventId});
        // Clear the cache
        clearCache();
    }

    private static final String USER_EVENTS_INSERT = "insert into userEvents (eventId, userId, silenced) values (?,?,?)";

    public void insertUserEvents(final int eventId,
            final List<Integer> userIds, final boolean alarm) {
        ejt.batchUpdate(USER_EVENTS_INSERT, new BatchPreparedStatementSetter() {
            @Override
            public int getBatchSize() {
                return userIds.size();
            }

            @Override
            public void setValues(PreparedStatement ps, int i)
                    throws SQLException {
                ps.setInt(1, eventId);
                ps.setInt(2, userIds.get(i));
                ps.setString(3, boolToChar(!alarm));
            }
        });

        if (alarm) {
            for (int userId : userIds) {
                removeUserIdFromCache(userId);
            }
        }
    }

    private static final String BASIC_EVENT_SELECT
            = "select e.id, e.typeId, e.typeRef1, e.typeRef2, e.activeTs, e.rtnApplicable, e.rtnTs, e.rtnCause, "
            + "  e.alarmLevel, e.message, e.ackTs, e.ackUserId, u.username, e.alternateAckSource "
            + "from events e "
            + "  left join users u on e.ackUserId = u.id ";

    public List<EventInstance> getActiveEvents() {
        List<EventInstance> results = ejt.query(BASIC_EVENT_SELECT
                + "where e.rtnCause=?",
                new EventInstanceRowMapper(), EventStatus.ACTIVE.getId());
        attachRelationalInfo(results);
        return results;
    }

    private static final String EVENT_SELECT_WITH_USER_DATA = "select e.id, e.typeId, e.typeRef1, e.typeRef2, e.activeTs, e.rtnApplicable, e.rtnTs, e.rtnCause, "
            + "  e.alarmLevel, e.message, e.ackTs, e.ackUserId, u.username, e.alternateAckSource, ue.silenced "
            + "from events e "
            + "  left join users u on e.ackUserId=u.id "
            + "  left join userEvents ue on e.id=ue.eventId ";

    public List<EventInstance> getEventsForDataPoint(int dataPointId, int userId) {
        List<EventInstance> results = ejt.query(EVENT_SELECT_WITH_USER_DATA
                + "where e.typeId=" + EventSources.DATA_POINT.mangoDbId
                + "  and e.typeRef1=? " + "  and ue.userId=? "
                + "order by e.activeTs desc", new Object[]{dataPointId,
                    userId}, new UserEventInstanceRowMapper());
        attachRelationalInfo(results);
        return results;
    }

    public List<EventInstance> getPendingEventsForDataPoint(int dataPointId,
            int userId) {
        // Check the cache
        List<EventInstance> userEvents = getFromCache(userId);
        if (userEvents == null) {
            // This is a potentially long running query, so run it offline.
            userEvents = Collections.emptyList();
            addToCache(userId, userEvents);
            Common.eventCronPool.execute(new UserPendingEventRetriever(userId));
        }

        List<EventInstance> list = null;
        for (EventInstance e : userEvents) {
            if (e.getEventType().getDataPointId() == dataPointId) {
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.add(e);
            }
        }

        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }

    class UserPendingEventRetriever implements EventRunnable {

        private final int userId;

        UserPendingEventRetriever(int userId) {
            this.userId = userId;
        }

        @Override
        public void run() {
            addToCache(
                    userId,
                    getPendingEvents(EventSources.DATA_POINT, -1, userId));
        }
    }

    public List<EventInstance> getPendingEventsForDataSource(int dataSourceId,
            int userId) {
        return getPendingEvents(EventSources.DATA_SOURCE, dataSourceId, userId);
    }

    public List<EventInstance> getPendingEventsForPublisher(int publisherId,
            int userId) {
        return getPendingEvents(EventSources.PUBLISHER, publisherId,
                userId);
    }

    List<EventInstance> getPendingEvents(EventSources eventSource, int typeRef1, int userId) {
        Object[] params;
        StringBuilder sb = new StringBuilder();
        sb.append(EVENT_SELECT_WITH_USER_DATA);
        sb.append("where e.typeId=?");

        if (typeRef1 == -1) {
            params = new Object[]{eventSource.mangoDbId, userId, boolToChar(true)};
        } else {
            sb.append("  and e.typeRef1=?");
            params = new Object[]{eventSource.mangoDbId, typeRef1, userId, boolToChar(true)};
        }
        sb.append("  and ue.userId=? ");
        sb.append("  and (e.ackTs is null or (e.rtnApplicable=? and e.rtnTs is null and e.alarmLevel > 0)) ");
        sb.append("order by e.activeTs desc");

        List<EventInstance> results = ejt.query(sb.toString(), params,
                new UserEventInstanceRowMapper());
        attachRelationalInfo(results);
        return results;
    }

    public List<EventInstance> getPendingEvents(final User user) {
        return getPendingEvents(user.getId());
    }

    @Deprecated
    public List<EventInstance> getPendingEvents(final int userId) {
        List<EventInstance> results = ejt.query(new PreparedStatementCreator() {

            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement ps = con.prepareCall(EVENT_SELECT_WITH_USER_DATA
                        + "where ue.userId=? and e.ackTs is null order by e.activeTs desc");
                ps.setInt(1, userId);
                ps.setMaxRows(MAX_PENDING_EVENTS);
                return ps;
            }
        }, new UserEventInstanceRowMapper());
        attachRelationalInfo(results);
        return results;
    }

    public EventInstance getEventInstance(int eventId) {
        return ejt.queryForObject(BASIC_EVENT_SELECT + "where e.id=?",
                new EventInstanceRowMapper(), eventId);
    }

    public static class EventInstanceRowMapper implements
            RowMapper<EventInstance> {

        @Override
        public EventInstance mapRow(ResultSet rs, int rowNum)
                throws SQLException {
            EventType type = createEventType(rs, 2);

            LocalizableMessage message;
            try {
                message = I18NUtils.deserialize(rs.getString(10));
            } catch (LocalizableMessageParseException e) {
                message = new LocalizableMessageImpl("common.default",
                        rs.getString(10));
            }

            EventStatus state;
            if (charToBool(rs.getString(6))) {
                //rtnApplicable == true, so we are stateful;
                state = EventStatus.fromId(rs.getInt(8));
            } else {
                // We do not cate of rs.getInt(8) we assume we are stateless.
                state = EventStatus.STATELESS;
            }

            EventInstance event = new EventInstance(type, rs.getLong(5), AlarmLevel.fromId(rs.getInt(9)), state, rs.getLong(7), message);
            event.setId(rs.getInt(1));
            long ackTs = rs.getLong(11);
            if (!rs.wasNull()) {
                event.setAcknowledgedTimestamp(ackTs);
                event.setAcknowledgedByUserId(rs.getInt(12));
                if (!rs.wasNull()) {
                    event.setAcknowledgedByUsername(rs.getString(13));
                }
                event.setAlternateAckSource(rs.getInt(14));
            }

            return event;
        }
    }

    class UserEventInstanceRowMapper extends EventInstanceRowMapper {

        @Override
        public EventInstance mapRow(ResultSet rs, int rowNum)
                throws SQLException {
            EventInstance event = super.mapRow(rs, rowNum);
            event.setSilenced(charToBool(rs.getString(15)));
            if (!rs.wasNull()) {
                event.setUserNotified(true);
            }
            return event;
        }
    }

    static EventType createEventType(ResultSet rs, int offset)
            throws SQLException {
        switch (EventSources.fromMangoDbId(rs.getInt(offset))) {
            case DATA_POINT:
                return new DataPointEventType(rs.getInt(offset + 1), rs.getInt(offset + 2));
            case DATA_SOURCE:
                return new DataSourceEventType(rs.getInt(offset + 1), rs.getInt(offset + 2));
            case SYSTEM:
                return new SystemEventType(SystemEventSource.fromId(rs.getInt(offset + 1)), rs.getInt(offset + 2));
            case COMPOUND:
                return new CompoundDetectorEventType(rs.getInt(offset + 1));
            case SCHEDULED:
                return new ScheduledEventType(rs.getInt(offset + 1));
            case PUBLISHER:
                return new PublisherEventType(rs.getInt(offset + 1), rs.getInt(offset + 2));
            case AUDIT:
                return new AuditEventType(AuditEventSource.fromId(rs.getInt(offset + 1)), rs.getInt(offset + 2));
            case MAINTENANCE:
                return new MaintenanceEventType(rs.getInt(offset + 1));
            default:
                throw new ShouldNeverHappenException("Unknown eventSource: "
                        + rs.getInt(offset));
        }
    }

    private void attachRelationalInfo(List<EventInstance> list) {
        for (EventInstance e : list) {
            attachRelationalInfo(e);
        }
    }

    private static final String EVENT_COMMENT_SELECT = UserCommentRowMapper.USER_COMMENT_SELECT
            + "where uc.commentType= "
            + UserComment.TYPE_EVENT
            + " and uc.typeKey=? " + "order by uc.ts";

    void attachRelationalInfo(EventInstance event) {
        event.setEventComments(ejt.query(EVENT_COMMENT_SELECT,
                new Object[]{event.getId()}, new UserCommentRowMapper()));
    }

    public EventInstance insertEventComment(int eventId, UserComment comment) {
        userDao.insertUserComment(UserComment.TYPE_EVENT, eventId,
                comment);
        return getEventInstance(eventId);
    }

    public int purgeEventsBefore(final long time) {
        // Find a list of event ids with no remaining acknowledgements pending.
        final JdbcTemplate ejt2 = ejt;
        int count = getTransactionTemplate().execute(
                new TransactionCallback<Integer>() {
                    @Override
                    public Integer doInTransaction(TransactionStatus status) {
                        int count = ejt2
                        .update("delete from events "
                                + "where activeTs<? "
                                + "  and ackTs is not null "
                                + "  and (rtnApplicable=? or (rtnApplicable=? and rtnTs is not null))",
                                new Object[]{time, boolToChar(false),
                                    boolToChar(true)});

                        // Delete orphaned user comments.
                        ejt2.update("delete from userComments where commentType="
                                + UserComment.TYPE_EVENT
                                + "  and typeKey not in (select id from events)");

                        return count;
                    }
                });

        clearCache();

        return count;
    }

    public int getEventCount() {
        return ejt.queryForInt("select count(*) from events");
    }

    public List<EventInstance> search(int eventId, int eventSourceType,
            EventStatus status, int alarmLevel, final String[] keywords, int userId,
            final ResourceBundle bundle, final int from, final int to,
            final Date date) {
        return search(eventId, eventSourceType, status, alarmLevel, keywords,
                -1, -1, userId, bundle, from, to, date);
    }

    public List<EventInstance> search(int eventId, int eventSourceType,
            EventStatus status, int alarmLevel, final String[] keywords,
            long dateFrom, long dateTo, int userId,
            final ResourceBundle bundle, final int from, final int to,
            final Date date) {
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        StringBuilder sql = new StringBuilder();
        sql.append(EVENT_SELECT_WITH_USER_DATA);
        sql.append("where ue.userId=?");
        params.add(userId);

        if (eventId != 0) {
            where.add("e.id=?");
            params.add(eventId);
        }

        if (eventSourceType != -1) {
            where.add("e.typeId=?");
            params.add(eventSourceType);
        }

        if (null != status) {
            switch (status) {
                case ACTIVE:
                    where.add("e.rtnApplicable=? and e.rtnTs is null");
                    params.add(boolToChar(true));
                    break;
/*                case INACTIVE:
                    where.add("e.rtnApplicable=? and e.rtnTs is not null");
                    params.add(boolToChar(true));
                    break;
*/                case STATELESS:
                    where.add("e.rtnApplicable=?");
                    params.add(boolToChar(false));
                    break;
            }
        }

        if (alarmLevel != -1) {
            where.add("e.alarmLevel=?");
            params.add(alarmLevel);
        }

        if (dateFrom != -1) {
            where.add("activeTs>=?");
            params.add(dateFrom);
        }

        if (dateTo != -1) {
            where.add("activeTs<?");
            params.add(dateTo);
        }

        if (!where.isEmpty()) {
            for (String s : where) {
                sql.append(" and ");
                sql.append(s);
            }
        }
        sql.append(" order by e.activeTs desc");

        final List<EventInstance> results = new ArrayList<>();
        final UserEventInstanceRowMapper rowMapper = new UserEventInstanceRowMapper();

        final int[] data = new int[2];

        ejt.query(sql.toString(), params.toArray(), new ResultSetExtractor() {
            @Override
            public Object extractData(ResultSet rs) throws SQLException,
                    DataAccessException {
                int row = 0;
                long dateTs = date == null ? -1 : date.getTime();
                int startRow = -1;

                while (rs.next()) {
                    EventInstance e = rowMapper.mapRow(rs, 0);
                    attachRelationalInfo(e);
                    boolean add = true;

                    if (keywords != null) {
                        // Do the text search. If the instance has a match, put
                        // it in the result. Otherwise ignore.
                        StringBuilder text = new StringBuilder();
                        text.append(AbstractLocalizer.localizeMessage(e.getMessage(), bundle));
                        for (UserComment comment : e.getEventComments()) {
                            text.append(' ').append(comment.getComment());
                        }

                        String[] values = text.toString().split("\\s+");

                        for (String keyword : keywords) {
                            if (keyword.startsWith("-")) {
                                if (StringUtils.globWhiteListMatchIgnoreCase(
                                        values, keyword.substring(1))) {
                                    add = false;
                                    break;
                                }
                            } else {
                                if (!StringUtils.globWhiteListMatchIgnoreCase(
                                        values, keyword)) {
                                    add = false;
                                    break;
                                }
                            }
                        }
                    }

                    if (add) {
                        if (date != null) {
                            if (e.getFireTimestamp() <= dateTs
                                    && results.size() < to - from) {
                                if (startRow == -1) {
                                    startRow = row;
                                }
                                results.add(e);
                            }
                        } else if (row >= from && row < to) {
                            results.add(e);
                        }

                        row++;
                    }
                }

                data[0] = row;
                data[1] = startRow;

                return null;
            }
        });

        searchRowCount = data[0];
        startRow = data[1];

        return results;
    }

    private int searchRowCount;
    private int startRow;

    public int getSearchRowCount() {
        return searchRowCount;
    }

    public int getStartRow() {
        return startRow;
    }

    //
    // /
    // / Event handlers
    // /
    //
    public String generateUniqueXid() {
        return generateUniqueXid(EventHandlerVO.XID_PREFIX, "eventHandlers");
    }

    public boolean isXidUnique(String xid, int excludeId) {
        return isXidUnique(xid, excludeId, "eventHandlers");
    }

    public EventType getEventHandlerType(int handlerId) {
        return ejt.queryForObject(
                "select eventTypeId, eventTypeRef1, eventTypeRef2 from eventHandlers where id=?",
                new Object[]{handlerId}, new RowMapper<EventType>() {
                    @Override
                    public EventType mapRow(ResultSet rs, int rowNum)
                    throws SQLException {
                        return createEventType(rs, 1);
                    }
                });
    }

    public List<EventHandlerVO> getEventHandlers(EventType type) {
        switch (type.getEventSource()) {
            case AUDIT: {
                final AuditEventType et = (AuditEventType) type;
                return getEventHandlers(et.getEventSource(), et.getAuditEventType().getId(), et.getReferenceId());
            }
            case DATA_POINT: {
                final DataPointEventType et = (DataPointEventType) type;
                return getEventHandlers(et.getEventSource(), et.getReferenceId1(), et.getReferenceId2());
            }
            case DATA_SOURCE: {
                final DataSourceEventType et = (DataSourceEventType) type;
                return getEventHandlers(et.getEventSource(), et.getReferenceId1(), et.getReferenceId2());
            }
            case MAINTENANCE: {
                final MaintenanceEventType et = (MaintenanceEventType) type;
                return getEventHandlers(et.getEventSource(), et.getReferenceId1(), et.getReferenceId2());
            }
            case PUBLISHER: {
                final PublisherEventType et = (PublisherEventType) type;
                return getEventHandlers(et.getEventSource(), et.getReferenceId1(), et.getReferenceId2());
            }
            case SCHEDULED: {
                final ScheduledEventType et = (ScheduledEventType) type;
                return getEventHandlers(et.getEventSource(), et.getReferenceId1(), et.getReferenceId2());
            }
            case SYSTEM: {
                final SystemEventType et = (SystemEventType) type;
                return getEventHandlers(et.getEventSource(), et.getSystemEventType().getId(), et.getReferenceId());
            }
            default:
                throw new ShouldNeverHappenException("Eventtype not supported");
        }
    }

    public List<EventHandlerVO> getEventHandlers(EventTypeVO type) {
        return getEventHandlers(type.getEventSource(), type.getTypeRef1(),
                type.getTypeRef2());
    }

    public List<EventHandlerVO> getEventHandlers() {
        return ejt.query(EVENT_HANDLER_SELECT, new EventHandlerRowMapper());
    }

    /**
     * Note: eventHandlers.eventTypeRef2 matches on both the given ref2 and 0.
     * This is to allow a single set of event handlers to be defined for user
     * login events, rather than have to individually define them for each user.
     */
    private List<EventHandlerVO> getEventHandlers(EventSources eventSource, int ref1, int ref2) {
        return ejt.query(EVENT_HANDLER_SELECT
                + "where eventTypeId=? and eventTypeRef1=? "
                + "  and (eventTypeRef2=? or eventTypeRef2=0)", new Object[]{
                    eventSource.mangoDbId, ref1, ref2}, new EventHandlerRowMapper());
    }

    public EventHandlerVO getEventHandler(int eventHandlerId) {
        return ejt.queryForObject(EVENT_HANDLER_SELECT + "where id=?",
                new Object[]{eventHandlerId}, new EventHandlerRowMapper());
    }

    public EventHandlerVO getEventHandler(String xid) {
        try {
            return ejt.queryForObject(EVENT_HANDLER_SELECT + "where xid=?",
                    new EventHandlerRowMapper(), xid);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private static final String EVENT_HANDLER_SELECT = "select id, xid, alias, data from eventHandlers ";

    class EventHandlerRowMapper implements RowMapper<EventHandlerVO> {

        @Override
        public EventHandlerVO mapRow(ResultSet rs, int rowNum)
                throws SQLException {
            EventHandlerVO h = (EventHandlerVO) SerializationHelper
                    .readObject(rs.getBlob(4).getBinaryStream());
            h.setId(rs.getInt(1));
            h.setXid(rs.getString(2));
            h.setAlias(rs.getString(3));
            return h;
        }
    }

    public EventHandlerVO saveEventHandler(final EventType type,
            final EventHandlerVO handler) {
        if (type == null) {
            throw new ShouldNeverHappenException("saveEventHandler EventType is null");
//            return saveEventHandler(0, 0, 0, handler);
        }
        switch (type.getEventSource()) {
            case AUDIT: {
                final AuditEventType et = (AuditEventType) type;
                return saveEventHandler(et.getEventSource(), et.getAuditEventType().getId(), et.getReferenceId(), handler);
            }
            case DATA_POINT: {
                final DataPointEventType et = (DataPointEventType) type;
                return saveEventHandler(et.getEventSource(), et.getReferenceId1(), et.getReferenceId2(), handler);
            }
            case DATA_SOURCE: {
                final DataSourceEventType et = (DataSourceEventType) type;
                return saveEventHandler(et.getEventSource(), et.getReferenceId1(), et.getReferenceId2(), handler);
            }
            case MAINTENANCE: {
                final MaintenanceEventType et = (MaintenanceEventType) type;
                return saveEventHandler(et.getEventSource(), et.getReferenceId1(), et.getReferenceId2(), handler);
            }
            case PUBLISHER: {
                final PublisherEventType et = (PublisherEventType) type;
                return saveEventHandler(et.getEventSource(), et.getReferenceId1(), et.getReferenceId2(), handler);
            }
            case SCHEDULED: {
                final ScheduledEventType et = (ScheduledEventType) type;
                return saveEventHandler(et.getEventSource(), et.getReferenceId1(), et.getReferenceId2(), handler);
            }
            case SYSTEM: {
                final SystemEventType et = (SystemEventType) type;
                return saveEventHandler(et.getEventSource(), et.getSystemEventType().getId(), et.getReferenceId(), handler);
            }
            default:
                throw new ShouldNeverHappenException("Eventtype not supported");
        }
    }

    public EventHandlerVO saveEventHandler(final EventTypeVO type,
            final EventHandlerVO handler) {
        if (type == null) {
            throw new ShouldNeverHappenException("saveEventHandler EventTypeVO is null");
//            return saveEventHandler(0, 0, 0, handler);
        }
        return saveEventHandler(type.getEventSource(), type.getTypeRef1(),
                type.getTypeRef2(), handler);
    }

    private EventHandlerVO saveEventHandler(final EventSources evetnSource,
            final int typeRef1, final int typeRef2, final EventHandlerVO handler) {
        getTransactionTemplate().execute(
                new TransactionCallbackWithoutResult() {
                    @Override
                    protected void doInTransactionWithoutResult(
                            TransactionStatus status) {
                                if (handler.getId() == Common.NEW_ID) {
                                    insertEventHandler(evetnSource, typeRef1, typeRef2,
                                            handler);
                                } else {
                                    updateEventHandler(handler);
                                }
                            }
                });
        return getEventHandler(handler.getId());
    }

    void insertEventHandler(final EventSources eventSource, final int typeRef1, final int typeRef2,
            final EventHandlerVO handler) {
        handler.setId(doInsert(
                new PreparedStatementCreator() {

                    final static String SQL_INSERT = "insert into eventHandlers (xid, alias, eventTypeId, eventTypeRef1, eventTypeRef2, data) values (?,?,?,?,?,?)";

                    @Override
                    public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                        PreparedStatement ps = con.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS);
                        ps.setString(1, handler.getXid());
                        ps.setString(2, handler.getAlias());
                        ps.setInt(3, eventSource.mangoDbId);
                        ps.setInt(4, typeRef1);
                        ps.setInt(5, typeRef2);
                        ps.setBlob(6, SerializationHelper.writeObject(handler));
                        return ps;
                    }
                }));

        AuditEventType.raiseAddedEvent(AuditEventSource.EVENT_HANDLER,
                handler);
    }

    void updateEventHandler(final EventHandlerVO handler) {
        EventHandlerVO old = getEventHandler(handler.getId());

        ejt.update(new PreparedStatementCreator() {

            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                final PreparedStatement ps = con.prepareStatement("update eventHandlers set xid=?, alias=?, data=? where id=?");
                ps.setString(1, handler.getXid());
                ps.setString(2, handler.getAlias());
                ps.setBlob(3, SerializationHelper.writeObject(handler));
                ps.setInt(4, handler.getId());
                return ps;
            }
        });

        AuditEventType.raiseChangedEvent(AuditEventSource.EVENT_HANDLER,
                old, handler);
    }

    public void deleteEventHandler(final int handlerId) {
        EventHandlerVO handler = getEventHandler(handlerId);
        ejt.update("delete from eventHandlers where id=?",
                new Object[]{handlerId});
        AuditEventType.raiseDeletedEvent(AuditEventSource.EVENT_HANDLER,
                handler);
    }

    //
    // /
    // / User alarms
    // /
    //
    private static final String SILENCED_SELECT = "select ue.silenced "
            + "from events e " + "  join userEvents ue on e.id=ue.eventId "
            + "where e.id=? " + "  and ue.userId=? " + "  and e.ackTs is null";

    public boolean toggleSilence(int eventId, int userId) {
        String result = ejt.queryForObject(SILENCED_SELECT, new Object[]{eventId, userId}, String.class);
        if (result == null) {
            return true;
        }

        boolean silenced = !charToBool(result);
        ejt.update(
                "update userEvents set silenced=? where eventId=? and userId=?",
                new Object[]{boolToChar(silenced), eventId, userId});
        return silenced;
    }

    public int getHighestUnsilencedAlarmLevel(int userId) {
        return ejt.queryForInt("select max(e.alarmLevel) from userEvents u "
                + "  join events e on u.eventId=e.id "
                + "where u.silenced=? and u.userId=?", new Object[]{
                    boolToChar(false), userId});
    }

    //
    // /
    // / Pending event caching
    // /
    //
    static class PendingEventCacheEntry {

        private final List<EventInstance> list;
        private final long createTime;

        public PendingEventCacheEntry(List<EventInstance> list) {
            this.list = list;
            createTime = System.currentTimeMillis();
        }

        public List<EventInstance> getList() {
            return list;
        }

        public boolean hasExpired() {
            return System.currentTimeMillis() - createTime > CACHE_TTL;
        }
    }

    private static Map<Integer, PendingEventCacheEntry> pendingEventCache = new ConcurrentHashMap<>();

    private static final long CACHE_TTL = 300000; // 5 minutes

    public static List<EventInstance> getFromCache(int userId) {
        PendingEventCacheEntry entry = pendingEventCache.get(userId);
        if (entry == null) {
            return null;
        }
        if (entry.hasExpired()) {
            pendingEventCache.remove(userId);
            return null;
        }
        return entry.getList();
    }

    public static void addToCache(int userId, List<EventInstance> list) {
        pendingEventCache.put(userId, new PendingEventCacheEntry(list));
    }

    public static void updateCache(EventInstance event) {
        if (event.isAlarm()
                && event.getEventType().getEventSource() == EventSources.DATA_POINT) {
            pendingEventCache.clear();
        }
    }

    public static void removeUserIdFromCache(int userId) {
        pendingEventCache.remove(userId);
    }

    public static void clearCache() {
        pendingEventCache.clear();
    }

}
