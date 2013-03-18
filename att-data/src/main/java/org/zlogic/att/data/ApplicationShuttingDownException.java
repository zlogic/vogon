/*
 * Awesome Time Tracker project.
 * License TBD.
 * Author: Dmitry Zolotukhin <zlogic@gmail.com>
 */
package org.zlogic.att.data;

import java.util.ResourceBundle;

/**
 * Application is shutting down exception
 *
 * @author Dmitry Zolotukhin <zlogic@gmail.com>
 */
public class ApplicationShuttingDownException extends RuntimeException {

	/**
	 * Localization messages
	 */
	private static final ResourceBundle messages = ResourceBundle.getBundle("org/zlogic/att/data/messages");

	/**
	 * Default constructor
	 */
	public ApplicationShuttingDownException() {
		super(messages.getString("APPLICATION_IS_SHUTTING_DOWN_EXCEPTION"));
	}
}
