package net.floodlightcontroller.contexthandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.bandwidthhandler.BandwidthHandler;
import net.floodlightcontroller.bandwidthhandler.IBandwidthAlertListener;
import net.floodlightcontroller.bandwidthhandler.IBandwidthAlertService;
import net.floodlightcontroller.context.Context;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.tools.FileTool;


public class ContextHandler implements IFloodlightModule, IBandwidthAlertListener {


	// Services used
	private IBandwidthAlertService bandwidthAlertsProvider; //to register as a listener
	private static IThreadPoolService threadPoolService; //to keep up to date with context file modif
	private IDeviceService deviceService; //to identify a machine


	private static final Logger log = LoggerFactory.getLogger(BandwidthHandler.class);

	// JSON object of Contexts.txt
	private JSONObject JSONContext;

	private ArrayList<Context> contextsToWatch; //not active


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
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		deviceService = context.getServiceImpl(IDeviceService.class);

		contextsToWatch = new ArrayList<Context>();

		// Load Context file into JSON object JSONContext
		contextFileToJSON();		       
	}


	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		bandwidthAlertsProvider.addBandwidthAlertListener(this);
		threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new ThreadContextFile(), 60, 60, TimeUnit.SECONDS);	
		
	}


	//Thread that reads context file and update if needed JSONContext (if file was modified by user)
	private class ThreadContextFile implements Runnable {

		@Override
		public void run(){
			contextFileToJSON();
		}
	}
	
	public void contextFileToJSON() {
		String jsonTxt;
		try {
			jsonTxt = FileTool.fileToString("Contexts.txt");
			JSONContext = new JSONObject(jsonTxt);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}	
	}


	// Implementation of IBandwidthAlertListener	
	@Override
	public void receiveBandwidthNotification(String type, float value, NodePortTuple dpid_port) {
		log.info("Alert of type: " + type + " received from " + dpid_port);

		//identify a potential victim
		IDevice potentialVictim = identifyVictim(dpid_port);
		if(potentialVictim != null){
			log.info("Victim identified: " + potentialVictim.getIPv4Addresses()[0]);
			
			//HashMap<context_name, ArrayList<context_value_for_this_condition_type> 
			//One condition of each type possible per conditions set
			HashMap<String, ArrayList<String>> matchingContexts = statMatchingContext(type, value);
			for(String concerned_context: matchingContexts.keySet()){
				log.info("Concerned context: " + concerned_context);
				//if(!isContext(concerned_context, ))
			}
			
		
		} else {
			//log.info("No victim identified for this node/port tuple");
		}
		
		


	}

	public HashMap<String, ArrayList<String>> statMatchingContext(String type, float value){
		//HashMap<String, HashMap<String, ArrayList<String>>> concernedContexts = new HashMap<String, HashMap<String, ArrayList<String>>>();
		//HashMap<String, ArrayList<String>> condition_fields = new HashMap<String, ArrayList<String>>();

		HashMap<String, ArrayList<String>> concernedContexts = new HashMap<String, ArrayList<String>>();

		JSONArray array;

		try {
			array = JSONContext.getJSONArray("contexts");
			for(int i = 0 ; i < array.length() ; i++){
				JSONObject context = array.getJSONObject(i);
				ArrayList<String> condition_values = new ArrayList<String>();
				int setNbr = 1;

				while(context.has("conditions_" + setNbr)) {
					JSONObject context_condition = context.getJSONObject("conditions_" + setNbr);


					if(context_condition.has(type)){
						if(Float.valueOf(context_condition.getString(type)) <= value){
							//log.info("Context " + context.getString("name") + " has one condition fulfilled in set " + setNbr);
							condition_values.add(context_condition.getString(type));
						} 
					}
					setNbr++;
				}
				if(condition_values.size() > 0){
					concernedContexts.put(context.getString("name"), condition_values);
				}
				//log.info("Intermediate ConcernedContexts: " + concernedContexts);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		log.info("ConcernedContexts: " + concernedContexts);
		return concernedContexts;
	}


	public IDevice identifyVictim(NodePortTuple dpid_port){
		Iterator<? extends IDevice> potentialVictim = deviceService.queryDevices(MacAddress.NONE, VlanVid.ZERO, IPv4Address.NONE, IPv6Address.NONE, dpid_port.getNodeId(), dpid_port.getPortId());

		if (!potentialVictim.hasNext()) { 
			return null;
		} else {
			return potentialVictim.next();
		}
	}

}
