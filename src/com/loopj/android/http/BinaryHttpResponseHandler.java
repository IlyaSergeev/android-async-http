/*
    Android Asynchronous Http Client
    Copyright (c) 2011 James Smith <james@loopj.com>
    http://loopj.com

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */

package com.loopj.android.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;

import android.os.Message;

/**
 * Used to intercept and handle the responses from requests made using
 * {@link AsyncHttpClient}. Receives response body as byte array with a
 * content-type whitelist. (e.g. checks Content-Type against allowed list,
 * Content-length).
 * <p>
 * For example:
 * <p>
 * 
 * <pre>
 * AsyncHttpClient client = new AsyncHttpClient();
 * String[] allowedTypes = new String[]
 * { &quot;image/png&quot; };
 * client.get(&quot;http://www.example.com/image.png&quot;, new BinaryHttpResponseHandler(allowedTypes)
 * {
 * 	&#064;Override
 * 	public void onSuccess(byte[] imageData)
 * 	{
 * 		// Successfully got a response
 * 	}
 * 
 * 	&#064;Override
 * 	public void onFailure(Throwable e, byte[] imageData)
 * 	{
 * 		// Response failed :(
 * 	}
 * });
 * </pre>
 */
public class BinaryHttpResponseHandler extends AsyncHttpResponseHandler
{
	private static final int PROGRESS_CHANGE_MESSAGE = 6;
	private static final int DEFAULT_BUF_SIZE = 1024*1024;
	
	// Allow all contentType
	private static String[] mAllowedContentTypes = null;
	private boolean isCanceled = false;

	/**
	 * Creates a new BinaryHttpResponseHandler
	 */
	public BinaryHttpResponseHandler()
	{
		super();
	}

	/**
	 * Creates a new BinaryHttpResponseHandler, and overrides the default
	 * allowed content types with passed String array (hopefully) of content
	 * types.
	 */
	public BinaryHttpResponseHandler(String[] allowedContentTypes)
	{
		this();
		mAllowedContentTypes = allowedContentTypes;
	}
	
	public void onDidDownload(int statusCode, byte[] downloaded, long lenght) throws IOException
	{
	}
	
	public void onProgressDidChange(int statusCode, long lenght, long totalLenght)
	{
	}

	//
	// Callbacks to be overridden, typically anonymously
	//

	/**
	 * Fired when a request returns successfully, override to handle in your own
	 * code
	 * 
	 * @param binaryData
	 *            the body of the HTTP response from the server
	 */
	public void onSuccess()
	{
	}

	/**
	 * Fired when a request returns successfully, override to handle in your own
	 * code
	 * 
	 * @param statusCode
	 *            the status code of the response
	 * @param binaryData
	 *            the body of the HTTP response from the server
	 */
	public void onSuccess(int statusCode)
	{
		onSuccess();
	}

	/**
	 * Fired when a request fails to complete, override to handle in your own
	 * code
	 * 
	 * @param error
	 *            the underlying cause of the failure
	 * @param binaryData
	 *            the response body, if any
	 * @deprecated
	 */
	@Deprecated
	public void onFailure(Throwable error, byte[] binaryData)
	{
		// By default, call the deprecated onFailure(Throwable) for
		// compatibility
		onFailure(error);
	}
	
	protected void sendProgressChangeMessage(int statusCode, long length, long totalLength)
	{
		sendMessage(obtainMessage(PROGRESS_CHANGE_MESSAGE, new Object[]
		{ statusCode, length, totalLength }));
	}

	//
	// Pre-processing of messages (executes in background threadpool thread)
	//

	protected void sendSuccessMessage(int statusCode, byte[] responseBody)
	{
		sendMessage(obtainMessage(SUCCESS_MESSAGE, new Object[]
		{ statusCode, responseBody }));
	}

	@Override
	protected void sendFailureMessage(Throwable e, byte[] responseBody)
	{
		sendMessage(obtainMessage(FAILURE_MESSAGE, new Object[]
		{ e, responseBody }));
	}

	//
	// Pre-processing of messages (in original calling thread, typically the UI
	// thread)
	//

	protected void handleSuccessMessage(int statusCode)
	{
		onSuccess(statusCode);
	}

	protected void handleFailureMessage(Throwable e, byte[] responseBody)
	{
		onFailure(e, responseBody);
	}

	// Methods which emulate android's Handler and Message methods
	@Override
	protected void handleMessage(Message msg)
	{
		Object[] response;
		switch (msg.what)
		{
		
		case PROGRESS_CHANGE_MESSAGE:
			response = (Object[]) msg.obj;
			onProgressDidChange(((Number)(response[0])).intValue(), ((Number)(response[1])).longValue(), ((Number)(response[2])).longValue());
			break;
		case SUCCESS_MESSAGE:
			response = (Object[]) msg.obj;
			handleSuccessMessage(((Integer) response[0]).intValue());
			break;
		case FAILURE_MESSAGE:
			response = (Object[]) msg.obj;
			String message = "no message";
			if (response[1] != null)
			{
				message = response[1].toString();
			}
			handleFailureMessage((Throwable) response[0], message);
			break;
		default:
			super.handleMessage(msg);
			break;
		}
	}

	// Interface to AsyncHttpRequest
	@Override
	void sendResponseMessage(HttpResponse response)
	{
		StatusLine status = response.getStatusLine();
		Header[] contentTypeHeaders = response.getHeaders("Content-Type");
		byte[] responseBody = new byte[0];
		if (contentTypeHeaders.length != 1)
		{
			// malformed/ambiguous HTTP Header, ABORT!
			sendFailureMessage(new HttpResponseException(status.getStatusCode(), "None, or more than one, Content-Type Header found!"), responseBody);
			return;
		}
		Header contentTypeHeader = contentTypeHeaders[0];
		boolean foundAllowedContentType = true;
		if (mAllowedContentTypes != null)
		{
			foundAllowedContentType = false;
			for (String anAllowedContentType : mAllowedContentTypes)
			{
				if (Pattern.matches(anAllowedContentType, contentTypeHeader.getValue()))
				{
					foundAllowedContentType = true;
				}
			}
		}
		if (!foundAllowedContentType)
		{
			// Content-Type not in allowed list, ABORT!
			sendFailureMessage(new HttpResponseException(status.getStatusCode(), "Content-Type not allowed!"), responseBody);
			return;
		}
		try
		{
			int statusCode = status.getStatusCode();
			HttpEntity temp = response.getEntity();
			if (temp != null)
			{
				InputStream stream = temp.getContent();
				byte[] beffer = new byte[DEFAULT_BUF_SIZE];
				long totalLength = temp.getContentLength();
				int readedBytes = 0;
				int allReadBytes = 0; 
				while(!isCanceled() && (readedBytes = stream.read(beffer)) != -1)
				{
					onDidDownload(statusCode, beffer, readedBytes);
					allReadBytes += readedBytes;
					sendProgressChangeMessage(statusCode, allReadBytes, totalLength);
				}
				if (isCanceled())
				{
					sendFailureMessage(new Exception("User stop downloading"), responseBody);
				}
			}
		}
		catch (IOException e)
		{
			sendFailureMessage(e, responseBody);
		}
	}

	public boolean isCanceled()
	{
		return isCanceled;
	}

	public void setCanceled(boolean isCanceled)
	{
		this.isCanceled = isCanceled;
	}
}
