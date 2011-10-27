package br.org.scadabr.tests;

/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006-2007 inavare GmbH (http://inavare.com)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.da.AccessBase;
import org.openscada.opc.lib.da.Async20Access;
import org.openscada.opc.lib.da.Server;
import org.openscada.utils.timing.Scheduler;

/**
 * Another test showing the "Access" interface with the Async20Access
 * implementation.
 * 
 * @author Jens Reimann <jens.reimann@inavare.net>
 * 
 */
public class OPCTest4 {
	private static Logger _log = Logger.getLogger(OPCTest4.class);

	public static void main(String[] args) throws Throwable {
		// create connection information
		ConnectionInformation ci = new ConnectionInformation();
		ci.setHost(ServerInfo.HOST);
		ci.setDomain(ServerInfo.DOMAIN);
		ci.setUser(ServerInfo.USER);
		ci.setPassword(ServerInfo.PASSWORD);
		ci.setClsid(ServerInfo.CLSID);

		Set<String> items = new HashSet<String>();
		for (int i = 5; i < args.length; i++) {
			items.add(args[i]);
		}
		if (items.isEmpty()) {
			items.add("Saw-toothed Waves.Int2");
		}

		// create a new server
		Server server = new Server(ci, new Scheduler(true));
		try {
			// connect to server
			server.connect();

			// add sync access
			AccessBase access = new Async20Access(server, 100, false);
			for (String itemId : items) {
				access.addItem(itemId, new DataCallbackDumper());
			}

			// start reading
			access.bind();

			// wait a little bit
			_log.info("Sleep for some seconds to give events a chance...");
			Thread.sleep(10 * 1000);
			_log.info("Returned from sleep");

			// stop reading
			access.unbind();
		} catch (JIException e) {
			System.out.println(String.format("%08X: %s", e.getErrorCode(),
					server.getErrorMessage(e.getErrorCode())));
		}
	}
}
