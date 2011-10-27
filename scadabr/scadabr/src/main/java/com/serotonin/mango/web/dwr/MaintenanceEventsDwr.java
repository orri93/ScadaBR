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
package com.serotonin.mango.web.dwr;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;

import com.serotonin.mango.Common;
import com.serotonin.mango.db.dao.DataSourceDao;
import com.serotonin.mango.db.dao.MaintenanceEventDao;
import com.serotonin.mango.rt.RuntimeManager;
import com.serotonin.mango.rt.event.AlarmLevels;
import com.serotonin.mango.rt.event.maintenance.MaintenanceEventRT;
import com.serotonin.mango.vo.dataSource.DataSourceVO;
import com.serotonin.mango.vo.event.MaintenanceEventVO;
import com.serotonin.util.StringUtils;
import com.serotonin.web.dwr.DwrResponseI18n;
import com.serotonin.web.i18n.LocalizableMessage;

/**
 * @author Matthew Lohbihler
 */
public class MaintenanceEventsDwr extends BaseDwr {

    @Autowired
    private RuntimeManager runtimeManager;
    @Autowired
    private DataSourceDao dataSourceDao;

    public DwrResponseI18n getMaintenanceEvents() {
        permissions.ensureAdmin();

        DwrResponseI18n response = new DwrResponseI18n();
        final ResourceBundle bundle = getResourceBundle();

        List<MaintenanceEventVO> events = new MaintenanceEventDao().getMaintenanceEvents();
        Collections.sort(events, new Comparator<MaintenanceEventVO>() {

            @Override
            public int compare(MaintenanceEventVO m1, MaintenanceEventVO m2) {
                return m1.getDescription().getLocalizedMessage(bundle).compareTo(m1.getDescription().getLocalizedMessage(bundle));
            }
        });
        response.addData("events", events);

        Map<Integer, String> dataSources = new HashMap();
        for (DataSourceVO<?> ds : dataSourceDao.getDataSources()) {
            dataSources.put(ds.getId(), ds.getName());
        }
        response.addData("dataSources", dataSources);

        return response;
    }

    public DwrResponseI18n getMaintenanceEvent(int id) {
        permissions.ensureAdmin();

        DwrResponseI18n response = new DwrResponseI18n();

        MaintenanceEventVO me;
        boolean activated = false;
        if (id == Common.NEW_ID) {
            DateTime dt = new DateTime();
            me = new MaintenanceEventVO();
            me.setXid(new MaintenanceEventDao().generateUniqueXid());
            me.setActiveYear(dt.getYear());
            me.setInactiveYear(dt.getYear());
            me.setActiveMonth(dt.getMonthOfYear());
            me.setInactiveMonth(dt.getMonthOfYear());
        } else {
            me = new MaintenanceEventDao().getMaintenanceEvent(id);

            MaintenanceEventRT rt = runtimeManager.getRunningMaintenanceEvent(me.getId());
            if (rt != null) {
                activated = rt.isEventActive();
            }
        }

        response.addData("me", me);
        response.addData("activated", activated);

        return response;
    }

    public DwrResponseI18n saveMaintenanceEvent(int id, String xid, int dataSourceId, String alias, AlarmLevels alarmLevel,
            int scheduleType, boolean disabled, int activeYear, int activeMonth, int activeDay, int activeHour,
            int activeMinute, int activeSecond, String activeCron, int inactiveYear, int inactiveMonth,
            int inactiveDay, int inactiveHour, int inactiveMinute, int inactiveSecond, String inactiveCron) {
        permissions.ensureAdmin();

        MaintenanceEventVO e = new MaintenanceEventVO();
        e.setId(id);
        e.setXid(xid);
        e.setDataSourceId(dataSourceId);
        e.setAlias(alias);
        e.setAlarmLevel(alarmLevel);
        e.setScheduleType(scheduleType);
        e.setDisabled(disabled);
        e.setActiveYear(activeYear);
        e.setActiveMonth(activeMonth);
        e.setActiveDay(activeDay);
        e.setActiveHour(activeHour);
        e.setActiveMinute(activeMinute);
        e.setActiveSecond(activeSecond);
        e.setActiveCron(activeCron);
        e.setInactiveYear(inactiveYear);
        e.setInactiveMonth(inactiveMonth);
        e.setInactiveDay(inactiveDay);
        e.setInactiveHour(inactiveHour);
        e.setInactiveMinute(inactiveMinute);
        e.setInactiveSecond(inactiveSecond);
        e.setInactiveCron(inactiveCron);

        DwrResponseI18n response = new DwrResponseI18n();
        MaintenanceEventDao maintenanceEventDao = new MaintenanceEventDao();

        if (StringUtils.isEmpty(xid)) {
            response.addContextualMessage("xid", "validate.required");
        } else if (!maintenanceEventDao.isXidUnique(xid, id)) {
            response.addContextualMessage("xid", "validate.xidUsed");
        }

        e.validate(response);

        // Save the maintenance event
        if (!response.getHasMessages()) {
            runtimeManager.saveMaintenanceEvent(e);
            response.addData("meId", e.getId());
        }

        return response;
    }

    public void deleteMaintenanceEvent(int meId) {
        permissions.ensureAdmin();
        runtimeManager.deleteMaintenanceEvent(meId);
    }

    public DwrResponseI18n toggleMaintenanceEvent(int id) {
        permissions.ensureAdmin();
        DwrResponseI18n response = new DwrResponseI18n();

        MaintenanceEventRT rt = runtimeManager.getRunningMaintenanceEvent(id);
        boolean activated = false;
        if (rt == null) {
            response.addMessage(new LocalizableMessage("maintenanceEvents.toggle.disabled"));
        } else {
            activated = rt.toggle();
        }

        response.addData("activated", activated);

        return response;
    }
}
