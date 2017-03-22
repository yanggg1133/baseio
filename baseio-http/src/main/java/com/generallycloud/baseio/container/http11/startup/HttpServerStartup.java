/*
 * Copyright 2015-2017 GenerallyCloud.com
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ 
package com.generallycloud.baseio.container.http11.startup;

import java.io.File;

import com.generallycloud.baseio.acceptor.SocketChannelAcceptor;
import com.generallycloud.baseio.codec.http11.ServerHTTPProtocolFactory;
import com.generallycloud.baseio.codec.http11.future.WebSocketBeatFutureFactory;
import com.generallycloud.baseio.common.CloseUtil;
import com.generallycloud.baseio.common.Encoding;
import com.generallycloud.baseio.common.Logger;
import com.generallycloud.baseio.common.LoggerFactory;
import com.generallycloud.baseio.common.SharedBundle;
import com.generallycloud.baseio.component.LoggerSocketSEListener;
import com.generallycloud.baseio.component.NioSocketChannelContext;
import com.generallycloud.baseio.component.SocketChannelContext;
import com.generallycloud.baseio.component.SocketSessionAliveSEListener;
import com.generallycloud.baseio.component.ssl.SSLUtil;
import com.generallycloud.baseio.component.ssl.SslContext;
import com.generallycloud.baseio.configuration.PropertiesSCLoader;
import com.generallycloud.baseio.configuration.ServerConfiguration;
import com.generallycloud.baseio.configuration.ServerConfigurationLoader;
import com.generallycloud.baseio.container.ApplicationContext;
import com.generallycloud.baseio.container.ApplicationIoEventHandle;
import com.generallycloud.baseio.container.configuration.ApplicationConfiguration;
import com.generallycloud.baseio.container.configuration.ApplicationConfigurationLoader;
import com.generallycloud.baseio.container.configuration.FileSystemACLoader;
import com.generallycloud.baseio.container.http11.service.FutureAcceptorHttpFilter;

public class HttpServerStartup {
	
	public void launch(String base) throws Exception {

		SharedBundle bundle = SharedBundle.instance().loadAllProperties(base);
		
		LoggerFactory.configure(bundle.loadProperties("conf/log4j.properties", Encoding.UTF8));
		
		ApplicationConfigurationLoader acLoader = new FileSystemACLoader(base);

		ApplicationConfiguration ac = acLoader.loadConfiguration(SharedBundle.instance());
		
		ApplicationContext applicationContext = new ApplicationContext(ac);
		
		ServerConfigurationLoader configurationLoader = new PropertiesSCLoader();
		
		ServerConfiguration configuration = configurationLoader.loadConfiguration(SharedBundle.instance());

//		configuration.setSERVER_ENABLE_MEMORY_POOL(false);
		
		SocketChannelContext context = new NioSocketChannelContext(configuration);
//		SocketChannelContext context = new AioSocketChannelContext(configuration);
		
		SocketChannelAcceptor acceptor = new SocketChannelAcceptor(context);

		try {
			
			applicationContext.setServiceFilter(new FutureAcceptorHttpFilter());
			
			context.setBeatFutureFactory(new WebSocketBeatFutureFactory());

			context.setIoEventHandleAdaptor(new ApplicationIoEventHandle(applicationContext));

			context.addSessionEventListener(new LoggerSocketSEListener());
			
			context.addSessionIdleEventListener(new SocketSessionAliveSEListener());
			
			context.setProtocolFactory(new ServerHTTPProtocolFactory());
			
			if (configuration.isSERVER_ENABLE_SSL()) {
				
				File certificate = SharedBundle.instance().loadFile("conf/generallycloud.com.crt");
				File privateKey = SharedBundle.instance().loadFile("conf/generallycloud.com.key");
				
//				File certificate = SharedBundle.instance().loadFile(base + "/conf/keyutil_127.0.0.1.crt");
//				File privateKey = SharedBundle.instance().loadFile(base + "/conf/keyutil_127.0.0.1.key");
				
				SslContext sslContext = SSLUtil.initServer(privateKey,certificate);
				
				context.setSslContext(sslContext);
				
				configuration.setSERVER_PORT(443);
			}else{
				
				configuration.setSERVER_PORT(80);
			}
			
			acceptor.bind();

		} catch (Throwable e) {
			
			Logger logger = LoggerFactory.getLogger(getClass());
			
			logger.error(e.getMessage(), e);

			CloseUtil.unbind(acceptor);
		}
	}
	
	public static void main(String[] args) throws Exception {
		
		HttpServerStartup startup = new HttpServerStartup();
		
		String base = null;
		
		if (args != null && args.length > 0) {
			base = args[0];
		}
		
		startup.launch(base);
	}
}
