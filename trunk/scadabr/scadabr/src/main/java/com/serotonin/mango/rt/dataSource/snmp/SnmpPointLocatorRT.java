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
package com.serotonin.mango.rt.dataSource.snmp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.smi.Counter32;
import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.Gauge32;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.Opaque;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.Variable;

import com.serotonin.ShouldNeverHappenException;
import com.serotonin.mango.MangoDataType;
import com.serotonin.mango.rt.dataImage.types.AlphanumericValue;
import com.serotonin.mango.rt.dataImage.types.BinaryValue;
import com.serotonin.mango.rt.dataImage.types.MangoValue;
import com.serotonin.mango.rt.dataImage.types.MultistateValue;
import com.serotonin.mango.rt.dataImage.types.NumericValue;
import com.serotonin.mango.rt.dataSource.PointLocatorRT;
import com.serotonin.mango.vo.dataSource.snmp.SnmpPointLocatorVO;
import com.serotonin.util.StringUtils;

/**
 * @author Matthew Lohbihler
 * 
 */
public class SnmpPointLocatorRT extends PointLocatorRT {
    private final static Logger LOG = LoggerFactory.getLogger(SnmpPointLocatorRT.class);

    private final SnmpPointLocatorVO vo;
    private final OID oid;

    public SnmpPointLocatorRT(SnmpPointLocatorVO vo) {
        this.vo = vo;
        oid = new OID(vo.getOid());
    }

    @Override
    public boolean isSettable() {
        return vo.isSettable();
    }

    public OID getOid() {
        return oid;
    }

    public SnmpPointLocatorVO getVO() {
        return vo;
    }

    public MangoValue variableToValue(Variable variable) {
        switch (vo.getMangoDataType()) {
        case BINARY:
            return new BinaryValue(StringUtils.isEqual(variable.toString(), vo.getBinary0Value()));

        case MULTISTATE:
            return new MultistateValue(variable.toInt());

        case NUMERIC:
            if (variable instanceof OctetString) {
                try {
                    return NumericValue.parseNumeric(variable.toString());
                }
                catch (NumberFormatException e) {
                    // no op
                }
            }
            return new NumericValue(variable.toInt());

        case ALPHANUMERIC:
            return new AlphanumericValue(variable.toString());

        }

        throw new ShouldNeverHappenException("Unknown data type id: " + vo.getMangoDataType().name());
    }

    public Variable valueToVariable(MangoValue value) {
        return valueToVariableImpl(value, vo.getSetType());
    }

    public static Variable valueToVariableImpl(MangoValue value, int setType) {
        switch (setType) {
        case SnmpPointLocatorVO.SetTypes.INTEGER_32:
            if (value instanceof NumericValue)
                return new Integer32(value.getIntegerValue());
            if (value instanceof BinaryValue)
                return new Integer32(value.getBooleanValue() ? 1 : 0);

            LOG.warn("Can't convert value '" + value + "' (" + value.getMangoDataType() + ") to Integer32");
            return new Integer32(0);

        case SnmpPointLocatorVO.SetTypes.OCTET_STRING:
            return new OctetString(value.toString());

        case SnmpPointLocatorVO.SetTypes.OID:
            return new OID(value.toString());

        case SnmpPointLocatorVO.SetTypes.IP_ADDRESS:
            return new IpAddress(value.toString());

        case SnmpPointLocatorVO.SetTypes.COUNTER_32:
            return new Counter32((long) value.getDoubleValue());

        case SnmpPointLocatorVO.SetTypes.GAUGE_32:
            return new Gauge32((long) value.getDoubleValue());

        case SnmpPointLocatorVO.SetTypes.TIME_TICKS:
            return new TimeTicks((long) value.getDoubleValue());

        case SnmpPointLocatorVO.SetTypes.OPAQUE:
            return new Opaque(value.toString().getBytes());

        case SnmpPointLocatorVO.SetTypes.COUNTER_64:
            return new Counter64((long) value.getDoubleValue());
        default:

        throw new ShouldNeverHappenException("Unknown set type id: " + setType);
    }
    }
}
