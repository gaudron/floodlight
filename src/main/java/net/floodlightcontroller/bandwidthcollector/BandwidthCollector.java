package net.floodlightcontroller.bandwidthcollector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.StatisticsCollector;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;
import net.floodlightcontroller.threadpool.IThreadPoolService;

public class BandwidthCollector implements IFloodlightModule {

	private static final Logger log = LoggerFactory.getLogger(BandwidthCollector.class);

	protected IStatisticsService statsProvider;
	protected Map<NodePortTuple, SwitchPortBandwidth> statsResult;
	private static IThreadPoolService threadPoolService;





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
		    l.add(IThreadPoolService.class);
		    l.add(IStatisticsService.class);
		    return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		statsProvider = context.getServiceImpl(IStatisticsService.class);
		statsResult = new HashMap<NodePortTuple, SwitchPortBandwidth>();
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
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
				log.info("Port number:" + keys.getPortId().getPortNumber());
				log.info("Received: " +  statsResult.get(keys).getBitsPerSecondRx().getValue()+" bits/s");
				log.info("Transmitted: " + statsResult.get(keys).getBitsPerSecondTx().getValue()+" bits/s");
			}

		}
	}
}
