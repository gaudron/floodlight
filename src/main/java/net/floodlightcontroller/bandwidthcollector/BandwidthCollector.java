package net.floodlightcontroller.bandwidthcollector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;
import net.floodlightcontroller.threadpool.IThreadPoolService;

public class BandwidthCollector implements IFloodlightModule, IBandwidthAlertService {
	
	/**
	 * Collects bandwidth stats every x sec and raises alarms if overload detected
	 */

	private static final Logger log = LoggerFactory.getLogger(BandwidthCollector.class);

	//Services used
	protected IStatisticsService statsProvider;
	private static IThreadPoolService threadPoolService;
	
	protected Map<NodePortTuple, SwitchPortBandwidth> statsResult;
	protected List<IBandwidthAlertListener> listeners;


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
	}

	private class ThreadBandwidthCollector implements Runnable {

		@Override
		public void run() {
			statsResult = statsProvider.getBandwidthConsumption();
			for (NodePortTuple keys : statsResult.keySet()) {
				long RxRate = statsResult.get(keys).getBitsPerSecondRx().getValue();
				long TxRate = statsResult.get(keys).getBitsPerSecondTx().getValue();
				log.info("Port number:" + keys.getPortId().getPortNumber());
				log.info("Received: " +  RxRate +" bits/s");
				log.info("Transmitted: " + TxRate +" bits/s");
				
				if(TxRate > 2000){
					notifyListeners("overload",  keys);
				}
			}

		}
	}
	
	
	
	// Implementation of IBandwidthAlertService
	@Override
	public void addBandwidthAlertListener(IBandwidthAlertListener listener) {
		listeners.add(listener);	
	}

	@Override
	public void notifyListeners(String type, NodePortTuple dpid_port) {
		for (IBandwidthAlertListener list : listeners) {   //ContextActivator is a listener
			list.receiveBandwidthNotification(type, dpid_port);
		}
	}
}
