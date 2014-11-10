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
package com.serotonin.mango.rt.link;

import br.org.scadabr.DataType;
import br.org.scadabr.rt.event.type.DuplicateHandling;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptException;

import com.serotonin.mango.Common;
import com.serotonin.mango.rt.dataImage.DataPointListener;
import com.serotonin.mango.rt.dataImage.DataPointRT;
import com.serotonin.mango.rt.dataImage.IDataPoint;
import com.serotonin.mango.rt.dataImage.PointValueTime;
import com.serotonin.mango.rt.dataImage.SetPointSource;
import com.serotonin.mango.rt.dataSource.meta.ResultTypeException;
import com.serotonin.mango.rt.dataSource.meta.ScriptExecutor;
import com.serotonin.mango.rt.event.type.SystemEventType;
import com.serotonin.mango.rt.maint.work.SetPointWorkItem;
import com.serotonin.mango.vo.link.PointLinkVO;
import br.org.scadabr.utils.i18n.LocalizableMessage;
import br.org.scadabr.utils.i18n.LocalizableMessageImpl;
import br.org.scadabr.vo.event.type.SystemEventSource;
import com.serotonin.mango.rt.RuntimeManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * @author Matthew Lohbihler
 */
@Configurable
public class PointLinkRT implements DataPointListener, SetPointSource {

    public static final String CONTEXT_VAR_NAME = "source";
    private final PointLinkVO vo;
    private final SystemEventType eventType;
    @Autowired
    private RuntimeManager runtimeManager;

    public PointLinkRT(PointLinkVO vo) {
        this.vo = vo;
        eventType = new SystemEventType(SystemEventSource.POINT_LINK_FAILURE, vo.getId(),
                DuplicateHandling.IGNORE_SAME_MESSAGE);
    }

    public void initialize() {
        runtimeManager.addDataPointListener(vo.getSourcePointId(), this);
        checkSource();
    }

    public void terminate() {
        runtimeManager.removeDataPointListener(vo.getSourcePointId(), this);
        returnToNormal();
    }

    public int getId() {
        return vo.getId();
    }

    private void checkSource() {
        DataPointRT source = runtimeManager.getDataPoint(vo.getSourcePointId());
        if (source == null) // The source has been terminated, was never enabled, or not longer exists.
        {
            raiseFailureEvent(new LocalizableMessageImpl("event.pointLink.sourceUnavailable"));
        } else // Everything is good
        {
            returnToNormal();
        }
    }

    private void raiseFailureEvent(LocalizableMessage message) {
        raiseFailureEvent(System.currentTimeMillis(), message);
    }

    private void raiseFailureEvent(long time, LocalizableMessage message) {
        eventType.fire(time, message);
    }

    private void returnToNormal() {
        eventType.clearAlarm();
    }

    private void execute(PointValueTime newValue) {
        // Propagate the update to the target point. Validate that the target point is available.
        DataPointRT targetPoint = runtimeManager.getDataPoint(vo.getTargetPointId());
        if (targetPoint == null) {
            raiseFailureEvent(newValue.getTime(), new LocalizableMessageImpl("event.pointLink.targetUnavailable"));
            return;
        }

        if (!targetPoint.getPointLocator().isSettable()) {
            raiseFailureEvent(newValue.getTime(), new LocalizableMessageImpl("event.pointLink.targetNotSettable"));
            return;
        }

        DataType targetDataType = targetPoint.getDataType();

        if (!vo.getScript().isEmpty()) {
            ScriptExecutor scriptExecutor = new ScriptExecutor();
            Map<String, IDataPoint> context = new HashMap<>();
            DataPointRT source = runtimeManager.getDataPoint(vo.getSourcePointId());
            context.put(CONTEXT_VAR_NAME, source);

            try {
                PointValueTime pvt = scriptExecutor.execute(vo.getScript(), context, newValue.getTime(),
                        targetDataType, newValue.getTime());
                if (pvt.getValue() == null) {
                    raiseFailureEvent(newValue.getTime(), new LocalizableMessageImpl("event.pointLink.nullResult"));
                    return;
                }
                newValue = pvt;
            } catch (ScriptException e) {
                raiseFailureEvent(newValue.getTime(), new LocalizableMessageImpl("common.default", e.getMessage()));
                return;
            } catch (ResultTypeException e) {
                raiseFailureEvent(newValue.getTime(), e);
                return;
            }
        }

        if (newValue.getDataType() != targetDataType) {
            raiseFailureEvent(newValue.getTime(), new LocalizableMessageImpl("event.pointLink.convertError"));
            return;
        }

        // Queue a work item to perform the update.
        Common.ctx.getBackgroundProcessing().addWorkItem(new SetPointWorkItem(vo.getTargetPointId(), newValue, this));
        returnToNormal();
    }

    //
    // /
    // / DataPointListener
    // /
    //
    @Override
    public void pointInitialized() {
        checkSource();
    }

    @Override
    public void pointTerminated() {
        checkSource();
    }

    @Override
    public void pointChanged(PointValueTime oldValue, PointValueTime newValue) {
        if (vo.getEvent() == PointLinkVO.EVENT_CHANGE) {
            execute(newValue);
        }
    }

    @Override
    public void pointSet(PointValueTime oldValue, PointValueTime newValue) {
        // No op
    }

    @Override
    public void pointBackdated(PointValueTime value) {
        // No op
    }

    @Override
    public void pointUpdated(PointValueTime newValue) {
        if (vo.getEvent() == PointLinkVO.EVENT_UPDATE) {
            execute(newValue);
        }
    }

    //
    // /
    // / SetPointSource
    // /
    //
    @Override
    public int getSetPointSourceId() {
        return vo.getId();
    }

    @Override
    public int getSetPointSourceType() {
        return SetPointSource.Types.POINT_LINK;
    }

    @Override
    public void raiseRecursionFailureEvent() {
        raiseFailureEvent(new LocalizableMessageImpl("event.pointLink.recursionFailure"));
    }
}
