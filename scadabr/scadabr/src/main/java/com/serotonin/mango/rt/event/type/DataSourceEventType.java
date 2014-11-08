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
import com.serotonin.mango.db.dao.DataSourceDao;
import com.serotonin.mango.vo.dataSource.DataSourceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

@JsonRemoteEntity
@Configurable
public class DataSourceEventType extends EventType {
    @Autowired
    private DataSourceDao dataSourceDao;

    private int dataSourceId;
    private int dataSourceEventTypeId;
    private AlarmLevel alarmLevel;
    private DuplicateHandling duplicateHandling;

    public DataSourceEventType() {
        // Required for reflection.
    }

    public DataSourceEventType(int dataSourceId, int dataSourceEventTypeId) {
        this(dataSourceId, dataSourceEventTypeId, AlarmLevel.URGENT, DuplicateHandling.IGNORE);
    }

    public DataSourceEventType(int dataSourceId, int dataSourceEventTypeId, AlarmLevel alarmLevel, DuplicateHandling duplicateHandling) {
        this.dataSourceId = dataSourceId;
        this.dataSourceEventTypeId = dataSourceEventTypeId;
        this.alarmLevel = alarmLevel;
        this.duplicateHandling = duplicateHandling;
    }

    @Override
    public EventSources getEventSource() {
        return EventSources.DATA_SOURCE;
    }

    public int getDataSourceEventTypeId() {
        return dataSourceEventTypeId;
    }

    public AlarmLevel getAlarmLevel() {
        return alarmLevel;
    }

    @Override
    public int getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public String toString() {
        return "DataSoureEventType(dataSourceId=" + dataSourceId + ", eventTypeId=" + dataSourceEventTypeId + ")";
    }

    @Override
    public DuplicateHandling getDuplicateHandling() {
        return duplicateHandling;
    }

    public int getReferenceId1() {
        return dataSourceId;
    }

    public int getReferenceId2() {
        return dataSourceEventTypeId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + dataSourceEventTypeId;
        result = prime * result + dataSourceId;
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
        DataSourceEventType other = (DataSourceEventType) obj;
        if (dataSourceEventTypeId != other.dataSourceEventTypeId) {
            return false;
        }
        return dataSourceId == other.dataSourceId;
    }

    //
    // /
    // / Serialization
    // /
    //
    @Override
    public void jsonSerialize(Map<String, Object> map) {
        super.jsonSerialize(map);
        DataSourceVO<?> ds = dataSourceDao.getDataSource(dataSourceId);
        map.put("XID", ds.getXid());
        map.put("dataSourceEventType", ds.getEventCodes().getCode(dataSourceEventTypeId));
    }

    @Override
    public void jsonDeserialize(JsonReader reader, JsonObject json) throws JsonException {
        super.jsonDeserialize(reader, json);
        DataSourceVO<?> ds = getDataSource(json, "XID");
        dataSourceId = ds.getId();
        dataSourceEventTypeId = getInt(json, "dataSourceEventType", ds.getEventCodes());
    }

}
