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
package com.serotonin.mango.rt.dataSource.galil;

import br.org.scadabr.DataType;
import com.serotonin.mango.rt.dataImage.types.AlphanumericValue;
import com.serotonin.mango.rt.dataImage.types.BinaryValue;
import com.serotonin.mango.rt.dataImage.types.MangoValue;
import com.serotonin.mango.rt.dataImage.types.MultistateValue;
import com.serotonin.mango.rt.dataImage.types.NumericValue;
import com.serotonin.mango.vo.dataSource.galil.VariablePointTypeVO;
import br.org.scadabr.utils.i18n.LocalizableException;

/**
 * @author Matthew Lohbihler
 */
public class VariablePointTypeRT extends PointTypeRT {

    private final VariablePointTypeVO vo;

    public VariablePointTypeRT(VariablePointTypeVO vo) {
        super(vo);
        this.vo = vo;
    }

    @Override
    protected String getPollRequestImpl() {
        if (vo.getDataType() == DataType.ALPHANUMERIC) {
            return vo.getVariableName() + "={S6}";
        }
        return vo.getVariableName() + "=";
    }

    @Override
    public MangoValue parsePollResponse(String data, String pointName) throws LocalizableException {
        if (vo.getDataType() == DataType.ALPHANUMERIC) {
            return new AlphanumericValue(data);
        }

        try {
            double value = Double.parseDouble(data);

            if (vo.getDataType() == DataType.BINARY) {
                return new BinaryValue(value != 0);
            }

            if (vo.getDataType() == DataType.MULTISTATE) {
                return new MultistateValue((int) value);
            }

            // Must be numeric.
            return new NumericValue(value);
        } catch (NumberFormatException e) {
            throw new LocalizableException("event.galil.numericParse", data);
        }
    }

    @Override
    protected String getSetRequestImpl(MangoValue value) {
        StringBuilder data = new StringBuilder();
        data.append(vo.getVariableName()).append('=');

        if (vo.getDataType() == DataType.BINARY) {
            data.append(value.getBooleanValue() ? '1' : '0');
        } else if (vo.getDataType() == DataType.MULTISTATE) {
            data.append(value.getIntegerValue());
        } else if (vo.getDataType() == DataType.NUMERIC) {
            data.append(value.getDoubleValue());
        } else {
            data.append('"').append(value.getStringValue()).append('"');
        }

        return data.toString();
    }

    @Override
    public MangoValue parseSetResponse(String data) throws LocalizableException {
        if (!"".equals(data)) {
            throw new LocalizableException("event.galil.unexpected", data);
        }
        return null;
    }
}
