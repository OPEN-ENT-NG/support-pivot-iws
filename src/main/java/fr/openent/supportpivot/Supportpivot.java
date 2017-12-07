package fr.openent.supportpivot;

import org.entcore.common.http.BaseServer;

public class Supportpivot extends BaseServer {

	@Override
	public void start() {
		super.start();
		addController(new SupportController());
	}

}
