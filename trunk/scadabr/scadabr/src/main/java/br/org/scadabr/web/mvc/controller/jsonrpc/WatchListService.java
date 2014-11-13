package br.org.scadabr.web.mvc.controller.jsonrpc;
import br.org.scadabr.logger.LogUtils;
import br.org.scadabr.web.l10n.RequestContextAwareLocalizer;
import com.googlecode.jsonrpc4j.JsonRpcService;
import com.serotonin.mango.db.dao.DataPointDao;
import com.serotonin.mango.db.dao.PointValueDao;
import com.serotonin.mango.db.dao.UserDao;
import com.serotonin.mango.db.dao.WatchListDao;
import com.serotonin.mango.rt.RuntimeManager;
import com.serotonin.mango.rt.dataImage.PointValueTime;
import com.serotonin.mango.view.ShareUser;
import com.serotonin.mango.view.chart.TimePeriodChartRenderer;
import com.serotonin.mango.vo.DataPointVO;
import com.serotonin.mango.vo.User;
import com.serotonin.mango.vo.WatchList;
import com.serotonin.mango.vo.permission.Permissions;
import com.serotonin.mango.web.UserSessionContextBean;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

//TODO custom Watchlist scope???
@Named
@JsonRpcService("/rpc/watchlists.json")
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class WatchListService implements Serializable {

    private static final Logger LOG = Logger.getLogger(LogUtils.LOGGER_SCADABR_WEB);

    @Inject
    private transient PointValueDao pointValueDao;
    @Inject
    private transient DataPointDao dataPointDao;
    @Inject
    private transient WatchListDao watchListDao;
    @Inject
    private transient RuntimeManager runtimeManager;
    @Inject
    private transient UserDao userDao;
    @Inject
    private transient UserSessionContextBean userSessionContextBean;
    @Inject
    private transient RequestContextAwareLocalizer localizer;

    public JsonWatchList getWatchList(int watchlistId, HttpServletRequest request) {
        return new JsonWatchList(watchListDao.getWatchList(watchlistId), dataPointDao, runtimeManager, localizer);
    }

    public JsonWatchList getSelectedWatchlist(HttpServletRequest request) {
        return new JsonWatchList(watchListDao.getWatchList(userSessionContextBean.getUser().getSelectedWatchList()), dataPointDao, runtimeManager, localizer);
    }

    public JsonWatchList addPointToWatchlist(int watchlistId, int index, int dataPointId, HttpServletRequest request) {
        LOG.warning("ENTER addPointToWatchlist");
        final User user = userSessionContextBean.getUser();
        DataPointVO point = dataPointDao.getDataPoint(dataPointId);
        if (point == null) {
            return null;
        }
        WatchList watchList = watchListDao.getWatchList(watchlistId);

        // Check permissions.
        Permissions.ensureDataPointReadPermission(user, point);
        Permissions.ensureWatchListEditPermission(user, watchList);

        // Add it to the watch list.
        watchList.getPointList().add(index, point);
        watchListDao.saveWatchList(watchList);
        updateSetPermission(point, watchList.getUserAccess(user), userDao.getUser(watchList.getUserId()));
        LOG.log(Level.WARNING, "ENTER addPointToWatchlist {0}", watchListDao.getWatchList(watchlistId).getName());
        return new JsonWatchList(watchListDao.getWatchList(watchlistId), dataPointDao, runtimeManager, localizer);
    }

    private void updateSetPermission(DataPointVO point, int access, User owner) {
        // Point isn't settable
        if (!point.getPointLocator().isSettable()) {
            return;
        }

        // Read-only access
        if (access != ShareUser.ACCESS_OWNER && access != ShareUser.ACCESS_SET) {
            return;
        }

        // Watch list owner doesn't have set permission
        if (!Permissions.hasDataPointSetPermission(owner, point)) {
            return;
        }

        // All good.
        point.setSettable(true);
    }

    public JsonWatchList deletePointFromWatchlist(int watchlistId, int dataPointId, HttpServletRequest request) {
        LOG.warning("ENTER deletePointFromWatchlist");
        final User user = userSessionContextBean.getUser();
        DataPointVO point = dataPointDao.getDataPoint(dataPointId);
        if (point == null) {
            return null;
        }
        WatchList watchList = watchListDao.getWatchList(watchlistId);

        // Check permissions.
        Permissions.ensureDataPointReadPermission(user, point);
        Permissions.ensureWatchListEditPermission(user, watchList);

        //remove
        for (DataPointVO dp : watchList) {
            if (dp.getId() == dataPointId) {
                watchList.getPointList().remove(dp);
                break;
            }
        }
        watchListDao.saveWatchList(watchList);
        updateSetPermission(point, watchList.getUserAccess(user), userDao.getUser(watchList.getUserId()));
        LOG.log(Level.WARNING, "Exit deletePointFromWatchlist {0}", watchListDao.getWatchList(watchlistId).getName());
        return new JsonWatchList(watchListDao.getWatchList(watchlistId), dataPointDao, runtimeManager, localizer);
    }

    public JsonChartDataSet getChartDataSet(int dataPointId) {
        DataPointVO dp = dataPointDao.getDataPoint(dataPointId);
        if (dp.getChartRenderer() instanceof TimePeriodChartRenderer) {
            final TimePeriodChartRenderer tpcr = (TimePeriodChartRenderer)dp.getChartRenderer();
            final long timeStamp = System.currentTimeMillis();
            final long from = tpcr.getStartTime(timeStamp);
            final long to = tpcr.getEndTime(timeStamp);
            List<PointValueTime> pvt = pointValueDao.getPointValuesBetween(dataPointId, from, to);
            return new JsonChartDataSet(from, to, dp, pvt);
        }
        return new JsonChartDataSet();
    }

    
}