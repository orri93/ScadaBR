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
package com.serotonin.mango.web.dwr.beans;

import br.org.scadabr.util.StringUtils;
import java.util.Objects;

abstract public class BasePointState implements Cloneable {

    private String id;
    private String change;
    private String chart;
    private String messages;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getChange() {
        return change;
    }

    public void setChange(String change) {
        this.change = change;
    }

    public String getChart() {
        return chart;
    }

    public void setChart(String chart) {
        this.chart = chart;
    }

    public String getMessages() {
        return messages;
    }

    public void setMessages(String messages) {
        this.messages = messages;
    }

    public void removeEqualValue(BasePointState that) {
        if (Objects.equals(change, that.change)) {
            change = null;
        }
        if (Objects.equals(chart, that.chart)) {
            chart = null;
        }
        if (Objects.equals(messages, that.messages)) {
            messages = null;
        }
    }

    public boolean isEmpty() {
        return change == null && chart == null && messages == null;
    }
}
