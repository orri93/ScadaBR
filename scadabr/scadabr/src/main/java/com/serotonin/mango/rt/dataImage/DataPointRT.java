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
package com.serotonin.mango.rt.dataImage;

import br.org.scadabr.DataType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import br.org.scadabr.dao.PointValueDao;
import br.org.scadabr.dao.SystemSettingsDao;
import br.org.scadabr.logger.LogUtils;
import br.org.scadabr.rt.SchedulerPool;
import br.org.scadabr.rt.event.schedule.ScheduledEventManager;
import com.serotonin.mango.rt.RuntimeManager;
import com.serotonin.mango.rt.dataSource.PointLocatorRT;
import com.serotonin.mango.rt.event.detectors.PointEventDetectorRT;
import com.serotonin.mango.util.timeout.RunClient;
import com.serotonin.mango.vo.DataPointVO;
import com.serotonin.mango.vo.event.DoublePointEventDetectorVO;
import br.org.scadabr.timer.cron.EventRunnable;
import br.org.scadabr.util.ILifecycle;
import com.serotonin.mango.rt.EventManager;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

//TODO split tist to datatypes Double ....
@Configurable
public abstract class DataPointRT<T extends PointValueTime> implements IDataPoint<T>, ILifecycle, RunClient {

    protected static final Logger LOG = Logger.getLogger(LogUtils.LOGGER_SCADABR_CORE);
    private static final PvtTimeComparator pvtTimeComparator = new PvtTimeComparator();

    // Configuration data.
    protected final DataPointVO<T> vo;
    protected final PointLocatorRT pointLocator;

    // Runtime data.
    protected volatile T pointValue;
    @Autowired
    protected RuntimeManager runtimeManager;
    @Autowired
    protected ScheduledEventManager scheduledEventManager;
    @Autowired
    protected EventManager eventManager;
    @Autowired
    protected PointValueDao pointValueDao;
    @Autowired
    protected SystemSettingsDao systemSettingsDao;
    @Autowired
    private SchedulerPool schedulerPool;
    private List<PointEventDetectorRT> detectors;
    private final Map<String, Object> attributes = new HashMap<>();

    public DataPointRT(DataPointVO<T> vo, PointLocatorRT pointLocator) {
        this.vo = vo;
        this.pointLocator = pointLocator;
    }

    /**
     * This method should only be called by the data source. Other types of
     * point setting should include a set point source object so that the
     * annotation can be logged.
     *
     * @param newValue
     */
    @Override
    public void updatePointValueAsync(T newValue) {
        savePointValueAsync(newValue, null);
    }

    @Override
    public void updatePointValueSync(T newValue) {
        savePointValueSync(newValue, null);
    }

    /**
     * Use this method to update a data point for reasons other than just data
     * source update.
     *
     * @param newValue the value to set
     * @param source the source of the set. This can be a user object if the
     * point was set from the UI, or could be a program run by schedule or on
     * event.
     */
    @Override
    public void setPointValueSync(T newValue, SetPointSource source) {
            savePointValueSync(newValue, source);
    }

    @Override
    public void setPointValueAsync(T newValue) {
            savePointValueAsync(newValue, null);
    }

    protected abstract void savePointValueAsync(T newValue, SetPointSource source);
    protected abstract void savePointValueSync(T newValue, SetPointSource source);

    //
    // /
    // / Properties
    // /
    //
    public int getId() {
        return vo.getId();
    }

    @Override
    public T getPointValue() {
        return pointValue;
    }

    @SuppressWarnings("unchecked")
    public <T extends PointLocatorRT> T getPointLocator() {
        return (T) pointLocator;
    }

    public DataPointVO getVo() {
        return vo;
    }

    public String getVoName() {
        return vo.getName();
    }

    @Override
    public DataType getDataType() {
        return vo.getDataType();
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + getId();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DataPointRT other = (DataPointRT) obj;
        return getId() == other.getId();
    }

    @Override
    public String toString() {
        return "DataPointRT(id=" + getId() + ", name=" + vo.getName() + ")";
    }

    //
    // /
    // / Listeners
    // /
    //
    protected void fireEvents(PointValueTime oldValue, PointValueTime newValue, boolean set, boolean backdate) {
        DataPointListener l = runtimeManager.getDataPointListeners(vo.getId());
        if (l != null) {
            schedulerPool.execute(new EventNotifyWorkItem(l, oldValue, newValue, set, backdate));
        }
    }

    class EventNotifyWorkItem implements EventRunnable {

        private final DataPointListener listener;
        private final PointValueTime oldValue;
        private final PointValueTime newValue;
        private final boolean set;
        private final boolean backdate;

        EventNotifyWorkItem(DataPointListener listener, PointValueTime oldValue, PointValueTime newValue, boolean set,
                boolean backdate) {
            this.listener = listener;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.set = set;
            this.backdate = backdate;
        }

        @Override
        public void run() {
            if (backdate) {
                listener.pointBackdated(newValue);
            } else {
                // Always fire this.
                listener.pointUpdated(newValue);

                // Fire if the point has changed.
                if (!PointValueTime.equalValues(oldValue, newValue)) {
                    listener.pointChanged(oldValue, newValue);
                }

                // Fire if the point was set.
                if (set) {
                    listener.pointSet(oldValue, newValue);
                }
            }
        }
        /*
         @Override
         public int getPriority() {
         return WorkItem.PRIORITY_MEDIUM;
         }
         */
    }

    //
    //
    // Lifecycle
    //
    @Override
    public void initialize() {
        // Get the latest value for the point from the database.
        pointValue = pointValueDao.getLatestPointValue(vo);

        // Add point event listeners
        for (DoublePointEventDetectorVO ped : vo.getEventDetectors()) {
            if (detectors == null) {
                detectors = new ArrayList<>();
            }

            PointEventDetectorRT pedRT = ped.createRuntime();
            detectors.add(pedRT);
            scheduledEventManager.addPointEventDetector(pedRT);
            runtimeManager.addDataPointListener(vo.getId(), pedRT);
        }
    }

    @Override
    public void terminate() {

        //TODO notify runtimeManger and lat them handle this???
        if (detectors != null) {
            for (PointEventDetectorRT pedRT : detectors) {
                runtimeManager.removeDataPointListener(vo.getId(), pedRT);
                scheduledEventManager.removePointEventDetector(pedRT.getEventDetectorKey());
            }
        }
        //TODO notify runtimeManger and lat them handle this???
        eventManager.cancelEventsForDataPoint(vo.getId());
    }

    @Override
    public void joinTermination() {
        // no op
    }
    
    @Override
    public void updatePointValue(T newValue) {
        savePointValueAsync(pointValue, null);
    }

    @Override
    public void setPointValue(T newValue) {
        savePointValueAsync(pointValue, null);
    }

}
