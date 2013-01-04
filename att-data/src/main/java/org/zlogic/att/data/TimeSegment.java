/*
 * Awesome Time Tracker project.
 * License TBD.
 * Author: Dmitry Zolotukhin <zlogic@gmail.com>
 */
package org.zlogic.att.data;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Period;

/**
 * Entity class for a time segment Each task's time is tracked with
 * TimeSegments.
 *
 * @author Dmitry Zolotukhin <zlogic@gmail.com>
 */
@Entity
public class TimeSegment implements Serializable {

	/**
	 * JPA ID
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private long id;
	/**
	 * The start time
	 */
	@Temporal(TemporalType.TIMESTAMP)
	private Date startTime;
	/**
	 * The ending time
	 */
	@Temporal(TemporalType.TIMESTAMP)
	private Date endTime;
	/**
	 * The time segment description
	 */
	private String description;
	/**
	 * The Task owning this TimeSegment
	 */
	@ManyToOne
	private Task owner;

	/**
	 * Default constructor
	 */
	protected TimeSegment() {
		id = -1;
		startTime = new Date();
		endTime = new Date();
		description = "";
	}

	/**
	 * Constructs the time segment with a predefined owner Task
	 *
	 * @param owner the owner of this TimeSegment
	 */
	public TimeSegment(Task owner) {
		this();
		setOwner(owner);
	}

	/**
	 * Returns the start time
	 *
	 * @return the start time
	 */
	public Date getStartTime() {
		return startTime;
	}

	/**
	 * Sets the start time
	 *
	 * @param startTime the new start time
	 */
	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	/**
	 * Returns the end time
	 *
	 * @return the end time
	 */
	public Date getEndTime() {
		return endTime;
	}

	/**
	 * Sets the end time
	 *
	 * @param endTime the new end time
	 */
	public void setEndTime(Date endTime) {
		this.endTime = endTime;
	}

	/**
	 * Returns the description
	 *
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the description
	 *
	 * @param description the new description
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Returns the owner task
	 *
	 * @return the owner task
	 */
	public Task getOwner() {
		return owner;
	}

	/**
	 * Assigns a new owner task. Removes this segment from the previous owner
	 * (if present)
	 *
	 * @param owner the new owner task
	 */
	public void setOwner(Task owner) {
		//TODO: test inside transactions
		if (this.owner != null) {
			this.owner.removeSegment(this);
			this.owner = null;
		}
		this.owner = owner;
		owner.addSegment(this);
	}

	/**
	 * Returns the JPA ID
	 *
	 * @return the JPA ID
	 */
	public long getId() {
		return id;
	}

	/**
	 * Returns the calculated time segment duration
	 *
	 * @return the calculated time segment duration
	 */
	public Period getDuration() {
		return new Interval(new DateTime(startTime), new DateTime(endTime)).toPeriod();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof TimeSegment && id == ((TimeSegment) obj).id;
	}

	@Override
	public int hashCode() {
		return new Long(id).hashCode();
	}
}
