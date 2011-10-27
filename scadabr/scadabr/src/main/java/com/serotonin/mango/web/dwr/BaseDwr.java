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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.jstl.core.Config;
import javax.servlet.jsp.jstl.fmt.LocalizationContext;

import org.directwebremoting.WebContext;
import org.directwebremoting.WebContextFactory;
import org.joda.time.DateTime;
import org.joda.time.IllegalFieldValueException;
import org.springframework.beans.factory.annotation.Autowired;

import com.serotonin.ShouldNeverHappenException;
import com.serotonin.mango.Common;
import com.serotonin.mango.db.dao.DataPointDao;
import com.serotonin.mango.db.dao.EventDao;
import com.serotonin.mango.db.dao.UserCommentDao;
import com.serotonin.mango.db.dao.UserDao;
import com.serotonin.mango.rt.RuntimeManager;
import com.serotonin.mango.rt.dataImage.DataPointRT;
import com.serotonin.mango.rt.dataImage.PointValueTime;
import com.serotonin.mango.rt.dataImage.SetPointSource;
import com.serotonin.mango.rt.dataImage.types.MangoValue;
import com.serotonin.mango.rt.event.EventInstance;
import com.serotonin.mango.util.DateUtils;
import com.serotonin.mango.view.chart.ChartRenderer;
import com.serotonin.mango.vo.DataPointComment;
import com.serotonin.mango.vo.DataPointExtendedNameComparator;
import com.serotonin.mango.vo.DataPointVO;
import com.serotonin.mango.vo.EventComment;
import com.serotonin.mango.vo.User;
import com.serotonin.mango.vo.UserComment;
import com.serotonin.mango.vo.permission.Permissions;
import com.serotonin.mango.web.dwr.beans.BasePointState;
import com.serotonin.mango.web.dwr.beans.DataPointBean;
import com.serotonin.mango.web.dwr.beans.WatchListState;
import com.serotonin.mango.web.taglib.Functions;
import com.serotonin.util.ObjectUtils;
import com.serotonin.util.StringUtils;
import com.serotonin.web.content.ContentGenerator;
import com.serotonin.web.dwr.MethodFilter;
import com.serotonin.web.i18n.I18NUtils;
import com.serotonin.web.i18n.LocalizableMessage;

abstract public class BaseDwr {

    public static final String MODEL_ATTR_EVENTS = "events";
    public static final String MODEL_ATTR_HAS_UNACKED_EVENT = "hasUnacknowledgedEvent";
    public static final String MODEL_ATTR_RESOURCE_BUNDLE = "bundle";
    @Autowired
    protected Common common;
    @Autowired
    protected Permissions permissions;
    @Autowired
    protected UserCommentDao userCommentDao;
    @Autowired
    protected EventDao eventDao;
    @Autowired
    private RuntimeManager runtimeManager;
    @Autowired
    protected UserDao userDao;
    protected ResourceBundle changeSnippetMap = ResourceBundle.getBundle("changeSnippetMap");
    protected ResourceBundle chartSnippetMap = ResourceBundle.getBundle("chartSnippetMap");

    /**
     * Base method for preparing information in a state object and returning a point value.
     *
     * @param componentId
     *            a unique id for the browser side component. Required for set point snippets.
     * @param state
     * @param point
     * @param status
     * @param model
     * @return
     */
    protected PointValueTime prepareBasePointState(String componentId, BasePointState state, DataPointVO pointVO,
            DataPointRT point, Map<String, Object> model) {
        model.clear();
        model.put("componentId", componentId);
        model.put("point", pointVO);
        model.put("pointRT", point);
        model.put(MODEL_ATTR_RESOURCE_BUNDLE, getResourceBundle());

        PointValueTime pointValue = null;
        if (point == null) {
            model.put("disabled", "true");
        } else {
            pointValue = point.getPointValue();
            if (pointValue != null) {
                model.put("pointValue", pointValue);
            }
        }

        return pointValue;
    }

    protected void setEvents(DataPointVO pointVO, User user, Map<String, Object> model) {
        int userId = 0;
        if (user != null) {
            userId = user.getId();
        }
        List<EventInstance> events = eventDao.getPendingEventsForDataPoint(pointVO.getId(), userId);
        if (events != null) {
            model.put(MODEL_ATTR_EVENTS, events);
            for (EventInstance event : events) {
                if (!event.isAcknowledged()) {
                    model.put(MODEL_ATTR_HAS_UNACKED_EVENT, true);
                }
            }
        }
    }

    protected void setPrettyText(WatchListState state, DataPointVO pointVO, Map<String, Object> model,
            PointValueTime pointValue) {
        String prettyText = Functions.getHtmlText(pointVO, pointValue);
        model.put("text", prettyText);
        if (!ObjectUtils.isEqual(pointVO.lastValue(), pointValue)) {
            state.setValue(prettyText);
            if (pointValue != null) {
                state.setTime(Functions.getTime(pointValue));
            }
            pointVO.updateLastValue(pointValue);
        }
    }

    protected void setChange(DataPointVO pointVO, BasePointState state, DataPointRT point, HttpServletRequest request,
            Map<String, Object> model, User user) {
        if (permissions.hasDataPointSetPermission(user, pointVO)) {
            setChange(pointVO, state, point, request, model);
        }
    }

    protected void setChange(DataPointVO pointVO, BasePointState state, DataPointRT point, HttpServletRequest request,
            Map<String, Object> model) {
        if (pointVO.getPointLocator().isSettable()) {
            if (point == null) {
                state.setChange(getMessage("common.pointDisabled"));
            } else {
                String snippet = changeSnippetMap.getString(pointVO.getTextRenderer().getClass().getName());
                state.setChange(generateContent(request, snippet, model));
            }
        }
    }

    protected void setChart(DataPointVO point, BasePointState state, HttpServletRequest request,
            Map<String, Object> model) {
        ChartRenderer chartRenderer = point.getChartRenderer();
        if (chartRenderer != null) {
            chartRenderer.addDataToModel(model, point);
            String snippet = chartSnippetMap.getString(chartRenderer.getClass().getName());
            state.setChart(generateContent(request, snippet, model));
        }
    }

    protected void setMessages(BasePointState state, HttpServletRequest request, String snippet,
            Map<String, Object> model) {
        state.setMessages(generateContent(request, snippet + ".jsp", model).trim());
    }

    /**
     * Allows the setting of a given data point. Used by the watch list and point details pages. Views implement their
     * own version to accommodate anonymous users.
     *
     * @param pointId
     * @param valueStr
     * @return
     */
    @MethodFilter
    public int setPoint(int pointId, int componentId, String valueStr) {
        User user = common.getUser();
        DataPointVO point = new DataPointDao().getDataPoint(pointId);

        // Check permissions.
        permissions.ensureDataPointSetPermission(user, point);

        setPointImpl(point, valueStr, user);
        return componentId;
    }

    protected void setPointImpl(DataPointVO point, String valueStr, SetPointSource source) {
        if (point == null) {
            return;
        }

        if (valueStr == null) {
            runtimeManager.relinquish(point.getId());
        } else {
            // Convert the string value into an object.
            MangoValue value = MangoValue.stringToValue(valueStr, point.getPointLocator().getMangoDataType());
            runtimeManager.setDataPointValue(point.getId(), value, source);
        }
    }

    @MethodFilter
    public void forcePointRead(int pointId) {
        User user = common.getUser();
        DataPointVO point = new DataPointDao().getDataPoint(pointId);

        // Check permissions.
        permissions.ensureDataPointReadPermission(user, point);

        runtimeManager.forcePointRead(pointId);
    }

    /**
     * Logs a dataPoint comment after validation.
     *
     * @param dataPointId
     * @param comment
     * @return
     */
    public DataPointComment addDataPointComment(int dataPointId, String comment) {
        if (StringUtils.isEmpty(comment)) {
            return null;
        }

        User user = common.getUser();
        DataPointComment dpc = new DataPointComment(user, comment, dataPointId);
        userCommentDao.insertComment(dpc);

        return dpc;
    }

    /**
     * Logs a dataPoint comment after validation.
     *
     * @param eventId
     * @param comment
     * @return
     */
    public EventComment addEventComment(int eventId, String comment) {
        User user = common.getUser();
        EventComment ec = new EventComment(user, comment, eventId);
        userCommentDao.insertComment(ec);

        return ec;
    }

    protected List<DataPointBean> getReadablePoints() {
        User user = common.getUser();

        List<DataPointVO> points = new DataPointDao().getDataPoints(DataPointExtendedNameComparator.instance, false);
        if (!permissions.hasAdmin(user)) {
            List<DataPointVO> userPoints = new ArrayList();
            for (DataPointVO dp : points) {
                if (permissions.hasDataPointReadPermission(user, dp)) {
                    userPoints.add(dp);
                }
            }
            points = userPoints;
        }

        List<DataPointBean> result = new ArrayList();
        for (DataPointVO dp : points) {
            result.add(new DataPointBean(dp));
        }

        return result;
    }

    public Map<String, Object> getDateRangeDefaults(int periodType, int period) {
        Map<String, Object> result = new HashMap();

        // Default the specific date fields.
        DateTime dt = new DateTime();
        result.put("toYear", dt.getYear());
        result.put("toMonth", dt.getMonthOfYear());
        result.put("toDay", dt.getDayOfMonth());
        result.put("toHour", dt.getHourOfDay());
        result.put("toMinute", dt.getMinuteOfHour());
        result.put("toSecond", 0);

        dt = DateUtils.minus(dt, periodType, period);
        result.put("fromYear", dt.getYear());
        result.put("fromMonth", dt.getMonthOfYear());
        result.put("fromDay", dt.getDayOfMonth());
        result.put("fromHour", dt.getHourOfDay());
        result.put("fromMinute", dt.getMinuteOfHour());
        result.put("fromSecond", 0);

        return result;
    }

    protected String getMessage(String key) {
        return I18NUtils.getMessage(getResourceBundle(), key);
    }

    protected String getMessage(LocalizableMessage message) {
        return message.getLocalizedMessage(getResourceBundle());
    }

    protected ResourceBundle getResourceBundle() {
        WebContext webContext = WebContextFactory.get();
        LocalizationContext localizationContext = (LocalizationContext) Config.get(webContext.getHttpServletRequest(),
                Config.FMT_LOCALIZATION_CONTEXT);
        return localizationContext.getResourceBundle();
    }

    public static String generateContent(HttpServletRequest request, String snippet, Map<String, Object> model) {
        try {
            return ContentGenerator.generateContent(request, "/WEB-INF/snippet/" + snippet, model);
        } catch (ServletException | IOException e) {
            throw new ShouldNeverHappenException(e);
        }
    }

    protected List<User> getShareUsers(User excludeUser) {
        List<User> users = new ArrayList();
        for (User u : userDao.getUsers()) {
            if (u.getId() != excludeUser.getId()) {
                users.add(u);
            }
        }
        return users;
    }

    //
    //
    // Charts and data in a time frame
    //
    protected DateTime createDateTime(int year, int month, int day, int hour, int minute, int second, boolean none) {
        DateTime dt = null;
        try {
            if (!none) {
                dt = new DateTime(year, month, day, hour, minute, second, 0);
            }
        } catch (IllegalFieldValueException e) {
            dt = new DateTime();
        }
        return dt;
    }
}
