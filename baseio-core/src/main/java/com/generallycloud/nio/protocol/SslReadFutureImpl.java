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
package com.generallycloud.nio.protocol;

import java.io.IOException;

import com.generallycloud.nio.buffer.ByteBuf;
import com.generallycloud.nio.common.ReleaseUtil;
import com.generallycloud.nio.component.Session;
import com.generallycloud.nio.component.SocketSession;
import com.generallycloud.nio.component.ssl.SslHandler;

public class SslReadFutureImpl extends AbstractChannelReadFuture implements SslReadFuture {

	private boolean	body_complete;

	private ByteBuf	buf;

	private boolean	header_complete;

	private int		length;

	private int		limit;

	public SslReadFutureImpl(SocketSession session, ByteBuf buf, int limit) {
		super(session.getContext());
		this.buf = buf;
		this.limit = limit;
	}

	private void doBodyComplete(SocketSession session, ByteBuf buf) throws IOException {

		body_complete = true;

		buf.flip();

		SslHandler handler = context.getSslContext().getSslHandler();

		ByteBuf old = this.buf;

		try {

			this.buf = handler.unwrap(session, buf);

		} finally {
			ReleaseUtil.release(old);
		}
	}

	private void doHeaderComplete(Session session, ByteBuf buf) throws IOException {

		header_complete = true;

		int length = getEncryptedPacketLength(buf);

		if (length < 1) {
			throw new ProtocolException("illegal length:" + length);
		}
		
		buf.reallocate(length, limit, true);

		this.length = length;
	}

	int getEncryptedPacketLength(ByteBuf buffer) {
		int packetLength = 0;
		int offset = 0;
		// FIXME offset

		// SSLv3 or TLS - Check ContentType
		boolean tls;
		switch (buffer.getUnsignedByte(offset)) {
		case SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC:
		case SSL_CONTENT_TYPE_ALERT:
		case SSL_CONTENT_TYPE_HANDSHAKE:
		case SSL_CONTENT_TYPE_APPLICATION_DATA:
			tls = true;
			break;
		default:
			// SSLv2 or bad data
			tls = false;
		}

		if (tls) {
			// SSLv3 or TLS - Check ProtocolVersion
			int majorVersion = buffer.getUnsignedByte(offset + 1);
			if (majorVersion == 3) {
				// SSLv3 or TLS
				packetLength = buffer.getUnsignedShort(offset + 3) + SSL_RECORD_HEADER_LENGTH;
				if (packetLength <= SSL_RECORD_HEADER_LENGTH) {
					// Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
					tls = false;
				}
			} else {
				// Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
				tls = false;
			}
		}

		if (!tls) {
			// SSLv2 or bad data - Check the version
			int headerLength = (buffer.getUnsignedByte(offset) & 0x80) != 0 ? 2 : 3;
			int majorVersion = buffer.getUnsignedByte(offset + headerLength + 1);
			if (majorVersion == 2 || majorVersion == 3) {
				// SSLv2
				if (headerLength == 2) {
					packetLength = (buffer.getShort(offset) & 0x7FFF) + 2;
				} else {
					packetLength = (buffer.getShort(offset) & 0x3FFF) + 3;
				}
				if (packetLength <= headerLength) {
					return -1;
				}
			} else {
				return -1;
			}
		}
		return packetLength;
	}

	public int getLength() {
		return length;
	}

	@Override
	public ByteBuf getProduce() {
		return buf;
	}

	private boolean isHeaderReadComplete(ByteBuf buf) {
		return !buf.hasRemaining();
	}

	@Override
	public boolean read(SocketSession session, ByteBuf buffer) throws IOException {

		if (!header_complete) {

			ByteBuf buf = this.buf;

			buf.read(buffer);

			if (!isHeaderReadComplete(buf)) {
				return false;
			}

			doHeaderComplete(session, buf);
		}

		if (!body_complete) {

			ByteBuf buf = this.buf;

			buf.read(buffer);

			if (buf.hasRemaining()) {
				return false;
			}

			doBodyComplete(session, buf);
		}

		return true;
	}

	@Override
	public void release() {
		ReleaseUtil.release(buf);
	}

}
