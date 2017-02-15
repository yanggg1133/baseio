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
package com.generallycloud.nio.component;

import com.generallycloud.nio.common.CloseUtil;
import com.generallycloud.nio.common.Logger;
import com.generallycloud.nio.common.LoggerFactory;

public class SocketSessionAliveSEListener implements SocketSessionIdleEventListener{
	
	private Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void sessionIdled(SocketSession session, long lastIdleTime, long currentTime) {
		
		if (session.getLastAccessTime() < lastIdleTime) {
			logger.info("心跳周期内未检测到心跳消息，准备断开连接：{}",session);
			CloseUtil.close(session);
		}
	}
}
