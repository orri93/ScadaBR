/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.serotonin.mango.vo.dataSource.fhz4j;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

import net.sf.fhz4j.Fhz1000;
import net.sf.fhz4j.FhzProtocol;
import net.sf.fhz4j.hms.HmsDeviceType;
import net.sf.fhz4j.hms.HmsProperty;

import com.serotonin.json.JsonObject;
import com.serotonin.json.JsonReader;
import com.serotonin.json.JsonRemoteEntity;
import com.serotonin.json.JsonRemoteProperty;
import com.serotonin.mango.rt.event.type.AuditEventType;
import com.serotonin.web.i18n.LocalizableMessage;

/**
 *
 * @author aploese
 */
@JsonRemoteEntity
public class HmsPointLocator extends ProtocolLocator<HmsProperty> {

    @JsonRemoteProperty
    private short housecode;
    @JsonRemoteProperty
    private HmsDeviceType hmsDeviceType;
    

    /**
     * @return the housecode
     */
    public short getHousecode() {
        return housecode;
    }
    
    public String defaultName() {
        return getProperty() == null ? "HMS dataPoint" : String.format("%s %s", getHousecodeStr(), getProperty().getLabel());
    }

    /**
     * @param housecode the housecode to set
     */
    public void setHousecode(short housecode) {
        this.housecode = housecode;
    }

    public void setHousecodeStr(String deviceHousecode) {
        this.housecode = (short)Integer.parseInt(deviceHousecode, 16);
    }
    
    public String getHousecodeStr() {
        return String.format("%04X", housecode);
    }

    public String getDeviceHousecodeStr() {
        return Fhz1000.houseCodeToString(housecode);
    }

    @Override
    public void addProperties(List<LocalizableMessage> list) {
        AuditEventType.addPropertyMessage(list, "dsEdit.hfz4j.dataPoint", housecode);
        AuditEventType.addPropertyMessage(list, "dsEdit.hfz4j.dataPoint", hmsDeviceType);
    }

    @Override
    public void addPropertyChanges(List<LocalizableMessage> list, ProtocolLocator o) {
        super.addPropertyChanges(list, o);
        final HmsPointLocator from = (HmsPointLocator)o;
        AuditEventType.maybeAddPropertyChangeMessage(list, "dsEdit.fhz4j.dataPoint", from.housecode, housecode);
        AuditEventType.maybeAddPropertyChangeMessage(list, "dsEdit.fhz4j.dataPoint", from.hmsDeviceType, hmsDeviceType);
    }
    
    //
    // /
    // / Serialization
    // /
    //
    private static final long serialVersionUID = -1;
    private static final int SERIAL_VERSION = 1;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(SERIAL_VERSION);
        out.writeShort(housecode);
        out.writeObject(hmsDeviceType);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int ver = in.readInt();

        // Switch on the version of the class so that version changes can be elegantly handled.
        switch (ver) {
            case 1:
                housecode = in.readShort();
                hmsDeviceType = (HmsDeviceType) in.readObject();
                break;
            default:
                throw new RuntimeException("Cant handle version");
        }
    }

    @Override
    public void jsonDeserialize(JsonReader reader, JsonObject json) {
    }

    @Override
    public void jsonSerialize(Map<String, Object> map) {
        super.jsonSerialize(map);
    }

    @Override
    public FhzProtocol getFhzProtocol() {
        return FhzProtocol.HMS;
    }

    /**
     * @return the hmsDeviceType
     */
    public HmsDeviceType getHmsDeviceType() {
        return hmsDeviceType;
    }

    /**
     * @param hmsDeviceType the hmsDeviceType to set
     */
    public void setHmsDeviceType(HmsDeviceType hmsDeviceType) {
        this.hmsDeviceType = hmsDeviceType;
    }

    
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HmsPointLocator other = (HmsPointLocator) obj;
        if (this.housecode != other.housecode) {
            return false;
        }
        if (this.hmsDeviceType != other.hmsDeviceType) {
            return false;
        }
        if (this.getProperty() != other.getProperty()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 43 * hash + this.housecode;
        hash = 43 * hash + (this.hmsDeviceType != null ? this.hmsDeviceType.hashCode() : 0);
        hash = 43 * hash + (this.getProperty() != null ? this.getProperty().hashCode() : 0);
        return hash;
    }
    
    @Override
    public String toString() {
        return String.format("%s [housecode: %x, devicetype: property %s]", getClass().getName(), housecode, hmsDeviceType, getProperty());
    }

 }