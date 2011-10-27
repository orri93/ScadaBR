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

import java.util.Collection;

import org.jinterop.dcom.common.JIException;
import org.openscada.opc.dcom.list.ClassDetails;
import org.openscada.opc.lib.list.Categories;
import org.openscada.opc.lib.list.Category;
import org.openscada.opc.lib.list.ServerList;

/**
 * A sample that queries the server browser interface
 * 
 * @author Jens Reimann &lt;jens.reimann@inavare.net&gt;
 * 
 */
public class OPCTest8 {
	protected static void showDetails(ServerList serverList, String clsid)
			throws JIException {
		ClassDetails cd = serverList.getDetails(clsid);
		if (cd != null) {
			System.out.println(cd.getProgId() + " = " + cd.getDescription());
		} else {
			System.out.println("unknown");
		}
	}

	@SuppressWarnings("unused")
	public static void main(String[] args) throws Throwable {
		ServerList serverList = new ServerList(ServerInfo.HOST,
				ServerInfo.USER, ServerInfo.PASSWORD, ServerInfo.DOMAIN);

		String cls = serverList.getClsIdFromProgId("Matrikon.OPC.Simulation.1");
		System.out.println("Matrikon OPC Simulation Server: " + cls);
		showDetails(serverList, cls);

		Collection<ClassDetails> detailsList = serverList
				.listServersWithDetails(
						new Category[] { Categories.OPCDAServer20 },
						new Category[] {});

		for (ClassDetails details : detailsList) {
			System.out.println(String.format("Found: %s", details.getClsId()));
			System.out.println(String.format("\tProgID: %s", details
					.getProgId()));
			System.out.println(String.format("\tDescription: %s", details
					.getDescription()));
		}
	}
}
