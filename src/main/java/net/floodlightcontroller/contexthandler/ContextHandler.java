package net.floodlightcontroller.contexthandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.bandwidthcollector.BandwidthCollector;
import net.floodlightcontroller.bandwidthcollector.IBandwidthAlertListener;
import net.floodlightcontroller.bandwidthcollector.IBandwidthAlertService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;


public class ContextHandler implements IFloodlightModule, IBandwidthAlertListener {


	// Services used
	protected IBandwidthAlertService bandwidthAlertsProvider;

	private static final Logger log = LoggerFactory.getLogger(BandwidthCollector.class);

	// JSON object of Contexts.txt
	private JSONObject JSONContext;


	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IBandwidthAlertService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		bandwidthAlertsProvider = context.getServiceImpl(IBandwidthAlertService.class);

		// Load Context file into JSON object JSONContext
		String jsonTxt;
		try {
			jsonTxt = fileToString("Contexts.txt");
			JSONContext = new JSONObject(jsonTxt);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}		       
	}


@Override
public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
	bandwidthAlertsProvider.addBandwidthAlertListener(this);
}



// Implementation of ContextActivator	
@Override
public void receiveBandwidthNotification(String type, long value, NodePortTuple dpid_port) {
	log.info("Alert of type: " + type + " received from " + dpid_port);
	JSONArray array;
	try {
		array = JSONContext.getJSONArray("contexts");
		for(int i = 0 ; i < array.length() ; i++){
			JSONObject context = array.getJSONObject(i);
			JSONObject context_condition = context.getJSONObject("conditions");

			if(context_condition.has("stat:bandwidth")){
				if(Integer.valueOf(context_condition.getString("stat:bandwidth")) <= value){
					log.info("Context " + context.getString("name") + " has one condition fullfilled");
				}
			}
		}
	} catch (JSONException e) {
		e.printStackTrace();
	}
}



private String fileToString(String filename) throws IOException {

	String file_str;
	try(BufferedReader br = new BufferedReader(new FileReader(filename))) {
		StringBuilder sb = new StringBuilder();
		String line = br.readLine();

		while (line != null) {
			//log.info(line);
			sb.append(line);
			sb.append(System.lineSeparator());
			line = br.readLine();
		}
		file_str = sb.toString();
		return file_str;
	}

}

}
