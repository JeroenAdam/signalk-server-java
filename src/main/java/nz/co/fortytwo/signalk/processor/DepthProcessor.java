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
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package nz.co.fortytwo.signalk.processor;

import nz.co.fortytwo.signalk.handler.DepthHandler;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.logging.log4j.LogManager; import org.apache.logging.log4j.Logger;



/**
 * Calls DepthHandler to calculate depth.belowDurface and depth.belowKeek
 * 
 * @author RBerliner
 * 
 */
public class DepthProcessor extends SignalkProcessor implements Processor{

	private static Logger logger = LogManager.getLogger(DepthProcessor.class);
	private DepthHandler depthHandler = new DepthHandler();

	public void process(Exchange exchange) throws Exception {
		
		try {		
                        depthHandler.handle(signalkModel);			
		} catch (Exception e) {
			logger.error(e);
		}
	}
	

}
