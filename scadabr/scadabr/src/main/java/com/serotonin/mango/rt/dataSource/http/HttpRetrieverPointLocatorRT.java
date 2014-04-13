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
package com.serotonin.mango.rt.dataSource.http;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.regex.Pattern;

import com.serotonin.mango.DataTypes;
import com.serotonin.mango.rt.dataSource.PointLocatorRT;
import com.serotonin.mango.vo.dataSource.http.HttpRetrieverPointLocatorVO;

/**
 * @author Matthew Lohbihler
 */
public class HttpRetrieverPointLocatorRT extends PointLocatorRT<HttpRetrieverPointLocatorVO> {

    private final Pattern valuePattern;
    private final boolean ignoreIfMissing;
    private final int dataTypeId;
    private String binary0Value;
    private DecimalFormat valueFormat;
    private final Pattern timePattern;
    private final SimpleDateFormat timeFormat;

    public HttpRetrieverPointLocatorRT(HttpRetrieverPointLocatorVO vo) {
        super(vo);
        valuePattern = Pattern.compile(vo.getValueRegex());
        ignoreIfMissing = vo.isIgnoreIfMissing();
        dataTypeId = vo.getDataTypeId();

        if (dataTypeId == DataTypes.BINARY) {
            binary0Value = vo.getValueFormat();
        } else if (dataTypeId == DataTypes.NUMERIC && !vo.getValueFormat().isEmpty()) {
            valueFormat = new DecimalFormat(vo.getValueFormat());
        }

        if (!vo.getTimeRegex().isEmpty()) {
            timePattern = Pattern.compile(vo.getTimeRegex());
            timeFormat = new SimpleDateFormat(vo.getTimeFormat());
        } else {
            timePattern = null;
            timeFormat = null;
        }

    }

    public Pattern getValuePattern() {
        return valuePattern;
    }

    public boolean isIgnoreIfMissing() {
        return ignoreIfMissing;
    }

    public DecimalFormat getValueFormat() {
        return valueFormat;
    }

    public int getDataTypeId() {
        return dataTypeId;
    }

    public String getBinary0Value() {
        return binary0Value;
    }

    public Pattern getTimePattern() {
        return timePattern;
    }

    public SimpleDateFormat getTimeFormat() {
        return timeFormat;
    }
}
