/***********************************************************************************
 * 
 * Copyright (c) 2014 Kamil Baczkowicz
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 *    
 * The Eclipse Distribution License is available at
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * 
 *    Kamil Baczkowicz - initial API and implementation and/or initial documentation
 *    
 */
package pl.baczkowicz.mqttspy.logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.baczkowicz.mqttspy.common.generated.LoggedMqttMessage;
import pl.baczkowicz.mqttspy.messages.BaseMqttMessage;
import pl.baczkowicz.spy.exceptions.SpyException;
import pl.baczkowicz.spy.exceptions.XMLException;
import pl.baczkowicz.spy.files.FileUtils;
import pl.baczkowicz.spy.utils.ConversionUtils;
import pl.baczkowicz.spy.utils.TimeUtils;
import pl.baczkowicz.spy.utils.tasks.ProgressUpdater;

/**
 * Message log utilities.
 */
public class MqttMessageLogParserUtils
{
	/** Diagnostic logger. */
	private final static Logger logger = LoggerFactory.getLogger(MqttMessageLogParserUtils.class);	
	
	/**
	 * This all-in-one method reads a message log from the given file and turns that into a list of MQTT message objects.
	 * 
	 * @param selectedFile The file to read from
	 * 
	 * @return List of MQTT messages (ReceivedMqttMessage objects)
	 * 
	 * @throws SpyException Thrown when cannot process the given file
	 */
	public static List<BaseMqttMessage> readAndConvertMessageLog(final File selectedFile) throws SpyException
	{
		final List<String> lines = FileUtils.readFileAsLines(selectedFile);
		logger.info("Message log - read {} messages from {}", lines.size(), selectedFile.getAbsoluteFile());		
		
		return processMessageLog(parseMessageLog(lines, null, 0, 0), null, 0, 0);
	}
	
//	/**
//	 * Reads a message log from the given file and turns that into a list of lines.
//	 * 
//	 * @param selectedFile The file to read from
//	 * 
//	 * @return List of lines
//	 * 
//	 * @throws SpyException Thrown when cannot process the given file
//	 */
//	public static List<String> readMessageLog(final File selectedFile) throws SpyException
//	{
//		try
//		{
//			BufferedReader in = new BufferedReader(new FileReader(selectedFile));
//	        String str;
//			        
//	        final List<String> list = new ArrayList<String>();
//	        while((str = in.readLine()) != null)
//	        {
//	        	list.add(str);	        	
//	        }
//	        
//	        in.close();
//	        logger.info("Message log - read {} messages from {}", list.size(), selectedFile.getAbsoluteFile());		        		       
//	        
//	        return list;
//		}
//		catch (IOException e)
//		{
//			throw new SpyException("Can't open the message log file at " + selectedFile.getAbsolutePath(), e);
//		}
//	}
	
	/**
	 * Parses the given list of XML messages into a list of MQTT message objects.
	 * 
	 * @param messages The list of XML messages 
	 * @param progress The progress updater to call with updated progress
	 * @param current Current progress
	 * @param max Maximum progress value 
	 * 
	 * @return List of MQTT message objects (LoggedMqttMessage)
	 * 
	 * @throws SpyException Thrown when cannot process the given list
	 */
	public static List<LoggedMqttMessage> parseMessageLog(final List<String> messages, 
			final ProgressUpdater progress, final long current, final long max) throws SpyException
	{
		try
		{
			final long startTime = TimeUtils.getMonotonicTime();
			final int items = messages.size();
	        final long chunkSize = items / 10;
	        
			final MqttMessageLogParser parser = new MqttMessageLogParser();			       
	        final List<LoggedMqttMessage> list = new ArrayList<LoggedMqttMessage>();
	        
	        long item = 0;
	        long reportAt = 1;
	               	        	
	        
	        for (int i = 0; i < items; i++)
	        //for (final String message : messages)
	        {	        		      
	        	final String message  = messages.get(i);
	        	
	        	if (progress != null)
	        	{
	        		if (progress.isCancelled())
					{
						logger.info("Task cancelled!");
						return null;
					}
	        		
		        	item++;
		        	// Update every 1000
		        	if (item % 1000 == 0)
		        	{
		        		progress.update(current + item, max);
		        	}
	        	}
	        	
	        	// If we have 10%, 20%, 30%...
	        	if ((i > 0 ) && (i == (chunkSize * reportAt)))
	        	{	        		
	        		final long currentTime = TimeUtils.getMonotonicTime();
	        		
	        		final long timeTaken = currentTime - startTime;
	        		final long totalTimeExpected =  timeTaken * items / i;
	        		
	        		// If the 10% took more than 1 second
	        		if (timeTaken > 1000)
	        		{
	        			logger.info("Processed {}%, estimated time left = {}s", reportAt * 10, (totalTimeExpected - timeTaken) / 1000);
	        		}
	        		reportAt++;
	        	}
	        	
	        	try
	        	{
	        		list.add(parser.parse(message));
	        	}
	        	catch (XMLException e)
	        	{
	        		logger.error("Can't process message " + message, e);
	        	}
	        }
	        
	        logger.info("Message log - parsed {} XML messages", list.size());		        		       
	        
	        return list;
		}
		catch (XMLException e)
		{
			throw new SpyException("Can't parse the message log file", e);
		}
	}
	
	/**
	 * Turns the given list of LoggedMqttMessages into ReceivedMqttMessages.
	 * 
	 * @param list List of logged messages to progress
	 * @param progress The progress updater to call with updated progress
	 * @param current Current progress
	 * @param max Maximum progress value 
	 * 
	 * @return List of MQTT message objects (ReceivedMqttMessage)
	 */
	public static List<BaseMqttMessage> processMessageLog(
			final List<LoggedMqttMessage> list, final ProgressUpdater progress,
			final long current, final long max)
	{
		final List<BaseMqttMessage> mqttMessageList = new ArrayList<BaseMqttMessage>();
		long item = 0;
		
		// Process the messages
        for (final LoggedMqttMessage loggedMessage : list)
        {        	        
        	if (progress != null)
        	{
        		if (progress.isCancelled())
    			{
    				logger.info("Task cancelled!");
    				return null;
    			}
        		
	        	item++;
	        	if (item % 1000 == 0)
	        	{
	        		progress.update(current + item, max);
	        	}
        	}
        	
        	mqttMessageList.add(convertToBaseMqttMessage(loggedMessage));
        }
        logger.info("Message log - processed {} MQTT messages", list.size());		        	
        
        return mqttMessageList;
	}
	
	public static BaseMqttMessage convertToBaseMqttMessage(final LoggedMqttMessage loggedMessage)
	{
		final MqttMessage mqttMessage = new MqttMessage();
    	
    	if (logger.isTraceEnabled())
    	{
    		logger.trace("Processing message with payload {}", loggedMessage.getValue());
    	}
    	
    	if (Boolean.TRUE.equals(loggedMessage.isEncoded()))
    	{
    		mqttMessage.setPayload(Base64.decodeBase64(loggedMessage.getValue()));
    	}
    	else
    	{
    		mqttMessage.setPayload(ConversionUtils.stringToArray(loggedMessage.getValue()));
    	}
    	
    	mqttMessage.setQos(loggedMessage.getQos() == null ? 0 : loggedMessage.getQos());
    	mqttMessage.setRetained(loggedMessage.isRetained() == null ? false : loggedMessage.isRetained());
    	
    	return new BaseMqttMessage(loggedMessage.getId(), loggedMessage.getTopic(), mqttMessage, new Date(loggedMessage.getTimestamp()));
	}
}
