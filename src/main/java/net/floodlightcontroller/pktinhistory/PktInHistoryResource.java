package net.floodlightcontroller.pktinhistory;

import java.util.ArrayList;
import java.util.List;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import net.floodlightcontroller.core.types.SwitchMessagePair;

public class PktInHistoryResource extends ServerResource {
	@Get("json")
    public List<SwitchMessagePair> retrieve() {
        IPktInHistoryService pihr = (IPktInHistoryService)getContext().getAttributes().get(IPktInHistoryService.class.getCanonicalName());
        List<SwitchMessagePair> l = new ArrayList<SwitchMessagePair>();
        l.addAll(java.util.Arrays.asList(pihr.getBuffer().snapshot()));
        return l;
    }

}
