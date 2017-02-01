/*
 * 
 * Copyright (C) 2012-2014 R T Huitema. All Rights Reserved.
 * Web: www.42.co.nz
 * Email: robert@42.co.nz
 * Author: R T Huitema
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nz.co.fortytwo.signalk.processor;


import static nz.co.fortytwo.signalk.util.SignalKConstants.FORMAT_DELTA;
import static nz.co.fortytwo.signalk.util.SignalKConstants.POLICY_FIXED;
import static nz.co.fortytwo.signalk.util.SignalKConstants.POLICY_IDEAL;
import static nz.co.fortytwo.signalk.util.SignalKConstants.POLICY_INSTANT;
import static nz.co.fortytwo.signalk.util.SignalKConstants.SIGNALK_FORMAT;
import static nz.co.fortytwo.signalk.util.SignalKConstants.dot;
import static nz.co.fortytwo.signalk.util.SignalKConstants.source;
import static nz.co.fortytwo.signalk.util.SignalKConstants.sourceRef;
import static nz.co.fortytwo.signalk.util.SignalKConstants.timestamp;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.websocket.WebsocketConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.Subscribe;

import nz.co.fortytwo.signalk.model.SignalKModel;
import nz.co.fortytwo.signalk.model.event.PathEvent;
import nz.co.fortytwo.signalk.model.impl.SignalKModelFactory;
import nz.co.fortytwo.signalk.server.Subscription;
import nz.co.fortytwo.signalk.util.ConfigConstants;

/**
 * Exports the signalkModel as a json object.
 * Avoids meta, sources, and values branches
 *
 * @author robert
 */
public class FullExportProcessor extends SignalkProcessor implements Processor, FullExportProcessorMBean {

	private static final Logger logger = LogManager.getLogger(FullExportProcessor.class);
    private static final Timer timer = new Timer("Export Timer", true);

    private final String wsSession;
    private final String routeId;
    private final AtomicLong lastSend;
    //private ThreadLocal<ConcurrentLinkedQueue<String>> pendingThreadLocal = new ThreadLocal<>();
    private final ConcurrentLinkedQueue<String> pendingPaths = new ConcurrentLinkedQueue<>();

    public FullExportProcessor(String wsSession, String routeId) {
        super();
        this.wsSession = wsSession;
        this.routeId=routeId;
        lastSend = new AtomicLong(System.currentTimeMillis());
        signalkModel.getEventBus().register(this);
        
       
        //setup jmx
        //register the MBean
		try {
			ObjectName name = new ObjectName(getClass().getName(),"id",UUID.randomUUID().toString());
			ManagementFactory.getPlatformMBeanServer().registerMBean(this, name);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.error(e.getMessage(),e);
		} 
        
    }
    
	
	 public void process(Exchange exchange) throws Exception {
	        try {
	            if (logger.isDebugEnabled())
	                logger.info("process  subs for " + routeId + " as delta? " + isDelta(routeId));

	            // Clear the pending paths as we are about to send all.
	            
	            pendingPaths.clear();
	            lastSend.set(System.currentTimeMillis());

	            // get the accumulated delta nodes.
	            exchange.getIn().setBody(createTree(routeId));
	            setHeaders(exchange);
	            if (logger.isDebugEnabled()) {
	                logger.debug("Headers set to :" + exchange.getIn().getHeaders());
	               // logger.debug("Body set to :" + StringUtils.abbreviate(exchange.getIn().getBody().toString(),500));
	                logger.debug("Body set to :" + exchange.getIn().getBody().toString());
	            }
	        } catch (Exception e) {
	            logger.error(e.getMessage(), e);
	            throw e;
	        }
	    }
	/*public void process(Exchange exchange) throws Exception {

		try {
			if(logger.isInfoEnabled())logger.info("process  subs for " + exchange.getFromRouteId()+" as delta? "+isDelta(exchange.getFromRouteId()));
			// get the accumulated delta nodes.
			exchange.getIn().setBody(createTree(exchange.getFromRouteId()));
			setHeaders(exchange);
			if(logger.isDebugEnabled()){
				logger.debug("Headers set to :" + exchange.getIn().getHeaders());
				logger.debug("Body set to :" + exchange.getIn().getBody());
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}*/

	 private void setHeaders(Exchange exchange) {
	        for (Subscription sub : manager.getSubscriptions(wsSession)) {
	            if (sub == null || !sub.isActive() || !routeId.equals(sub.getRouteId()))
	                continue;
	            exchange.getIn().setHeader(SIGNALK_FORMAT, sub.getFormat());
	            if (sub.getDestination() != null) {
	                exchange.getIn().setHeader(ConfigConstants.DESTINATION, sub.getDestination());
	            }
	            exchange.getIn().setHeader(ConfigConstants.OUTPUT_TYPE, manager.getOutputType(sub.getWsSession()));
	            exchange.getIn().setHeader(WebsocketConstants.CONNECTION_KEY, sub.getWsSession());

	        }
	    }

	   
	 private boolean isDelta(String routeId) {
	        for (Subscription sub : manager.getSubscriptions(wsSession)) {
	            if (sub != null && sub.isActive() && routeId.equals(sub.getRouteId()) 
	            		&& FORMAT_DELTA.equals(sub.getFormat())) {
	                return true;
	            }
	        }
	        return false;
	    }

	   private SignalKModel createTree(String routeId) {
	        SignalKModel temp = SignalKModelFactory.getCleanInstance();
	        if(logger.isDebugEnabled())logger.debug("subs for ws:" + wsSession + " = " + manager.getSubscriptions(wsSession));
	        for (Subscription sub : manager.getSubscriptions(wsSession)) {
	            if (sub != null && sub.isActive() && routeId.equals(sub.getRouteId())) {
	            	if(logger.isDebugEnabled())logger.debug("Found active sub:" + sub);
	                for (String p : sub.getSubscribed(null)) {
	                    NavigableMap<String, Object> node = signalkModel.getSubMap(p);
	                    if(logger.isDebugEnabled())logger.debug("Found node:" + p + " = " + node);
	                    for (String key:node.keySet()){
	                    	if(key.contains(".meta."))continue;
	                    	if(key.contains(".values."))continue;
	                    	//if(key.contains(".source"))continue;
	                    	//if(key.contains(".$source"))continue;
	                    	Object val = node.get(key);
	                    	if(val!=null){
	                    		temp.getData().put(key,val);
	                    	}
	                    }
	                    
	                }
	            }
	        }
	        return temp;
	    }
/*
	private SignalKModel createTree(String routeId) {
		// we need the routeId.
		// Multimap<String,String> vesselList = TreeMultimap.create();
		SignalKModel temp = SignalKModelFactory.getCleanInstance();
		
		for (Subscription sub : manager.getSubscriptions(wsSession)) {
			if (sub == null || !sub.isActive() || !routeId.equals(sub.getRouteId()))
				continue;
			for (String p : sub.getSubscribed(null)) {
				populateTree(temp, p);
			}
		}
		return temp;

	}*/

	
	

	  /**
     * @param pathEvent the path that was changed
     */
    @Subscribe
    public void recordEvent(PathEvent pathEvent) {
        if (pathEvent == null)
            return;

        String path = pathEvent.getPath();
        if (path == null)
            return;
        
        if(path.endsWith(dot+source)
    			|| path.endsWith(dot+timestamp)
    			|| path.contains(dot+source+dot)
    			|| path.endsWith(dot+sourceRef)){
        	return;
        }
        // Send update if necessary.
        for (Subscription s : manager.getSubscriptions(wsSession)) {
            if (s.isActive() && s.isSubscribed(path) && routeId.equals(s.getRouteId())) {
                switch (s.getPolicy()) {
                    case POLICY_INSTANT:
                        // Always send now.
                        send(Collections.singletonList(path),s);
                        return;
                    case POLICY_IDEAL:
                        // Schedule a timer if the queue is currently empty.
                    	
                        boolean schedule = pendingPaths.isEmpty();
                        pendingPaths.add(path);

                        if (schedule) {
                            TimerTask task = new TimerTask() {
                                @Override
                                public void run() {
                                    List<String> paths = new ArrayList<>();
                                    for (String path = pendingPaths.poll(); path != null; path = pendingPaths.poll()) {
                                        paths.add(path);
                                    }
                                    send(paths,s);
                                }
                            };
                            // Schedule to send minPeriod after the last send; if that's in the past the task will run immediately.
                            timer.schedule(task, new Date(lastSend.get() + s.getMinPeriod()));
                        }
                        return;
                    case POLICY_FIXED:
                    default:
                        // Updates will be sent at the next period.
                        break;
                }
            }
        }
    }

    /**
	 * @param pathEvent
	 */
	/*@Subscribe
	public void recordEvent(PathEvent pathEvent) {
		if (pathEvent == null)
			return;
		if (pathEvent.getPath() == null)
			return;
		//if (logger.isTraceEnabled())logger.trace(this.wsSession + " received event " + pathEvent.getPath());
		if(logger.isDebugEnabled())logger.debug(this.wsSession + " received event " + pathEvent.getPath());
		// do we care?
		for (Subscription s : manager.getSubscriptions(wsSession)) {
			if (s.isActive() && !POLICY_FIXED.equals(s.getPolicy()) && s.isSubscribed(pathEvent.getPath())) {
				if(logger.isDebugEnabled())logger.debug("Adding to send queue : "+pathEvent.getPath());
				queue.add(pathEvent.getPath());
				sender.startSender();
				break;
			}
		}

	}*/
	@Subscribe
	public void recordEvent(DeadEvent e) {
		logger.debug("Received dead event" + e.getSource());
	}


    private void send(Collection<String> paths, Subscription sub) {
        if (paths.isEmpty()) {
            return;
        }

        SignalKModel temp = SignalKModelFactory.getCleanInstance();
        boolean send = false;
        for (String path : paths) {
            Object node = signalkModel.get(path);
            if (node != null) {
                temp.getFullData().put(path, node);
                send = true;
            }
        }

        if (send) {
            if (logger.isDebugEnabled()) {
                logger.debug("Sending : " + temp.getKeys());
            }

            Map<String, Object> headers = new HashMap<>();
            headers.put(WebsocketConstants.CONNECTION_KEY, wsSession);
            headers.put(ConfigConstants.OUTPUT_TYPE, manager.getOutputType(wsSession));
            if (sub.getDestination() != null) {
            	headers.put(ConfigConstants.DESTINATION, sub.getDestination());
            }
            asyncSendBodyAndHeaders(outProducer,temp, headers);
            lastSend.set(System.currentTimeMillis());
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Nothing to send");
            }
        }

    }


	@Override
	public String getSubscriptionDetails() {
		ArrayList<String> subDetails = new ArrayList<>();
		for (Subscription s : manager.getSubscriptions(wsSession)) {
			if(routeId.equals(s.getRouteId())){
				subDetails.add(s.toString());
			}
		}
		return subDetails.toString();
	}
	
/*	class MsgSender implements Runnable {
		long lastSend = 0;

		Thread t = null;

		public void startSender() {
			if(logger.isDebugEnabled())logger.debug("Checking sender..");
			if (t != null && t.isAlive())
				return;
			if(logger.isDebugEnabled())logger.debug("Starting sender..");
			t = new Thread(this);
			t.start();
		}

		@Override
		public void run() {
			// TODO make this react to actual subs minPeriod
			while (true) {
				if (System.currentTimeMillis() - lastSend > 100) {
					// send messages
					String p = null;
					SignalKModel temp = SignalKModelFactory.getCleanInstance();
					boolean send = false;
					while ((p = queue.poll()) != null) {
						Object node = signalkModel.get(p);
						if(logger.isDebugEnabled())logger.debug("Found node:" + p + " = " + node);
						if (node != null) {
							temp.getFullData().put(p, node);
							send = true;
						}
					}
					if (send) {
						HashMap<String, Object> headers = new HashMap<String, Object>();
						headers.put(WebsocketConstants.CONNECTION_KEY, wsSession);
						headers.put(ConfigConstants.OUTPUT_TYPE, manager.getOutputType(wsSession));
						asyncSendBodyAndHeaders(outProducer,temp, headers);
						lastSend = System.currentTimeMillis();
					}
					break;
				} else {
					try {
						Thread.sleep(20);
					} catch (InterruptedException e) {
					}
				}
			}

		}

		

	}*/

}
