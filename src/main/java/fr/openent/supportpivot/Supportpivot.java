/*
 *
 * Copyright (c) Mairie de Paris, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.supportpivot;

import fr.openent.supportpivot.controllers.GlpiController;
import fr.openent.supportpivot.controllers.JiraController;
import fr.openent.supportpivot.controllers.LdeController;
import fr.openent.supportpivot.controllers.SupportPivotController;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.managers.ServiceManager;
import org.entcore.common.http.BaseServer;

public class Supportpivot extends BaseServer {

    @Override
	public void start() throws Exception {
		super.start();
		ConfigManager.init(config);
		/*
		if (!Config.getInstance().checkConfiguration()) {
			LocalMap<String, String> deploymentsIdMap = vertx.sharedData().getLocalMap("deploymentsId");
			vertx.undeploy(deploymentsIdMap.get("fr.openent.supportpivot"));
			return;
	   */

		ServiceManager.init(vertx, config);
		addController(new SupportPivotController());
		addController(new GlpiController());
		addController(new JiraController());
		addController(new LdeController());

	}

}
