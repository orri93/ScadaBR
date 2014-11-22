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
package com.serotonin.mango.vo.dataSource.onewire;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.org.scadabr.json.JsonException;
import br.org.scadabr.json.JsonObject;
import br.org.scadabr.json.JsonReader;


import com.serotonin.mango.Common;
import com.serotonin.mango.rt.dataSource.DataSourceRT;
import com.serotonin.mango.rt.dataSource.onewire.OneWireDataSourceRT;
import com.serotonin.mango.rt.event.type.AuditEventType;
import com.serotonin.mango.util.ExportCodes;
import com.serotonin.mango.util.LocalizableJsonException;
import com.serotonin.mango.vo.dataSource.DataSourceVO;
import com.serotonin.mango.vo.event.EventTypeVO;
import br.org.scadabr.util.SerializationHelper;
import br.org.scadabr.util.StringUtils;
import br.org.scadabr.utils.ImplementMeException;
import br.org.scadabr.utils.TimePeriods;
import br.org.scadabr.web.dwr.DwrResponseI18n;
import br.org.scadabr.utils.i18n.LocalizableMessage;
import br.org.scadabr.utils.i18n.LocalizableMessageImpl;
import java.util.Arrays;

/**
 * @author Matthew Lohbihler
 */

public class OneWireDataSourceVO extends DataSourceVO<OneWireDataSourceVO> {

    public static final Type TYPE = Type.ONE_WIRE;
    public static final String RESCAN_NONE_TEXT = "NONE";

    @Override
    protected void addEventTypes(List<EventTypeVO> ets) {
        ets.add(createEventType(OneWireDataSourceRT.DATA_SOURCE_EXCEPTION_EVENT, new LocalizableMessageImpl(
                "event.ds.dataSource")));
        ets.add(createEventType(OneWireDataSourceRT.POINT_READ_EXCEPTION_EVENT, new LocalizableMessageImpl(
                "event.ds.pointRead")));
        ets.add(createEventType(OneWireDataSourceRT.POINT_WRITE_EXCEPTION_EVENT, new LocalizableMessageImpl(
                "event.ds.pointWrite")));
    }

    private static final ExportCodes EVENT_CODES = new ExportCodes();

    static {
        EVENT_CODES.addElement(OneWireDataSourceRT.DATA_SOURCE_EXCEPTION_EVENT, "DATA_SOURCE_EXCEPTION");
        EVENT_CODES.addElement(OneWireDataSourceRT.POINT_READ_EXCEPTION_EVENT, "POINT_READ_EXCEPTION");
        EVENT_CODES.addElement(OneWireDataSourceRT.POINT_WRITE_EXCEPTION_EVENT, "POINT_WRITE_EXCEPTION");
    }

    @Override
    public ExportCodes getEventCodes() {
        return EVENT_CODES;
    }

    @Override
    public LocalizableMessage getConnectionDescription() {
        return new LocalizableMessageImpl("common.default", commPortId);
    }

    @Override
    public Type getType() {
        return TYPE;
    }

    @Override
    public DataSourceRT createDataSourceRT() {
        return new OneWireDataSourceRT(this);
    }

    @Override
    public OneWirePointLocatorVO createPointLocator() {
        return new OneWirePointLocatorVO();
    }

    
    private String commPortId;
    private TimePeriods updatePeriodType = TimePeriods.MINUTES;
    
    private int updatePeriods = 5;
    /**
     * null means no rescan ...
     */
    private TimePeriods rescanPeriodType = null;
    
    private int rescanPeriods = 1;

    public String getCommPortId() {
        return commPortId;
    }

    public void setCommPortId(String commPortId) {
        this.commPortId = commPortId;
    }

    public TimePeriods getUpdatePeriodType() {
        return updatePeriodType;
    }

    public void setUpdatePeriodType(TimePeriods updatePeriodType) {
        this.updatePeriodType = updatePeriodType;
    }

    public int getUpdatePeriods() {
        return updatePeriods;
    }

    public void setUpdatePeriods(int updatePeriods) {
        this.updatePeriods = updatePeriods;
    }

    public boolean isRescan() {
        return rescanPeriodType != null;
    }

    public TimePeriods getRescanPeriodType() {
        if (rescanPeriodType == null) {
            throw new ImplementMeException();
        }
        return rescanPeriodType;
    }

    public void setRescanPeriodType(TimePeriods rescanPeriodType) {
        this.rescanPeriodType = rescanPeriodType;
    }

    public int getRescanPeriods() {
        if (rescanPeriodType == null) {
            throw new ImplementMeException();
        }
        return rescanPeriods;
    }

    public void setRescanPeriods(int rescanPeriods) {
        this.rescanPeriods = rescanPeriods;
    }

    @Override
    public void validate(DwrResponseI18n response) {
        super.validate(response);

        if (commPortId.isEmpty()) {
            response.addContextual("commPortId", "validate.required");
        }
        if (updatePeriods <= 0) {
            response.addContextual("updatePeriods", "validate.greaterThanZero");
        }
        if (isRescan() && rescanPeriods <= 0) {
            response.addContextual("rescanPeriods", "validate.greaterThanZero");
        }
    }

    @Override
    protected void addPropertiesImpl(List<LocalizableMessage> list) {
        AuditEventType.addPropertyMessage(list, "dsEdit.1wire.port", commPortId);
        AuditEventType.addPropertyMessage(list, "dsEdit.updatePeriod", updatePeriodType.getPeriodDescription(updatePeriods));
        if (isRescan()) {
            AuditEventType.addPropertyMessage(list, "dsEdit.1wire.scheduledRescan", new LocalizableMessageImpl(
                    "dsEdit.1wire.none"));
        } else {
            AuditEventType.addPropertyMessage(list, "dsEdit.1wire.scheduledRescan", rescanPeriodType.getPeriodDescription(rescanPeriods));
        }
    }

    @Override
    protected void addPropertyChangesImpl(List<LocalizableMessage> list, OneWireDataSourceVO from) {
        AuditEventType.maybeAddPropertyChangeMessage(list, "dsEdit.1wire.port", from.commPortId, commPortId);
        AuditEventType.maybeAddPropertyChangeMessage(list, "dsEdit.updatePeriod",
                from.updatePeriodType.getPeriodDescription(from.updatePeriods),
                updatePeriodType.getPeriodDescription(updatePeriods));
        if (from.rescanPeriodType != rescanPeriodType || from.rescanPeriods != rescanPeriods) {
            LocalizableMessage fromMessage;
            if (from.isRescan()) {
                fromMessage = new LocalizableMessageImpl("dsEdit.1wire.none");
            } else {
                fromMessage = from.rescanPeriodType.getPeriodDescription(from.rescanPeriods);
            }

            LocalizableMessage toMessage;
            if (isRescan()) {
                toMessage = new LocalizableMessageImpl("dsEdit.1wire.none");
            } else {
                toMessage = rescanPeriodType.getPeriodDescription(rescanPeriods);
            }

            AuditEventType.addPropertyChangeMessage(list, "dsEdit.1wire.scheduledRescan", fromMessage, toMessage);
        }
    }

    //
    // /
    // / Serialization
    // /
    //
    private static final long serialVersionUID = -1;
    private static final int version = 2;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(version);
        SerializationHelper.writeSafeUTF(out, commPortId);
        out.writeInt(updatePeriodType.mangoDbId);
        out.writeInt(updatePeriods);
        out.writeInt(rescanPeriodType.mangoDbId);
        out.writeInt(rescanPeriods);
    }

    private void readObject(ObjectInputStream in) throws IOException {
        int ver = in.readInt();

        // Switch on the version of the class so that version changes can be elegantly handled.
        if (ver == 1) {
            commPortId = SerializationHelper.readSafeUTF(in);
            updatePeriodType = TimePeriods.fromMangoDbId(in.readInt());
            updatePeriods = in.readInt();
            rescanPeriodType = null;
            rescanPeriods = 1;
        } else if (ver == 2) {
            commPortId = SerializationHelper.readSafeUTF(in);
            updatePeriodType = TimePeriods.fromMangoDbId(in.readInt());
            updatePeriods = in.readInt();
            rescanPeriodType = TimePeriods.fromMangoDbId(in.readInt());
            rescanPeriods = in.readInt();
        }
    }

    @Override
    public void jsonDeserialize(JsonReader reader, JsonObject json) throws JsonException {
        super.jsonDeserialize(reader, json);

        TimePeriods value = deserializeUpdatePeriodType(json);
        if (value != null) {
            updatePeriodType = value;
        }

        String text = json.getString("rescanPeriodType");
        if (text != null) {
            if (RESCAN_NONE_TEXT.equalsIgnoreCase(text)) {
                rescanPeriodType = null;
            } else {
                try {
                    rescanPeriodType = TimePeriods.valueOf(text);
                } catch (Exception e) {
                    List<Object> result = new ArrayList<>();
                    result.add(RESCAN_NONE_TEXT);
                    result.addAll(Arrays.asList(TimePeriods.values()));
                    throw new LocalizableJsonException("emport.error.invalid", "rescanPeriodType", text, result);
                }
            }
        }
    }

    @Override
    public void jsonSerialize(Map<String, Object> map) {
        super.jsonSerialize(map);
        serializeUpdatePeriodType(map, updatePeriodType);

        if (isRescan()) {
            map.put("rescanPeriodType", RESCAN_NONE_TEXT);
        } else {
            map.put("rescanPeriodType", TimePeriods.values());
        }
    }
}
