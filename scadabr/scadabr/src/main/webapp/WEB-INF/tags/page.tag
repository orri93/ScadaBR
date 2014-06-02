<%@ taglib prefix="fmt" uri="http://java.sun.com/jstl/fmt_rt" %>
<%@ taglib prefix="dijit" uri="/WEB-INF/tld/dijit.tld" %>
<!DOCTYPE html>
<html>
    <head>
        <link rel="stylesheet" href="resources/scadabr.css">
        <script src='resources/dojo/dojo.js' data-dojo-config="isDebug: true, async: true, parseOnLoad: true"></script>
        <script>
            require(["dojo/parser",
                "dojo/domReady!"]);
        </script>
    </head>

    <body class="soria">
        <style>
            #preloader,
            body, html {
                width:100%; height:100%; margin:0; padding:0;
            }

            #preloader {
                background-color:#fff;
                position:absolute;
            }

            html, body {
                width: 100%;
                height: 100%;
                margin: 0;
                overflow:hidden;
            }

            #mainLayout {
                width: 100%;
                height: 100%;
            }
        </style>
        <!-- Prevents flicker on page load of declarative dijits-->
        <div id="preloader"></div>

        <dijit:headlineLayoutContainer id="mainLayout">
            <dijit:topContentPane >
                <dijit:headlineLayoutContainer>
                    <dijit:leftContentPane>
                        <img src="images/mangoLogoMed.jpg" alt="Logo"/>
                    </dijit:leftContentPane>
                    <dijit:bottomContentPane>
                        <dijit:toolbar>
                            <dijit:button iconClass="scadaBrWatchListIcon" i18nTitle="header.watchlist">
                                <script type="dojo/connect" data-dojo-event="onClick">window.location = "watch_list.shtm";</script>
                            </dijit:button>
                            <dijit:button iconClass="scadaBrEventsIcon" i18nTitle="header.alarms">
                                <script type="dojo/connect" data-dojo-event="onClick">window.location = "events.shtm";</script>
                            </dijit:button>
                            <dijit:button iconClass="scadaBrPointHierarchyIcon" i18nTitle="header.pointHierarchy" >
                                <script type="dojo/connect" data-dojo-event="onClick">window.location = "point_hierarchy.shtm";</script>
                            </dijit:button>
                            <dijit:toolbarSeparator/>
                            <dijit:button iconClass="scadaBrLogoutIcon" i18nTitle="header.logout" >
                                <script type="dojo/connect" data-dojo-event="onClick">window.location = "logout.htm";</script>
                            </dijit:button>
                            <!-- USE style="float:right;" to right align button ...-->

                        </dijit:toolbar>
                    </dijit:bottomContentPane>
                </dijit:headlineLayoutContainer>
            </dijit:topContentPane>
            <dijit:centerContentPane >
                <jsp:doBody />
            </dijit:centerContentPane>
            <dijit:bottomContentPane >
                <span>&copy;2009-2014 Funda&ccedil;&atilde;o Certi, MCA Sistemas, Unis Sistemas, Conetec. <fmt:message key="footer.rightsReserved"/></span>
            </dijit:bottomContentPane>
        </dijit:headlineLayoutContainer>

    </body>
</html>