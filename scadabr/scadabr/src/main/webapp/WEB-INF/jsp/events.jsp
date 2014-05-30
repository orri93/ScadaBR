<%--
    Mango - Open Source M2M - http://mango.serotoninsoftware.com
    Copyright (C) 2006-2011 Serotonin Software Technologies Inc.
    @author Matthew Lohbihler
    y
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see http://www.gnu.org/licenses/.
--%>
<%@ taglib prefix="tag" tagdir="/WEB-INF/tags" %>
<%@ include file="/WEB-INF/jsp/include/tech.jsp" %>
<%@page import="com.serotonin.mango.vo.UserComment"%>
<%@page import="com.serotonin.mango.rt.event.type.EventType"%>
<%@page import="com.serotonin.mango.web.dwr.EventsDwr"%>
<tag:page>
    <!--%@ include file="/WEB-INF/jsp/include/userComment.jsp" %-->
    <style>
        .incrementControl { width: 2em; }
        .dgrid-column-id {
            width: 10em;
        }
        .dgrid-column-alarmLevel {
            width: 5em;
        }
        .dgrid-column-activeTimestamp {
            width: 10em;
        }
        .dgrid-column-acknowledged {
            width: 10em;
        }
        .dgrid-row-odd {
            background: #F2F5F9;
        }
    </style>
    <script type="text/javascript">

        //Todo make pagination work with jsonrest
        require([
            "dojo/dom",
            "dojo/dom-construct",
            "dojo/_base/declare",
            "dojo/request",
            "dojo/store/Memory",
            "dojo/store/Observable",
            "dgrid/Grid",
            "dgrid/extensions/Pagination",
            "dgrid/Keyboard",
            "dgrid/Selection",
            "dojo/rpc/JsonService",
            "dojo/on",
            "dijit/form/Button",
            "dijit/registry",
            "dojo/domReady!"
        ], function(dom, domConstruct, declare, request, Memory, Observable, Grid, Pagination, Keyboard, Selection, JsonService, on, Button, registry) {
            var grid;
            var svc;
            request("events/", {
                handleAs: "json"
            }).then(function(response) {
                // Once the response is received, build an in-memory store
                // with the data
                var store = new Memory({data: response});
                // Create a Grid instance using Pagination,
                // referencing the store
                grid = new (declare([Grid, Pagination, Keyboard, Selection]))({
                    store: store,
                    columns: {
                        id: {
                            label: "<fmt:message key="events.id"/>"
                        },
                        alarmLevel: {
                            label: "<fmt:message key="common.alarmLevel"/>",
                            renderCell: function(event, alarmLevel, default_node, options) {
                                var node = domConstruct.create("img");
                                var imgName;
                                switch (alarmLevel) {
                                    case 1:
                                        imgName = 'flag_blue';
                                        if (event.active) {
                                            node.alt = '<fmt:message key="common.alarmLevel.info"/>';
                                        } else {
                                            node.alt = '<fmt:message key="common.alarmLevel.info.rtn"/>';
                                        }
                                        break;
                                    case  2:
                                        imgName = 'flag_yellow';
                                        if (event.active) {
                                            node.alt = '<fmt:message key="common.alarmLevel.urgent"/>';
                                        } else {
                                            node.alt = '<fmt:message key="common.alarmLevel.urgent.rtn"/>';
                                        }
                                        break;
                                    case  3:
                                        if (event.active) {
                                            node.alt = '<fmt:message key="common.alarmLevel.critical"/>';
                                        } else {
                                            node.alt = '<fmt:message key="common.alarmLevel.critical.rtn"/>';
                                        }
                                        imgName = 'flag_orange';
                                        break;
                                    case  4:
                                        if (event.active) {
                                            node.alt = '<fmt:message key="common.alarmLevel.lifeSafety"/>';
                                        } else {
                                            node.alt = '<fmt:message key="common.alarmLevel.lifeSafety.rtn"/>';
                                        }
                                        imgName = 'flag_red';
                                        break;
                                    default :
                                        node.alt = alarmLevel;
                                        return  node;
                                }
                                node.src = 'images/' + imgName + (event.active ? '' : '_off') + '.png';
                                node.title = node.alt;
                                return node;
                            }
                        },
                        activeTimestamp: {
                            label: '<fmt:message key="common.time"/>',
                            resizable: true
                        },
                        message: {
                            label: "Message",
                            resizable: true,
                            formatter: function(msg) {
                                return msg;
                            }
                        },
                        rtnTimestamp: {
                            label: '<fmt:message key="common.inactiveTime"/>',
                            renderCell: function(event, timestamp, default_node, options) {
                                var node = domConstruct.create("div");

                                if (event.active) {
                                    node.innerHTML = '<fmt:message key="common.active"/>';
                                    var img = domConstruct.create("img", null, node);
                                    img.src = "images/flag_white.png";
                                    img.title = '<fmt:message key="common.active"/>';
                                    /*                                    
                                     on(img, "click", function(evt){
                                     console.log("CKLICKED: " + evt);
                                     });
                                     */
                                } else {
                                    if (!event.rtnApplicable) {
                                        node.innerHTML = '<fmt:message key="common.nortn"/>';
                                    } else {
                                        node.innerHTML = timestamp + ' - ' + event.rtnMessage;

                                    }
                                }
                                return node;
                            }
                        },
                        acknowledged: {
                            label: '',
                            renderCell: function(event, acknowledged, default_node, options) {
                                var myIconClass;
                                var myLabel;
                                if (acknowledged) {
                                    myIconClass = 'scadaBrCantDoActionIcon';
                                    myLabel = '<fmt:message key="events.acknowledged"/>';
                                } else {
                                    myIconClass = 'scadaBrDoActionIcon';
                                    myLabel = '<fmt:message key="events.acknowledge"/>';
                                }

                                var btnAck = new Button({
                                    eventId: event.id,
                                    showLabel: false,
                                    iconClass: myIconClass,
                                    label: myLabel,
                                    onClick: function() {
                                        svc.acknowledgePendingEvent(this.eventId).then(function(result) {
                                            grid.setStore(new Memory({data: result}));
                                        });
                                    }
                                }, default_node.appendChild(document.createElement("div")));
                                btnAck._destroyOnRemove = true;

                                var btnSilence = new Button({
                                    eventId: event.id,
                                    label: "Sil",
                                    iconClass: 'scadaBrDoActionIcon',
                                    showLabel: true
                                }, default_node.appendChild(document.createElement("div")));
                                btnSilence._destroyOnRemove = true;

                                //  return node;
                                /*
                                 
                                 var img = domConstruct.create("img");
                                 
                                 if (acknowledged) {
                                 img.src = "images/tick_off.png";
                                 img.alt = '<fmt:message key="events.acknowledged"/>';
                                 } else {
                                 img.src = "images/tick.png";
                                 img.alt = '<fmt:message key="events.acknowledge"/>';
                                 }
                                 img.title = img.alt;
                                 
                                 return img;
                                 */
                            }
                        }
                    },
                    loadingMessage: "Loading data...",
                    noDataMessage: "No results found.",
                    selectionMode: "single", // for Selection; only select a single row at a time
                    //cellNavigation: false, // for Keyboard; allow only row-level keyboard navigation
                    pagingLinks: 1,
                    pagingTextBox: true,
                    firstLastArrows: true,
                    pageSizeOptions: [10, 25, 50, 100]
                }, "pendingAlarms");

                //TODO move smd to server ...
                svc = new JsonService({
                    serviceUrl: 'jsonrpc/events.json', // Adress of the RPC service end point
                    timeout: 1000,
                    strictArgChecks: true,
                    methods: [{
                            name: 'acknowledgePendingEvent',
                            parameters: [
                                {
                                    name: 'id',
                                    type: 'INTEGER'
                                }
                            ]
                        },
                        {
                            name: 'acknowledgeAllPendingEvents',
                            parameters: []
                        }

                    ]
                });


                on(registry.byId("btnAcknowledgeAll").domNode, "click", function() {
                    svc.acknowledgeAllPendingEvents().then(function(result) {
                        grid.setStore(new Memory({data: result}));
                    });
                });

                grid.on("dgrid-error", function(event) {
                    console.log(event.error.message);
                });

            });
        });

        /*
         require([
         "dojo/_base/declare",
         "dojo/store/JsonRest",
         "dgrid/Grid",
         "dgrid/extensions/Pagination",
         "dgrid/Keyboard",
         "dgrid/Selection",
         "dojo/domReady!"
         ], function(declare, JsonRest, Grid, Pagination, Keyboard, Selection) {
         myRestStore = new JsonRest({
         target: "events/"
         });
         
         // Create a Grid instance using Pagination,
         // referencing the store
         var grid = new (declare([Grid, Pagination, Keyboard, Selection]))({
         store: myRestStore,
         columns: {
         id: "ID",
         alarmLevel: "AlarmLevel",
         activeTimestamp: "Active timestamp",
         rtnTimestamp: "Rtn Timestamp",
         message: "Message"
         },
         loadingMessage: "Loading data...",
         noDataMessage: "No results found.",
         selectionMode: "single", // for Selection; only select a single row at a time
         cellNavigation: false, // for Keyboard; allow only row-level keyboard navigation
         pagingLinks: 1,
         pagingTextBox: true,
         firstLastArrows: true,
         pageSizeOptions: [10, 25, 50, 100]
         }, "pendingAlarms");
         
         grid.on("dgrid-select", function(event) {
         // Report the item from the selected row to the console.
         console.log("Row selected: ", event.rows[0].data);
         });
         grid.on("dgrid-deselect", function(event) {
         console.log("Row de-selected: ", event.rows[0].data);
         });
         grid.on(".dgrid-row:click", function(event) {
         var row = grid.row(event);
         console.log("Row clicked:", row.id);
         });
         });
         */



        /*
         // Tell the log poll that we're interested in monitoring pending alarms.
         mango.longPoll.pollRequest.pendingAlarms = true;
         dojo.requireLocalization("dojo.i18n.calendar", "gregorian", null, "de,en,es,fi,fr,ROOT,hu,it,ja,ko,nl,pt,pt-br,sv,zh,zh-cn,zh-hk,zh-tw");
         dojo.requireLocalization("dojo.i18n.calendar", "gregorianExtras", null, "ROOT,ja,zh");
         
         function updatePendingAlarmsContent(content) {
         hide("hourglass");
         
         $set("pendingAlarms", content);
         if (content) {
         show("ackAllDiv");
         hide("noAlarms");
         } else {
         $set("pendingAlarms", "");
         hide("ackAllDiv");
         show("noAlarms");
         }
         }
         
         function doSearch(page, date) {
         setDisabled("searchBtn", true);
         $set("searchMessage", "<fmt:message key="events.search.searching"/>");
         EventsDwr.search($get("eventId"), $get("eventSourceType"), $get("eventStatus"), $get("alarmLevel"),
         $get("keywords"), page, date, function(results) {
         $set("searchResults", results.data.content);
         setDisabled("searchBtn", false);
         $set("searchMessage", results.data.resultCount);
         });
         }
         
         function jumpToDate(parent) {
         var div = $("datePickerDiv");
         var bounds = getAbsoluteNodeBounds(parent);
         div.style.top = bounds.y +"px";
         div.style.left = bounds.x +"px";
         var x = dojo.widget.byId("datePicker");
         x.show();
         }
         
         var dptimeout = null;
         function expireDatePicker() {
         dptimeout = setTimeout(function() { dojo.widget.byId("datePicker").hide(); }, 500);
         }
         
         function cancelDatePickerExpiry() {
         if (dptimeout) {
         clearTimeout(dptimeout);
         dptimeout = null;
         }
         }
         
         function jumpToDateClicked(date) {
         var x = dojo.widget.byId("datePicker");
         if (x.isShowing()) {
         x.hide();
         doSearch(0, date);
         }
         }
         
         function newSearch() {
         var x = dojo.widget.byId("datePicker");
         x.setDate(x.today);
         doSearch(0);
         }
         
         function silenceAll() {
         MiscDwr.silenceAll(function(result) {
         var silenced = result.data.silenced;
         for (var i=0; i<silenced.length; i++)
         setSilenced(silenced[i], true);
         });
         }
         
         dojo.addOnLoad(function() {
         var x = dojo.widget.byId("datePicker");
         x.hide();
         x.setDate(x.today);
         dojo.event.connect(x,'onValueChanged','jumpToDateClicked');
         });
         */
    </script>


    <div>
        <div class="smallTitle titlePadding" style="float:left;">
            <tag:img png="flag_white" title="events.alarms"/>
            <fmt:message key="events.pending"/>
        </div>

        <div id="ackAllDiv" class="titlePadding" style="float:right;">
            <button id="btnAcknowledgeAll" data-dojo-type="dijit/form/Button" data-dojo-props="iconClass:'scadaBrDoActionIcon'"><fmt:message key="events.acknowledgeAll"/></button>
            <button data-dojo-type="dijit/form/Button" data-dojo-props="iconClass:'scadaBrDoSilenceIcon'"><fmt:message key="events.silenceAll"/></button>
        </div>
        <div id="pendingAlarms" style="clear:both;"/>
    </div>
</div>

<div class="smallTitle titlePadding"><fmt:message key="events.search"/></div>

<div data-dojo-type="dijit/form/Form" id="myForm" data-dojo-id="myForm"
     encType="multipart/form-data" >
    <div data-dojo-type="dojox/layout/TableContainer" data-dojo-props="cols:1" id="tc1">
        <input data-dojo-type="dijit.form.TextBox" name="eventId" title="<fmt:message key="events.id"/>:"/>

        <select id="eventSourceType" data-dojo-type="dijit/form/Select" title="<fmt:message key="events.search.type"/>">
            <option value="-1"><fmt:message key="common.all"/></option>
            <option value="<c:out value="<%= EventType.EventSources.DATA_POINT%>"/>"><fmt:message key="eventHandlers.pointEventDetector"/></option>
            <option value="<c:out value="<%= EventType.EventSources.SCHEDULED%>"/>"><fmt:message key="scheduledEvents.ses"/></option>
            <option value="<c:out value="<%= EventType.EventSources.COMPOUND%>"/>"><fmt:message key="compoundDetectors.compoundEventDetectors"/></option>
            <option value="<c:out value="<%= EventType.EventSources.DATA_SOURCE%>"/>"><fmt:message key="eventHandlers.dataSourceEvents"/></option>
            <option value="<c:out value="<%= EventType.EventSources.PUBLISHER%>"/>"><fmt:message key="eventHandlers.publisherEvents"/></option>
            <option value="<c:out value="<%= EventType.EventSources.MAINTENANCE%>"/>"><fmt:message key="eventHandlers.maintenanceEvents"/></option>
            <option value="<c:out value="<%= EventType.EventSources.SYSTEM%>"/>"><fmt:message key="eventHandlers.systemEvents"/></option>
            <option value="<c:out value="<%= EventType.EventSources.AUDIT%>"/>"><fmt:message key="eventHandlers.auditEvents"/></option>
        </select>
        <select id="eventStatus" data-dojo-type="dijit/form/Select" title="<fmt:message key="common.status"/>">
            <option value="<c:out value="<%= EventsDwr.STATUS_ALL%>"/>"><fmt:message key="common.all"/></option>
            <option value="<c:out value="<%= EventsDwr.STATUS_ACTIVE%>"/>"><fmt:message key="common.active"/></option>
            <option value="<c:out value="<%= EventsDwr.STATUS_RTN%>"/>"><fmt:message key="event.rtn.rtn"/></option>
            <option value="<c:out value="<%= EventsDwr.STATUS_NORTN%>"/>"><fmt:message key="common.nortn"/></option>
        </select>
        <select id="alarmLevel" data-dojo-type="dijit/form/Select" title="<fmt:message key="common.alarmLevel"/>"><tag:alarmLevelOptions allOption="true"/></select>
        <input id="keywords" data-dojo-type="dijit/form/TextBox" title="<fmt:message key="events.search.keywords"/>"/>
        <button data-dojo-type="dijit/form/Button"><fmt:message key="events.search.search"/></button>
        <span id="searchMessage" class="formError"></span>
    </div>
    <div id="searchResults"></div>
</div>
<!--div id="datePickerDiv" style="position:absolute; top:0px; left:0px;" onmouseover="cancelDatePickerExpiry()" onmouseout="expireDatePicker()">
    <div widgetId="datePicker" dojoType="datepicker" dayWidth="narrow" lang="${lang}"></div>
</div-->

</tag:page>