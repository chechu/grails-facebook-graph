package com.daureos.facebook

public class FacebookGraphException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5070446410644711801L;

	/**
	 * The result from the API server that represents the exception information.
	 */
	def result
	
	/**
	 * The default constructor
	 */
	public FacebookGraphException() {
		result = [:]
	}

	/**
	 * Make a new API Exception with the given result.
	 *
	 * @param Array $result the result from the API server
	 */
	public FacebookGraphException(result) {
		super(result['error'] ? result['error']['message'] : result['error_msg'])
	    this.result = result
	}

	/**
	 * Returns the associated type for the error. This will default to
	 * 'Exception' when a type is not available.
	 *
	 * @return String
	 */
	public def getType() {
		return (result['error'] && result['error']['type']) ? result['error']['type'] : 'Exception'
	}

	/**
	 * To make debugging easier.
	 *
	 * @returns String the string representation of the error
	 */
	public String toString() {
		return getType() + ': ' + getMessage()
	}
}
