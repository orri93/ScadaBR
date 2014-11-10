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
package com.serotonin.mango.rt.event.type;

import java.util.Map;

import br.org.scadabr.json.JsonException;
import br.org.scadabr.json.JsonObject;
import br.org.scadabr.json.JsonReader;
import br.org.scadabr.json.JsonRemoteEntity;
import br.org.scadabr.rt.event.type.DuplicateHandling;
import br.org.scadabr.rt.event.type.EventSources;
import br.org.scadabr.vo.event.AlarmLevel;
import com.serotonin.mango.db.dao.ScheduledEventDao;
import com.serotonin.mango.vo.event.ScheduledEventVO;

/**
 * @author Matthew Lohbihler
 *
 */
@JsonRemoteEntity
public class ScheduledEventType extends EventType {

    private int scheduleId;
    private DuplicateHandling duplicateHandling = DuplicateHandling.IGNORE;
    private AlarmLevel alarmLevel;
    private boolean stateful;

    public ScheduledEventType() {
        // Required for reflection.
    }

    @Deprecated
    public ScheduledEventType(int scheduleId) {
        this.scheduleId = scheduleId;
        /*
         this.alarmLevel = vo.getAlarmLevel();
         if (!vo.isReturnToNormal()) {
         duplicateHandling = DuplicateHandling.ALLOW;
         }
         */
    }

    public ScheduledEventType(ScheduledEventVO vo) {
        this.scheduleId = vo.getId();
        this.alarmLevel = vo.getAlarmLevel();
        this.stateful = vo.isStateful();
        if (!vo.isStateful()) {
            duplicateHandling = DuplicateHandling.ALLOW;
        }
    }

    @Override
    public EventSources getEventSource() {
        return EventSources.SCHEDULED;
    }

    @Override
    public int getScheduleId() {
        return scheduleId;
    }

    @Override
    public String toString() {
        return "ScheduledEventType(scheduleId=" + scheduleId + ")";
    }

    @Override
    public DuplicateHandling getDuplicateHandling() {
        return duplicateHandling;
    }

    public void setDuplicateHandling(DuplicateHandling duplicateHandling) {
        this.duplicateHandling = duplicateHandling;
    }

    public int getReferenceId1() {
        return scheduleId;
    }

    public int getReferenceId2() {
        return 0;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + scheduleId;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ScheduledEventType other = (ScheduledEventType) obj;
        if (scheduleId != other.scheduleId) {
            return false;
        }
        return true;
    }

    //
    // /
    // / Serialization
    // /
    //
    @Override
    public void jsonSerialize(Map<String, Object> map) {
        super.jsonSerialize(map);
        map.put("XID", ScheduledEventDao.getInstance().getScheduledEvent(scheduleId).getXid());
    }

    @Override
    public void jsonDeserialize(JsonReader reader, JsonObject json) throws JsonException {
        super.jsonDeserialize(reader, json);
        scheduleId = getScheduledEventId(json, "XID");
    }

    @Override
    public AlarmLevel getAlarmLevel() {
        return alarmLevel;
    }

    /**
     * @return the stateful
     */
    @Override
    public boolean isStateful() {
        return stateful;
    }

}
