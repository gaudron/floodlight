package net.floodlightcontroller.bandwidthhandler;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.tools.*;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;
import net.floodlightcontroller.threadpool.IThreadPoolService;

public class BandwidthHandler implements IFloodlightModule, IBandwidthAlertService {

	/**
	 * Collects bandwidth stats every x sec and raises alarms if overload detected
	 */

	private static final Logger log = LoggerFactory.getLogger(BandwidthHandler.class);

	//Services used
	protected IStatisticsService statsProvider;
	private static IThreadPoolService threadPoolService;

	protected Map<NodePortTuple, SwitchPortBandwidth> statsResult;
	protected List<IBandwidthAlertListener> listeners; 

	private float monitoring_value = 1; //above this value bandwidth alert sent to listeners


	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IBandwidthAlertService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m =
				new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IBandwidthAlertService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IThreadPoolService.class);
		l.add(IStatisticsService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		statsProvider = context.getServiceImpl(IStatisticsService.class);
		statsResult = new HashMap<NodePortTuple, SwitchPortBandwidth>();
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		listeners = new ArrayList<IBandwidthAlertListener>();
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		statsProvider.collectStatistics(true); 
		threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new ThreadBandwidthCollector(), 5, 5, TimeUnit.SECONDS);
		threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new ThreadMonitoringValue(), 30, 30, TimeUnit.SECONDS);
		
		updateMonitoringValue();
	}

	private class ThreadBandwidthCollector implements Runnable {

		//DecimalFormat df = new DecimalFormat("0.##");
		long max_bandwidth = 10000; //invented value, the true one given by 
									//statsResult.get(keys).getLinkSpeedBitsPerSec().getValue() is 10000000

		@Override
		public void run() {
			statsResult = statsProvider.getBandwidthConsumption();
			for (NodePortTuple keys : statsResult.keySet()) {
				long RxRate = statsResult.get(keys).getBitsPerSecondRx().getValue();
				long TxRate = statsResult.get(keys).getBitsPerSecondTx().getValue();
				//float bandwidth_percent_Rx = (float) RxRate/max_bandwidth;
				float bandwidth_percent_Tx = (float) TxRate/max_bandwidth;
				log.info("Port number:" + keys.getPortId().getPortNumber());
				log.info("Received: " +  RxRate +" bits/s");
				log.info("Transmitted: " + TxRate +" bits/s");
				//log.info("Bandwidth percentage in reception: " + bandwidth_percent_Rx);
				log.info("Bandwidth percentage in transmission: " + bandwidth_percent_Tx);

				if(bandwidth_percent_Tx > monitoring_value){
					NodePortTuple sw = new NodePortTuple(statsResult.get(keys).getSwitchId(),statsResult.get(keys).getSwitchPort());
					log.info("Send bandwidth alert from: " + sw);
					notifyListeners("stat:bandwidth", bandwidth_percent_Tx,  sw);
				}
			}

		}
	}


	//Thread that reads context file to set monitoring_value
	private class ThreadMonitoringValue implements Runnable {

		@Override
		public void run(){
			updateMonitoringValue();
		}		
	}
	
	
	// To keep monitoring_value up to date (value above all messages are sent to listeners)
	public void updateMonitoringValue(){
		
		// Load Context file into JSON object JSONContext
		String jsonTxt;
		JSONObject JSONContext = null;
		try {
			jsonTxt = FileTool.fileToString("Contexts.txt");
			JSONContext = new JSONObject(jsonTxt);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}	


		JSONArray array;
		try {
			array = JSONContext.getJSONArray("contexts");
			for(int i = 0 ; i < array.length() ; i++){
				JSONObject context = array.getJSONObject(i);
				int setNbr = 1; //represent value of current conditions sets

				while(context.has("conditions_" + Integer.valueOf(setNbr))) {
					JSONObject context_condition = context.getJSONObject("conditions_" + Integer.valueOf(setNbr));

					if(context_condition.has("stat:bandwidth")){
						if(Float.parseFloat((context_condition.getString("stat:bandwidth"))) <= monitoring_value){
							monitoring_value = Float.parseFloat(context_condition.getString("stat:bandwidth"));
							log.info("New monitoring value is: " + monitoring_value);
						}
						
					}
					setNbr++;
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	



	// Implementation of IBandwidthAlertService
	@Override
	public void addBandwidthAlertListener(IBandwidthAlertListener listener) {
		listeners.add(listener);	
	}

	@Override
	public void notifyListeners(String type, float bandwidth_percent, NodePortTuple dpid_port) {
		for (IBandwidthAlertListener list : listeners) {   //ContextHandler is a listener
			list.receiveBandwidthNotification(type, bandwidth_percent, dpid_port);
		}
	}
}
