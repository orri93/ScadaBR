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
package com.serotonin.mango.db.upgrade;

import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jdbc.core.PreparedStatementSetter;

import com.serotonin.ShouldNeverHappenException;
import com.serotonin.mango.Common;
import com.serotonin.mango.view.PointView;
import com.serotonin.mango.view.graphic.AnalogImageSetRenderer;
import com.serotonin.mango.view.graphic.BasicRenderer;
import com.serotonin.mango.view.graphic.BinaryImageSetRenderer;
import com.serotonin.mango.view.graphic.GraphicRenderer;
import com.serotonin.mango.vo.DataPointVO;
import br.org.scadabr.util.SerializationHelper;
import org.springframework.jdbc.core.RowMapper;

/**
 * @author Matthew Lohbihler
 */
@SuppressWarnings("deprecation")
public class Upgrade1_0_1 extends DBUpgrade {
    private final Log log = LogFactory.getLog(getClass());

    @Override
    public void upgrade() throws Exception {
        OutputStream out = createUpdateLogOutputStream("1_0_1");

        // Get the point views from the field mapping version.
        List<PointView> pointViews = ejt.query(POINT_VIEW_SELECT, new PointViewRowMapper());
        for (PointView pv : pointViews) {
            DataPointVO dp = getDataPointFromPointViewId(pv.getId());
            pv.setDataPoint(dp);
        }
        log.info("Retrieved " + pointViews.size() + " point views");

        // Run the first script.
        log.info("Running script");
        runScript(script, out);

        // Save the point views to BLOBs.
        for (PointView pv : pointViews) {
            log.info("Saved point view " + pv.getId());
            insertPointView(pv);
        }

        out.flush();
        out.close();
    }

    @Override
    protected String getNewSchemaVersion() {
        return "1.1.0";
    }

    private static String[] script = { "drop table pointViews;", "create table pointViews (",
            "  id int not null generated by default as identity (start with 1, increment by 1),",
            "  mangoViewId int not null,", "  dataPointId int not null,", "  nameOverride varchar(255),",
            "  settableOverride char(1) not null,", "  bkgdColorOverride varchar(20),", "  x int not null,",
            "  y int not null,", "  grData blob not null", ");",
            "alter table pointViews add constraint pointViewsPk primary key (id);",
            "alter table pointViews add constraint pointViewsFk1 foreign key (mangoViewId) references mangoViews(id);",
            "alter table pointViews add constraint pointViewsFk2 foreign key (dataPointId) references dataPoints(id);", };

    //
    // / Point views.
    //
    private DataPointVO getDataPointFromPointViewId(int pointViewId) {
        int dpid = ejt.queryForInt("select dataPointId from pointViews where id=?", new Object[] { pointViewId });
        DataPointVO dp = new DataPointVO();
        dp.setId(dpid);
        return dp;
    }

    private static final String POINT_VIEW_SELECT = "select id, mangoViewId, nameOverride, settableOverride, x, y, "
            + "  grType, grImageSetId, grDisplayText, grBinaryZeroImage, grBinaryOneImage, grAnalogMin, grAnalogMax "
            + "from pointViews";

    class PointViewRowMapper implements RowMapper<PointView> {
        @SuppressWarnings("synthetic-access")
        public PointView mapRow(ResultSet rs, int rowNum) throws SQLException {
            PointView pv = new PointView();
            int i = 0;

            // Set the command data
            pv.setId(rs.getInt(++i));
            pv.setViewId(rs.getInt(++i));
            pv.setNameOverride(rs.getString(++i));
            pv.setSettableOverride(charToBool(rs.getString(++i)));
            pv.setX(rs.getInt(++i));
            pv.setY(rs.getInt(++i));

            // Set the graphic renderer.
            int grType = rs.getInt(++i);
            String imageSetId = rs.getString(++i);
            boolean displayText = charToBool(rs.getString(++i));
            int binaryZeroImage = rs.getInt(++i);
            int binaryOneImage = rs.getInt(++i);
            double analogMin = rs.getDouble(++i);
            double analogMax = rs.getDouble(++i);

            GraphicRenderer graphicRenderer;
            switch (grType) {
            case GraphicRenderer.TYPE_BASIC:
                graphicRenderer = new BasicRenderer();
                break;
            case GraphicRenderer.TYPE_BINARY_IMAGE_SET:
                graphicRenderer = new BinaryImageSetRenderer(Common.ctx.getImageSet(imageSetId), binaryZeroImage,
                        binaryOneImage, displayText);
                break;
            case GraphicRenderer.TYPE_ANALOG_IMAGE_SET:
                graphicRenderer = new AnalogImageSetRenderer(Common.ctx.getImageSet(imageSetId), analogMin, analogMax,
                        displayText);
                break;
            default:
                throw new ShouldNeverHappenException("Unknown graphical renderer type: " + grType);
            }
            pv.setGraphicRenderer(graphicRenderer);

            return pv;
        }
    }

    private void insertPointView(final PointView pv) {
        ejt.update("insert into pointViews "
                + "  (id, mangoViewId, dataPointId, nameOverride, settableOverride, bkgdColorOverride, x, y, grData) "
                + "values (?,?,?,?,?,?,?,?,?)", new PreparedStatementSetter() {
            public void setValues(PreparedStatement ps) throws SQLException {
                ps.setInt(1, pv.getId());
                ps.setInt(2, pv.getViewId());
                ps.setInt(3, pv.getDataPointId());
                ps.setString(4, pv.getNameOverride());
                ps.setString(5, pv.isSettableOverride() ? "Y" : "N");
                ps.setString(6, pv.getBkgdColorOverride());
                ps.setInt(7, pv.getX());
                ps.setInt(8, pv.getY());
                ps.setBlob(9, SerializationHelper.writeObject(pv.getGraphicRenderer()));
            }
        });
    }
}
