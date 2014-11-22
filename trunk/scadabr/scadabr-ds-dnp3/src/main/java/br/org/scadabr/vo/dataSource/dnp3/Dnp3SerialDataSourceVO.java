package br.org.scadabr.vo.dataSource.dnp3;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

import br.org.scadabr.rt.dataSource.dnp3.Dnp3SerialDataSource;

import br.org.scadabr.json.JsonException;
import br.org.scadabr.json.JsonObject;
import br.org.scadabr.json.JsonReader;


import com.serotonin.mango.rt.dataSource.DataSourceRT;
import com.serotonin.mango.rt.event.type.AuditEventType;
import br.org.scadabr.util.SerializationHelper;
import br.org.scadabr.util.StringUtils;
import br.org.scadabr.web.dwr.DwrResponseI18n;
import br.org.scadabr.utils.i18n.LocalizableMessage;
import br.org.scadabr.utils.i18n.LocalizableMessageImpl;


public class Dnp3SerialDataSourceVO extends
        Dnp3DataSourceVO<Dnp3SerialDataSourceVO> {

    public static final Type TYPE = Type.DNP3_SERIAL;

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
        return new Dnp3SerialDataSource(this);
    }

    
    private String commPortId;
    
    private int baudRate = 9600;
    
    private int dataBits = 8;
    
    private int stopBits = 1;
    
    private int parity = 0;
    
    private boolean echo = false;

    /**
     * @return the commPortId
     */
    public String getCommPortId() {
        return commPortId;
    }

    /**
     * @param commPortId the commPortId to set
     */
    public void setCommPortId(String commPortId) {
        this.commPortId = commPortId;
    }

    /**
     * @return the baudRate
     */
    public int getBaudRate() {
        return baudRate;
    }

    /**
     * @param baudRate the baudRate to set
     */
    public void setBaudRate(int baudRate) {
        this.baudRate = baudRate;
    }

    /**
     * @return the dataBits
     */
    public int getDataBits() {
        return dataBits;
    }

    /**
     * @param dataBits the dataBits to set
     */
    public void setDataBits(int dataBits) {
        this.dataBits = dataBits;
    }

    /**
     * @return the stopBits
     */
    public int getStopBits() {
        return stopBits;
    }

    /**
     * @param stopBits the stopBits to set
     */
    public void setStopBits(int stopBits) {
        this.stopBits = stopBits;
    }

    /**
     * @return the parity
     */
    public int getParity() {
        return parity;
    }

    /**
     * @param parity the parity to set
     */
    public void setParity(int parity) {
        this.parity = parity;
    }

    /**
     * @return the echo
     */
    public boolean isEcho() {
        return echo;
    }

    /**
     * @param echo the echo to set
     */
    public void setEcho(boolean echo) {
        this.echo = echo;
    }

    @Override
    public void validate(DwrResponseI18n response) {
        super.validate(response);
        if (commPortId.isEmpty()) {
            response.addContextual("commPortId", "validate.required");
        }
        if (baudRate <= 0) {
            response.addContextual("baudRate", "validate.invalidValue");
        }
        if (dataBits < 5 || dataBits > 8) {
            response.addContextual("dataBits", "validate.invalidValue");
        }
        if (stopBits < 1 || stopBits > 3) {
            response.addContextual("stopBits", "validate.invalidValue");
        }
        if (parity < 0 || parity > 4) {
            response .addContextual("parityBits", "validate.invalidValue");
        }
    }

    @Override
    protected void addPropertiesImpl(List<LocalizableMessage> list) {
        super.addPropertiesImpl(list);

        AuditEventType.addPropertyMessage(list, "dsEdit.dnp3Serial.port",
                commPortId);
        AuditEventType.addPropertyMessage(list, "dsEdit.dnp3Serial.baud",
                baudRate);
        AuditEventType.addPropertyMessage(list, "dsEdit.dnp3Serial.dataBits",
                dataBits);
        AuditEventType.addPropertyMessage(list, "dsEdit.dnp3Serial.stopBits",
                stopBits);
        AuditEventType.addPropertyMessage(list, "dsEdit.dnp3Serial.parity",
                parity);
        AuditEventType.addPropertyMessage(list, "dsEdit.dnp3Serial.echo", echo);
    }

    @Override
    protected void addPropertyChangesImpl(List<LocalizableMessage> list,
            Dnp3SerialDataSourceVO from) {
        super.addPropertyChangesImpl(list, from);
        AuditEventType.maybeAddPropertyChangeMessage(list,
                "dsEdit.dnp3Serial.port", from.commPortId, commPortId);
        AuditEventType.maybeAddPropertyChangeMessage(list,
                "dsEdit.dnp3Serial.baud", from.baudRate, baudRate);
        AuditEventType.maybeAddPropertyChangeMessage(list,
                "dsEdit.dnp3Serial.dataBits", from.dataBits, dataBits);
        AuditEventType.maybeAddPropertyChangeMessage(list,
                "dsEdit.dnp3Serial.stopBits", from.stopBits, stopBits);
        AuditEventType.maybeAddPropertyChangeMessage(list,
                "dsEdit.dnp3Serial.parity", from.parity, parity);
        AuditEventType.maybeAddPropertyChangeMessage(list,
                "dsEdit.dnp3Serial.echo", from.echo, echo);
    }

    private static final long serialVersionUID = -1;
    private static final int version = 1;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(version);
        SerializationHelper.writeSafeUTF(out, commPortId);
        out.writeInt(baudRate);
        out.writeInt(dataBits);
        out.writeInt(stopBits);
        out.writeInt(parity);
        out.writeBoolean(echo);

    }

    private void readObject(ObjectInputStream in) throws IOException,
            ClassNotFoundException {
        int ver = in.readInt();

		// Switch on the version of the class so that version changes can be
        // elegantly handled.
        if (ver == 1) {
            commPortId = SerializationHelper.readSafeUTF(in);
            baudRate = in.readInt();
            dataBits = in.readInt();
            stopBits = in.readInt();
            parity = in.readInt();
            echo = in.readBoolean();
        }
    }

    @Override
    public void jsonDeserialize(JsonReader reader, JsonObject json)
            throws JsonException {
        super.jsonDeserialize(reader, json);

    }

    @Override
    public void jsonSerialize(Map<String, Object> map) {
        super.jsonSerialize(map);
    }
}
